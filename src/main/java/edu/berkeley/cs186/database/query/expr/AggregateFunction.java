package edu.berkeley.cs186.database.query.expr;

import edu.berkeley.cs186.database.databox.*;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.Schema;

import java.util.Random;

abstract class AggregateFunction extends Expression {
    protected Type inputType;

    AggregateFunction(Expression... children) {
        super(children);
        if (this.children.size() != 1)
            throw new UnsupportedOperationException("Aggregates take exactly one argument.");
        // Relies on default behavior of Expression constructor to set this to
        // true if any children contain/are an aggregate
        if (this.hasAgg)
            throw new UnsupportedOperationException("Cannot compute nested aggregate functions.");
        this.hasAgg = true;
    }

    @Override
    public void setSchema(Schema s) {
        super.setSchema(s);
        this.inputType = this.children.get(0).getType();
        checkSchema();
    }

    @Override
    protected OperationPriority priority() {
        return OperationPriority.ATOMIC;
    }

    public void checkSchema() {
        // Do nothing by default
    };

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

    public abstract void update(Record record);
    public abstract void reset();
    public abstract String getName();

    /**
     * A SUM aggregate keeps a cumulative sum of the values it has seen so far
     * and returns that sum as a result. Undefined for non-numeric data types.
     * If the column type is BOOL or INT the result type will be INT. If the
     * column type is LONG the result type will be LONG. If the column type is
     * FLOAT the result type will be FLOAT.
    */
    static class SumAggregateFunction extends AggregateFunction {
        private float floatSum = 0;
        private int intSum = 0;
        private long longSum = 0;

        public SumAggregateFunction(Expression... children) {
            super(children);
        }

        @Override
        public void checkSchema() {
            if (this.inputType.getTypeId() == TypeId.STRING) {
                throw new IllegalArgumentException("Invalid data type for SUM aggregate: STRING");
            }
            if (this.inputType.getTypeId() == TypeId.BYTE_ARRAY) {
                throw new IllegalArgumentException("Invalid data type for SUM aggregate: BYTE_ARRAY");
            }
        }

        @Override
        public void update(Record record) {
            DataBox d = this.children.get(0).evaluate(record);
            switch (d.getTypeId()) {
                case BOOL:
                    boolean b = d.getBool();
                    if (b) intSum++;
                    return;
                case INT:
                    int i = d.getInt();
                    intSum += i;
                    return;
                case LONG:
                    long l = d.getLong();
                    longSum += l;
                    return;
                case FLOAT:
                    float f = d.getFloat();
                    floatSum += f;
                    return;
            }
            throw new IllegalStateException("Unreachable code.");
        }

        @Override
        public DataBox evaluate(Record record) {
            switch (getType().getTypeId()) {
                case INT: return new IntDataBox(intSum);
                case LONG: return new LongDataBox(longSum);
                case FLOAT: return new FloatDataBox(floatSum);
            }
            throw new IllegalStateException("Unreachable code.");
        }

        @Override
        public Type getType() {
            switch (this.inputType.getTypeId()) {
                case BOOL:
                case INT:
                    return Type.intType();
                case LONG:
                    return Type.longType();
                case FLOAT:
                    return Type.floatType();
            }
            throw new IllegalStateException("Unreachable code.");
        }

        @Override
        public void reset() {
            this.floatSum = 0;
            this.longSum = 0;
            this.intSum = 0;
        }

        @Override
        public String getName()  {
            return "SUM";
        }
    }

    /**
     * A MIN aggregate keeps track of the smallest value it has seen and return that
     * value as a result. Works for all data types and always returns the same data
     * type as the column being aggregated.
     */
    static class MinAggregateFunction extends AggregateFunction {
        private DataBox min;

        public MinAggregateFunction(Expression... children) {
            super(children);
        }

        @Override
        public void update(Record record) {
            DataBox d = children.get(0).evaluate(record);
            if (min == null || d.compareTo(min) < 0) min = d;
        }

        @Override
        public DataBox evaluate(Record record) {
            return min;
        }

        @Override
        public Type getType() {
            return this.inputType;
        }

        @Override
        public void reset() {
            this.min = null;
        }

        @Override
        public String getName() {
            return "MIN";
        }
    }

    /**
     * A MAX aggregate keeps track of the largest value it has seen and return that
     * value as a result. Works for all data types and always returns the same data
     * type as the column being aggregated.
     */
    static class MaxAggregateFunction extends AggregateFunction {
        private DataBox max;

        public MaxAggregateFunction(Expression... children) {
            super(children);
        }

        @Override
        public void update(Record record) {
            DataBox d = children.get(0).evaluate(record);
            if (max == null || d.compareTo(max) > 0) max = d;
        }

        @Override
        public DataBox evaluate(Record r) {
            return max;
        }

        @Override
        public Type getType() {
            return this.inputType;
        }

        @Override
        public void reset() {
            this.max = null;
        }

