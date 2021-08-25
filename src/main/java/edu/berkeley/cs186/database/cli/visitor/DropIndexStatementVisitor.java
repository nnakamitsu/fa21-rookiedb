package edu.berkeley.cs186.database.cli.visitor;

import edu.berkeley.cs186.database.Transaction;
import edu.berkeley.cs186.database.cli.parser.ASTColumnName;
import edu.berkeley.cs186.database.cli.parser.ASTIdentifier;

import java.io.PrintStream;

class DropIndexStatementVisitor extends StatementVisitor {
    public String tableName;
    public String columnName;

    @Override
    public void visit(ASTColumnName node, Object data) {
        this.columnName = (String) node.jjtGetValue();
    }

    @Override
    public void visit(ASTIdentifier node, Object data) {
        this.tableName = (String) node.jjtGetValue();
    }

    @Override
    public void execute(Transaction transaction, PrintStream out) {
        try {
            transaction.dropIndex(tableName, columnName);
            out.printf("DROP INDEX %s(%s)\n", tableName, columnName);
        } catch (Exception e) {
            out.println(e.getMessage());
            out.println("Failed to execute DROP INDEX.");
        }
    }

    @Override
    public StatementType getType() {
        return StatementType.DROP_INDEX;
    }
}