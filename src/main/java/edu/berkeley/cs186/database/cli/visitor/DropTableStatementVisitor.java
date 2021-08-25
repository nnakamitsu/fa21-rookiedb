package edu.berkeley.cs186.database.cli.visitor;

import edu.berkeley.cs186.database.Transaction;
import edu.berkeley.cs186.database.cli.parser.ASTIdentifier;

import java.io.PrintStream;

class DropTableStatementVisitor extends StatementVisitor {
    public String tableName;

    @Override
    public void visit(ASTIdentifier node, Object data) {
        this.tableName = (String) node.jjtGetValue();
    }

    @Override
    public void execute(Transaction transaction, PrintStream out) {
        try {
            transaction.dropTable(this.tableName);
            out.println("DROP TABLE " + this.tableName + ";");
        } catch (Exception e) {
            out.println(e.getMessage());
            out.println("Failed to execute DROP TABLE.");
        }
    }

    @Override
    public StatementType getType() {
        return StatementType.DROP_TABLE;
    }

}