        @Override
        public String getName() {
            return "MAX";
        }
    }

    /**
     * A RANGE aggregate keeps track of the largest and smallest values it has seen,
     * and returns the difference as a result. Undefined for the STRING and BOOL
     * data types. Always returns the same data type as the column being
     * aggregated.
     */
    static class RangeAggregateFunction extends AggregateFunction {
        MaxAggregateFunction maxAgg;
        MinAggregateFunction minAgg;

        public RangeAggregateFunction(Expression... children) {
            super(children);
            this.maxAgg = new MaxAggregateFunction(children);
            this.minAgg = new MinAggregateFunction(children);
        }

        @Override
        public void setSchema(Schema s) {
            super.setSchema(s);
            this.maxAgg.setSchema(s);
            this.minAgg.setSchema(s);
            if (inputType.getTypeId() == TypeId.STRING || inputType.getTypeId() == TypeId.BOOL || inputType.getTypeId() == TypeId.BYTE_ARRAY) {
                throw new IllegalArgumentException("Invalid data type for RANGE aggregate: " + inputType.getTypeId());
            }
        }

        @Override
        public void update(Record record) {
            this.maxAgg.update(record);
            this.minAgg.update(record);
        }

        @Override
        public Type getType() {
            return this.maxAgg.getType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox max = maxAgg.evaluate(record);
            DataBox min = minAgg.evaluate(record);
            switch (max.getTypeId()) {
                case INT: return new IntDataBox(max.getInt() - min.getInt());
                case LONG: return new LongDataBox(max.getLong() - min.getLong());
                case FLOAT: return new FloatDataBox(max.getFloat() - min.getFloat());
            }
            throw new IllegalStateException("Unreachable code.");
        }

        @Override
        public void reset() {
            maxAgg.reset();
            minAgg.reset();
        }

        @Override
        public String getName() {
            return "RANGE";
        }
    }

    /**
     * A FIRST aggregate keeps track of the first value it has seen and returns that
     * as a result. Works for all data types, and always returns the same data type
     * as the column being aggregated on.
     */
    static class FirstAggregateFunction extends AggregateFunction {
        DataBox first;

        public FirstAggregateFunction(Expression... children) {
            super(children);
        }

        @Override
        public void update(Record record) {
            DataBox value = this.children.get(0).evaluate(record);
            if (this.first == null) this.first = value;
        }

        @Override
        public DataBox evaluate(Record r) {
            return this.first;
        }

        @Override
        public Type getType() {
            return this.inputType;
        }

        @Override
        public void reset() {
            this.first = null;
        }

        @Override
        public String getName() {
            return "FIRST";
        }
    }

    /**
     * A LAST aggregate keeps track of the last value it has seen and returns that
     * as a result. Works for all data types, and always returns the same data type
     * as the column being aggregated on.
     */
    static class LastAggregateFunction extends AggregateFunction {
        DataBox last;

        public LastAggregateFunction(Expression... children) {
            super(children);
        }

        @Override
        public void update(Record r) {
            DataBox value = this.children.get(0).evaluate(r);
            this.last = value;
        }

        @Override
        public DataBox evaluate(Record r) {
            return this.last;
        }

        @Override
        public Type getType() {
            return this.inputType;
        }

        @Override
        public void reset() {
            this.last = null;
        }

        @Override
        public String getName() {
            return "LAST";
        }
    }

    /**
     * A COUNT aggregate counts the number of values passed in and returns the total
     * as a result. Works for all data types and always returns an INT type result.
     */
    static class CountAggregateFunction extends AggregateFunction {
        private int count = 0;

        public CountAggregateFunction(Expression... children) {
            super(new Literal(new StringDataBox("*")));
            // Do nothing
        }

        @Override
        public void update(Record r) {
            count++;
        }

        @Override
        public DataBox evaluate(Record r) {
            return new IntDataBox(count);
        }

        @Override
        public Type getType() {
            return Type.intType();
        }

        @Override
        public void reset() {
            this.count = 0;
        }

        @Override
        public String getName() {
            return "COUNT";
        }
    }

    /**
     * A RANDOM aggregate will uniformly at random return one of the values it has
     * been fed as a result. Works for all data types and always returns the same
     * data type as the column being aggregated.
     */
    static class RandomAggregateFunction extends AggregateFunction {
        private int count = 0;
        private DataBox value;
        private Random generator;

        public RandomAggregateFunction(Expression... children) {
            super(children);
            this.generator = new Random();
        }

        @Override
        public void update(Record record) {
            DataBox value = this.children.get(0).evaluate(record);
            count += 1;
            if (generator.nextDouble() <= 1.0 / count) {
                this.value = value;
            }
        }

        @Override
        public DataBox evaluate(Record r) {
            return this.value;
        }

        @Override
        public Type getType() {
            return this.inputType;
        }

        @Override
        public void reset() {
            this.value = null;
            this.count = 0;
        }

