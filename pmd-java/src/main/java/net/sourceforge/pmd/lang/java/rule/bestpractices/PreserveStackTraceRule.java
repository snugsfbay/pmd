/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.java.rule.bestpractices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.lang.ast.Node;
import net.sourceforge.pmd.lang.java.ast.ASTAdditiveExpression;
import net.sourceforge.pmd.lang.java.ast.ASTAllocationExpression;
import net.sourceforge.pmd.lang.java.ast.ASTArgumentList;
import net.sourceforge.pmd.lang.java.ast.ASTCastExpression;
import net.sourceforge.pmd.lang.java.ast.ASTCatchStatement;
import net.sourceforge.pmd.lang.java.ast.ASTClassOrInterfaceType;
import net.sourceforge.pmd.lang.java.ast.ASTName;
import net.sourceforge.pmd.lang.java.ast.ASTPrimaryExpression;
import net.sourceforge.pmd.lang.java.ast.ASTPrimaryPrefix;
import net.sourceforge.pmd.lang.java.ast.ASTThrowStatement;
import net.sourceforge.pmd.lang.java.ast.ASTVariableDeclaratorId;
import net.sourceforge.pmd.lang.java.ast.ASTVariableInitializer;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;
import net.sourceforge.pmd.lang.java.symboltable.VariableNameDeclaration;
import net.sourceforge.pmd.lang.symboltable.NameOccurrence;

/**
 *
 * @author Unknown,
 * @author Romain PELISSE, belaran@gmail.com, fix for bug 1808110
 *
 */
public class PreserveStackTraceRule extends AbstractJavaRule {

    private static final String FILL_IN_STACKTRACE = ".fillInStackTrace";

    @Override
    public Object visit(ASTCatchStatement catchStmt, Object data) {
        String target = catchStmt.jjtGetChild(0).findChildrenOfType(ASTVariableDeclaratorId.class).get(0).getImage();
        // Inspect all the throw stmt inside the catch stmt
        List<ASTThrowStatement> lstThrowStatements = catchStmt.findDescendantsOfType(ASTThrowStatement.class);
        for (ASTThrowStatement throwStatement : lstThrowStatements) {
            Node n = throwStatement.jjtGetChild(0).jjtGetChild(0);
            if (n instanceof ASTCastExpression) {
                ASTPrimaryExpression expr = (ASTPrimaryExpression) n.jjtGetChild(1);
                if (expr.jjtGetNumChildren() > 1 && expr.jjtGetChild(1) instanceof ASTPrimaryPrefix) {
                    RuleContext ctx = (RuleContext) data;
                    addViolation(ctx, throwStatement);
                }
                continue;
            }
            // Retrieve all argument for the throw exception (to see if the
            // original exception is preserved)
            ASTArgumentList args = throwStatement.getFirstDescendantOfType(ASTArgumentList.class);
            if (args != null) {
                Node parent = args.jjtGetParent().jjtGetParent();
                if (parent instanceof ASTAllocationExpression) {
                    // maybe it is used inside a anonymous class
                    ck(data, target, throwStatement, parent);
                } else {
                    // Check all arguments used in the throw statement 
                    ck(data, target, throwStatement, throwStatement);
                }
            } else {
                Node child = throwStatement.jjtGetChild(0);
                while (child != null && child.jjtGetNumChildren() > 0 && !(child instanceof ASTName)) {
                    child = child.jjtGetChild(0);
                }
                if (child != null) {
                    if (child instanceof ASTName && !target.equals(child.getImage())
                            && !child.hasImageEqualTo(target + FILL_IN_STACKTRACE)) {
                        Map<VariableNameDeclaration, List<NameOccurrence>> vars = ((ASTName) child).getScope()
                                .getDeclarations(VariableNameDeclaration.class);
                        for (Map.Entry<VariableNameDeclaration, List<NameOccurrence>> entry : vars.entrySet()) {
                            VariableNameDeclaration decl = entry.getKey();
                            List<NameOccurrence> occurrences = entry.getValue();
                            if (decl.getImage().equals(child.getImage())) {
                                if (!isInitCauseCalled(target, occurrences)) {
                                    // Check how the variable is initialized
                                    ASTVariableInitializer initializer = decl.getNode().jjtGetParent()
                                            .getFirstDescendantOfType(ASTVariableInitializer.class);
                                    if (initializer != null) {
                                        args = initializer.getFirstDescendantOfType(ASTArgumentList.class);
                                        if (args != null) {
                                            // constructor with args?
                                            ck(data, target, throwStatement, args);
                                        } else if (!isFillInStackTraceCalled(target, initializer)) {
                                            addViolation(data, throwStatement);
                                        }
                                    }
                                }
                            }
                        }
                    } else if (child instanceof ASTClassOrInterfaceType) {
                        addViolation(data, throwStatement);
                    }
                }
            }
        }
        return super.visit(catchStmt, data);
    }

    private boolean isFillInStackTraceCalled(final String target, final ASTVariableInitializer initializer) {
        final ASTName astName = initializer.getFirstDescendantOfType(ASTName.class);
        return astName != null && astName.hasImageEqualTo(target + FILL_IN_STACKTRACE);
    }

    private boolean isInitCauseCalled(String target, List<NameOccurrence> occurrences) {
        boolean initCauseCalled = false;
        for (NameOccurrence occurrence : occurrences) {
            String image = null;
            if (occurrence.getLocation() != null) {
                image = occurrence.getLocation().getImage();
            }
            if (image != null && image.endsWith("initCause")) {
                ASTPrimaryExpression primaryExpression = occurrence.getLocation()
                        .getFirstParentOfType(ASTPrimaryExpression.class);
                if (primaryExpression != null) {
                    ASTArgumentList args2 = primaryExpression.getFirstDescendantOfType(ASTArgumentList.class);
                    if (checkForTargetUsage(target, args2)) {
                        initCauseCalled = true;
                        break;
                    }
                }
            }
        }
        return initCauseCalled;
    }

    /**
     * Checks whether the given target is in the argument list. If this is the
     * case, then the target (root exception) is used as the cause.
     * 
     * @param target
     * @param baseNode
     */
    private boolean checkForTargetUsage(String target, Node baseNode) {
        boolean match = false;
        if (target != null && baseNode != null) {
            // TODO : use Node.findDescendantsOfType(ASTName.class, true) on 7.0.0
            List<ASTName> nameNodes = new ArrayList<>();
            baseNode.findDescendantsOfType(ASTName.class, nameNodes, true);
            for (ASTName nameNode : nameNodes) {
                if (target.equals(nameNode.getImage())) {
                    boolean isPartOfStringConcatenation = isStringConcat(nameNode, baseNode);
                    if (!isPartOfStringConcatenation) {
                        match = true;
                        break;
                    }
                }
            }
        }
        return match;
    }
    
    /**
     * Checks whether the given childNode is part of an additive expression (String concatenation) limiting search to base Node.
     * @param childNode
     * @param baseNode
     * @return
     */
    private boolean isStringConcat(Node childNode, Node baseNode) {
        Node currentNode = childNode;
        while (!Objects.equals(currentNode, baseNode)) {
            currentNode = currentNode.jjtGetParent();
            if (currentNode instanceof ASTAdditiveExpression) {
                return true;
            }
        }
        return false;
    }

    private void ck(Object data, String target, ASTThrowStatement throwStatement, Node baseNode) {
        if (!checkForTargetUsage(target, baseNode)) {
            RuleContext ctx = (RuleContext) data;
            addViolation(ctx, throwStatement);
        }
    }
}
