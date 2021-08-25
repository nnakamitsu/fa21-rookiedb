package edu.berkeley.cs186.database.cli.visitor;

import edu.berkeley.cs186.database.Transaction;
import edu.berkeley.cs186.database.cli.parser.ASTReleaseStatement;

import java.io.PrintStream;

class ReleaseStatementVisitor extends StatementVisitor {
    public String savepointName;

    @Override
    public void visit(ASTReleaseStatement node, Object data) {
        this.savepointName = (String) node.jjtGetValue();
    }

    @Override
    public void execute(Transaction transaction, PrintStream out) {
        try {
            transaction.releaseSavepoint(savepointName);
            out.println("RELEASE SAVEPOINT " + savepointName);
        } catch (Exception e) {
            out.println(e.getMessage());
            out.println("Failed to execute RELEASE SAVEPOINT.");
        }
    }

    @Override
    public StatementType getType() {
        return StatementType.RELEASE_SAVEPOINT;
    }

}