        @Override
        public String getName() {
            return "RANDOM";
        }
    }

    /**
     * An AVG aggregate keeps a cumulative sum of the values it has seen and returns
     * that sum over the total number of values. Undefined for the STRING data
     * type. Always returns FLOAT type result.
     */
    static class AverageAggregateFunction extends AggregateFunction {
        private SumAggregateFunction sumAgg;
        float count = 0;

        public AverageAggregateFunction(Expression... children) {
            super(children);
            this.sumAgg = new SumAggregateFunction(children);
        }

        @Override
        public void setSchema(Schema s) {
            super.setSchema(s);
            this.sumAgg.setSchema(s);
        }

        @Override
        public void checkSchema() {
            if (this.inputType.getTypeId() == TypeId.STRING || this.inputType.getTypeId() == TypeId.BYTE_ARRAY) {
                throw new IllegalArgumentException("Invalid data type for AVG aggregate: " + this.inputType.getTypeId());
            }
        }

        @Override
        public void update(Record record) {
            this.sumAgg.update(record);
            count++;
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox sum = this.sumAgg.evaluate(record);
            if (count == 0) return new FloatDataBox(Float.NEGATIVE_INFINITY);
            switch (sum.getTypeId()) {
                case INT: return new FloatDataBox(sum.getInt() / count);
                case LONG: return new FloatDataBox(sum.getLong() / count);
                case FLOAT: return new FloatDataBox(sum.getFloat() / count);
            }
            throw new IllegalStateException("Unreachable code.");
        }

        @Override
        public Type getType() {
            return Type.floatType();
        }

        @Override
        public void reset() {
            this.count = 0;
            sumAgg.reset();
        }

        @Override
        public String getName() {
            return "AVG";
        }
    }

    /**
     * A VAR aggregate keeps track of the variance across all the elements it has
     * seen so far, and returns that variance as a result. Undefined for the STRING
     * data type. Always returns a result of data type FLOAT. If only one value has
     * been seen, the result will be zero.
     *
     * Implementation based off of Welford's Online Algorithm for computing
     * variance.
     */
    static class VarianceAggregateFunction extends AggregateFunction {
        double M = 0.0;
        double S = 0.0;
        int k = 0;

        public VarianceAggregateFunction(Expression... children) {
            super(children);
        }

        @Override
        public void checkSchema() {
            if (inputType.getTypeId() == TypeId.STRING || inputType.getTypeId() == TypeId.BYTE_ARRAY) {
                throw new IllegalArgumentException("Invalid data type for VAR aggregate:" + inputType.getTypeId());
            }
        }

        @Override
        public void update(Record record) {
            DataBox d = this.children.get(0).evaluate(record);
            k++;
            float x = 0;
            switch (d.getTypeId()) {
                case BOOL:
                    x = d.getBool() ? 1 : 0;
                    break;
                case INT:
                    x = d.getInt();
                    break;
                case LONG:
                    x = d.getLong();
                    break;
                case FLOAT:
                    x = d.getFloat();
                    break;
                case STRING:
                    throw new IllegalArgumentException("Can't compute variance of a String");
            }
            double delta = x - M;
            M += delta / k;
            S += delta * (x - M);
        }

        @Override
        public DataBox evaluate(Record record) {
            if (k <= 1) return new FloatDataBox(0);
            Double result = M / (k - 1);
            return new FloatDataBox(result.floatValue());
        }

        @Override
        public Type getType() {
            return Type.floatType();
        }

        @Override
        public void reset() {
            this.M = 0.0;
            this.S = 0.0;
            this.k = 0;
        }

        @Override
        public String getName() {
            return "VAR";
        }
    }

    /**
     * A STDDEV aggregate maintains a VAR aggregate and returns the square root of
     * the variance as a result. Undefined for the STRING data type. Always
     * returns a FLOAT type result. If only one value has been seen, the result will
     * be zero.
     */
    static class StdDevAggregateFunction extends AggregateFunction {
        private VarianceAggregateFunction varAgg;
        public StdDevAggregateFunction(Expression... children) {
            super(children);
            this.varAgg = new VarianceAggregateFunction(children);
        }

        @Override
        public void setSchema(Schema s) {
            super.setSchema(s);
            this.varAgg.setSchema(s);
        }

        @Override
        public void checkSchema() {
            if (inputType.getTypeId() == TypeId.STRING) {
                throw new IllegalArgumentException("Invalid data type for STDDEV aggregate: STRING");
            }
        }

        @Override
        public void update(Record record) {
            this.varAgg.update(record);
        }

        @Override
        public DataBox evaluate(Record record) {
            Double result = Math.sqrt(varAgg.evaluate(record).getFloat());
            return new FloatDataBox(result.floatValue());
        }

        @Override
        public Type getType() {
            return Type.floatType();
        }

        @Override
        public void reset() {
            varAgg.reset();
        }

        @Override
        public String getName() {
            return "STDDEV";
        }
    }
}
