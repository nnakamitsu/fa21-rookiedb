package edu.berkeley.cs186.database.cli.visitor;

import edu.berkeley.cs186.database.Transaction;
import edu.berkeley.cs186.database.cli.PrettyPrinter;
import edu.berkeley.cs186.database.cli.parser.ASTIdentifier;
import edu.berkeley.cs186.database.cli.parser.ASTInsertValues;
import edu.berkeley.cs186.database.cli.parser.ASTLiteral;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.table.Record;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

class InsertStatementVisitor extends StatementVisitor {
    public String tableName;
    public List<Record> values = new ArrayList<>();

    @Override
    public void visit(ASTIdentifier node, Object data) {
        this.tableName = (String) node.jjtGetValue();
    }

    @Override
    public void visit(ASTInsertValues node, Object data) {
        List<DataBox> currValues = new ArrayList<>();
        super.visit(node, currValues);
        values.add(new Record(currValues));
    }

    @Override
    public void visit(ASTLiteral node, Object data) {
        List<DataBox> currValues = (List<DataBox>) data;
        currValues.add(PrettyPrinter.parseLiteral((String) node.jjtGetValue()));
    }

    @Override
    public void execute(Transaction transaction, PrintStream out) {
        try {
            for (Record record: values) {
                transaction.insert(this.tableName, record);
            }
            out.println("INSERT");
        } catch (Exception e) {
            out.println(e.getMessage());
            out.println("Failed to execute INSERT.");
        }
    }

    @Override
    public StatementType getType() {
        return StatementType.INSERT;
    }
}