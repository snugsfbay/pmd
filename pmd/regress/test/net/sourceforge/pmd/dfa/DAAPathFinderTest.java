package test.net.sourceforge.pmd.dfa;

import test.net.sourceforge.pmd.testframework.ParserTst;
import net.sourceforge.pmd.ast.ASTMethodDeclarator;
import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.dfa.pathfinder.DAAPathFinder;
import net.sourceforge.pmd.dfa.pathfinder.Executable;
import net.sourceforge.pmd.dfa.IDataFlowNode;

import java.util.List;

public class DAAPathFinderTest extends ParserTst implements Executable {

    public void testTwoUpdateDefs() throws Throwable {
        ASTMethodDeclarator meth = (ASTMethodDeclarator)getOrderedNodes(ASTMethodDeclarator.class, TWO_UPDATE_DEFS).get(0);
        DAAPathFinder a = new DAAPathFinder((IDataFlowNode)meth.getDataFlowNode().getFlow().get(0), this);
//        a.run();
    }

    public void execute(List path) {}


    private static final String TWO_UPDATE_DEFS =
    "class Foo {" + PMD.EOL +
    " void bar(int x) {" + PMD.EOL +
    "  for (int i=0; i<10; i++, j--) {}" + PMD.EOL +
    " }" + PMD.EOL +
    "}";


}
