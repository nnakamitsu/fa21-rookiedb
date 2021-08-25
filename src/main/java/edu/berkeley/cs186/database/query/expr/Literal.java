package edu.berkeley.cs186.database.query.expr;

import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.databox.Type;
import edu.berkeley.cs186.database.table.Record;

class Literal extends Expression {
    private DataBox data;

    public Literal(DataBox data) {
        super();
        this.data = data;
    }

    @Override
    public Type getType() {
        return data.type();
    }

    @Override
    public DataBox evaluate(Record record) {
        return data;
    }

    @Override
    protected OperationPriority priority() {
        return OperationPriority.ATOMIC;
    }

    @Override
    protected String subclassString() {
        return data.toString();
    }
}