package edu.berkeley.cs186.database.query.expr;

import edu.berkeley.cs186.database.databox.*;
import edu.berkeley.cs186.database.table.Record;

/**
 * This class is intentionally package private to avoid name collisions with
 * Java's builtin function class
 */
abstract class NamedFunction extends Expression {
    public NamedFunction(Expression... children) {
        super(children);
    }

    @Override
    protected String subclassString() {
        StringBuilder s = new StringBuilder(this.getName());
        s.append("(");
        for (int i = 0; i < this.children.size(); i++) {
            s.append(this.children.get(i).toString());
            if (i != this.children.size() - 1) s.append(", ");
        }
        s.append(")");
        return s.toString();
    }

    @Override
    protected OperationPriority priority() {
        return OperationPriority.ATOMIC;
    }

    public abstract String getName();

    static class UpperFunction extends NamedFunction {
        public UpperFunction(Expression... children) {
            super(children);
            if (children.length != 1) {
                throw new UnsupportedOperationException("UPPER takes exactly one argument");
            }
        }

        @Override
        public String getName() {
            return "UPPER";
        }

        @Override
        public Type getType() {
            Expression f = this.children.get(0);
            if (f.getType().getTypeId() != TypeId.STRING) {
                throw new UnsupportedOperationException("UPPER can only be used on strings.");
            }
            return f.getType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox value = this.children.get(0).evaluate(record);
            return new StringDataBox(value.getString().toUpperCase());
        }
    }

    static class LowerFunction extends NamedFunction {
        public LowerFunction(Expression... children) {
            super(children);
            if (children.length != 1) {
                throw new UnsupportedOperationException("LOWER takes exactly one argument");
            }
        }

        @Override
        public String getName() {
            return "LOWER";
        }

        @Override
        public Type getType() {
            Expression f = this.children.get(0);
            if (f.getType().getTypeId() != TypeId.STRING) {
                throw new UnsupportedOperationException("LOWER can only be used on strings.");
            }
            return f.getType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox value = this.children.get(0).evaluate(record);
            return new StringDataBox(value.getString().toLowerCase());
        }
    }

    static class ReplaceFunction extends NamedFunction {
        public ReplaceFunction(Expression... children) {
            super(children);
            if (children.length != 3) {
                throw new UnsupportedOperationException("REPLACE takes exactly three arguments");
            }
        }

        @Override
        public String getName() {
            return "REPLACE";
        }

        @Override
        public Type getType() {
            Expression f1 = this.children.get(0);
            Expression f2 = this.children.get(1);
            Expression f3 = this.children.get(1);
            if (f1.getType().getTypeId() != TypeId.STRING ||
                    f2.getType().getTypeId() != TypeId.STRING ||
                    f3.getType().getTypeId() != TypeId.STRING) {
                throw new UnsupportedOperationException("All arguments of REPLACE must be of type STRING");
            }
            return f1.getType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox v1 = this.children.get(0).evaluate(record);
            DataBox v2 = this.children.get(1).evaluate(record);
            DataBox v3 = this.children.get(2).evaluate(record);
            return new StringDataBox(v1.getString().replace(v2.getString(), v3.getString()));
        }
    }

    static class FloorFunction extends NamedFunction {
        public FloorFunction(Expression... children) {
            super(children);
            if (children.length != 1) {
                throw new UnsupportedOperationException("FLOOR takes exactly one argument");
            }
        }

        @Override
        public String getName() {
            return "FLOOR";
        }

        @Override
        public Type getType() {
            TypeId t = this.children.get(0).getType().getTypeId();
            if (t == TypeId.STRING || t == TypeId.BYTE_ARRAY) {
                throw new UnsupportedOperationException("FLOOR is not defined for types STRING and BYTE_ARRAY");
            }
            return Type.longType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox v = this.children.get(0).evaluate(record);
            return new LongDataBox(Math.round(Math.floor(toFloat(v))));
        }
    }

    static class CeilFunction extends NamedFunction {
        public CeilFunction(Expression... children) {
            super(children);
            if (children.length != 1) {
                throw new UnsupportedOperationException("CEIL takes exactly one argument");
            }
        }

        @Override
        public String getName() {
            return "CEIL";
        }

        @Override
        public Type getType() {
            TypeId t = this.children.get(0).getType().getTypeId();
            if (t == TypeId.STRING || t == TypeId.BYTE_ARRAY) {
                throw new UnsupportedOperationException("CEIL is not defined for types STRING and BYTE_ARRAY");
            }
            return Type.longType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox v = this.children.get(0).evaluate(record);
            return new LongDataBox(Math.round(Math.ceil(toFloat(v))));
        }
    }

    static class RoundFunction extends NamedFunction {
        public RoundFunction(Expression... children) {
            super(children);
            if (children.length != 1) {
                throw new UnsupportedOperationException("ROUND takes exactly one argument");
            }
        }

        @Override
        public String getName() {
            return "ROUND";
        }

        @Override
        public Type getType() {
            TypeId t = this.children.get(0).getType().getTypeId();
            if (t == TypeId.STRING || t == TypeId.BYTE_ARRAY) {
                throw new UnsupportedOperationException("ROUND is not defined for types STRING and BYTE_ARRAY");
            }
            return Type.longType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox v = this.children.get(0).evaluate(record);
            return new LongDataBox(Math.round(NamedFunction.toLong(v)));
        }
    }

    static class NegateFunction extends NamedFunction {
        public NegateFunction(Expression... children) {
            super(children);
            if (children.length != 1) {
                throw new UnsupportedOperationException("NEGATE takes exactly one argument");
            }
        }

        @Override
        public String getName() {
            return "NEGATE";
        }

        @Override
        public Type getType() {
            TypeId t = this.children.get(0).getType().getTypeId();
            if (t == TypeId.STRING || t == TypeId.BYTE_ARRAY || t == TypeId.BOOL) {
                throw new UnsupportedOperationException("NEGATE is not defined for type: " + t);
            }
            return this.children.get(0).getType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox d = this.children.get(0).evaluate(record);
            switch (this.getType().getTypeId()) {
                case INT: return new IntDataBox(-toInt(d));
                case LONG: return new LongDataBox(-toInt(d));
                case FLOAT: return new FloatDataBox(-toFloat(d));
                default: throw new RuntimeException("Unreachable code.");
            }
        }
    }
}
