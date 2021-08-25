package edu.berkeley.cs186.database.query.expr;

import edu.berkeley.cs186.database.DatabaseException;
import edu.berkeley.cs186.database.cli.parser.ParseException;
import edu.berkeley.cs186.database.cli.parser.RookieParser;
import edu.berkeley.cs186.database.databox.*;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.Schema;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/**
 * Expressions are groups of operations, literal values, and column names that
 * can be evaluated during a query. Some examples of expressions:
 *   1 + 1
 *   2 * someColumn + 3
 *   MAX(someColumn) * 2
 *   MAX(someColumn * 2)
 *   NOT 16 > 14 OR 2 + 2 == 4
 *
 * Everything here is already implemented for you, and none of the tests
 * (both public and hidden) require knowledge of how this class and its
 * subclasses work, since the topics covered would be a lot closer to 164 than
 * 186! The most useful ones to know if you are curious though are:
 * - evaluate(Record r): evaluates the expression against the columns in `r`
 * - update(Record r): Used by aggregates to compute partial results
 * - Expression.fromString(String s): Creates an expression from a String!
 */
public abstract class Expression {
    // The dependencies of an expression are the names of columns whose values
    // must be known in order into compute the expression. For example, the
    // dependencies of the expression `2 * int1 + int2` would be `int1` and
    // `int2`.
    protected Set<String> dependencies = new HashSet<>();

    // The schema of the input records. This can be set after the expression
    // object is created. Schema information is needed to determine which
    // DataBox in a given input record corresponds to a dependency. The schema
    // is also needed to determine the type of data returned. For example,
    // the expression `val1 + val2` would return an INT if val1 and val2 were
    // both INTs, but would return a FLOAT if they were FLOATs.
    protected Schema schema = null;

    // Whether or not the given expression contains an aggregate function.
    protected boolean hasAgg = false;

    // Subexpressions. For example, the expression `2 + (3 * 11)` is comprised
    // of two sub expressions: `2` and `3 * 11`.
    protected List<Expression> children;

    // Flag to include parentheses that the expression is wrapped in. Useful to
    // preserve order of operations in event of reparse
    boolean needsParentheses = false;

    public Expression(Expression... children) {
        this.children = Arrays.asList(children);
        for (Expression child: children) {
            hasAgg |= child.hasAgg();
            this.dependencies.addAll(child.dependencies);

            // Inject parentheses to preserve order of operations if needed
            if (this instanceof NamedFunction || this instanceof AggregateFunction)
                continue;
            if (child.priority().ordinal() > priority().ordinal()) {
                child.needsParentheses = true;
            }
        }
    }

    // Class methods //////////////////////////////////////////////////////////.

    /**
     * @return The type of data (INT, FLOAT, STRING, etc...) that this
     * expression returns when evaluated.
     */
    public abstract Type getType();

    /**
     * @param record The record that this expression will be evaluated on.
     * @return A DataBox containing the expression's value.
     */
    public abstract DataBox evaluate(Record record);

    /**
     * Sets the Schema of this expression. This schema should match the schema
     * of the records that will be passed to the update() and evaluate()
     * methods.
     * @param schema
     */
    public void setSchema(Schema schema) {
        this.schema = schema;
        for (Expression child: children) child.setSchema(schema);
    }

    /**
     * @return The set of this expression's column dependencies. For example,
     * the expression `int1 + (3 * int2)` would return a set with the elements
     * "int1" and "int2".
     */
    public Set<String> getDependencies() {
        return this.dependencies;
    }

    // Aggregate related methods
    /**
     * @return Whether or not the given expression contains an aggregate
     * function.
     */
    public boolean hasAgg() {
        return this.hasAgg;
    }

    /**
     * Used for aggregate functions. Aggregate functions are evaluated on
     * multiple records, updating internal state as each record is viewed.
     * By default, this method will attempt to update any subexpressions.
     * @param record The record which the function will update it's internal
     *               state with.
     */
    public void update(Record record) {
        assert this.schema != null;
        for (Expression child: children) {
            if (child.hasAgg()) child.update(record);
        }
    }

    /**
     * Resets any internal state from previous calls to update(). Useful for
     * GROUP BY's, where you may need to run the same aggregate function over
     * multiple groups of data.
     */
    public void reset() {
        for (Expression child: children) {
            if (child.hasAgg()) child.reset();
        }
    }

    public final String toString() {
        if (this.needsParentheses) return "(" + subclassString() + ")";
        return subclassString();
    };

    /**
     * @return The order of operations priority of this expression.
     */
    protected abstract OperationPriority priority();

    /**
     * @return A String representation of this expression. Must be implemented
     * for all subclasses of expression.
     */
    protected abstract String subclassString();

    /**
     * Parses a string and returns an expression object
     * @param s a String. For example, "2 + 2", "ROUND(column * 2.1)", etc...
     * @return An expression corresponding to the passed in String
     */
    public static Expression fromString(String s) {
        RookieParser parser = new RookieParser(
                new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
        try {
            ExpressionVisitor visitor = new ExpressionVisitor();
            parser.expression().jjtAccept(visitor, null);
            return visitor.build();
        } catch (ParseException e) {
            throw new DatabaseException(e.getMessage());
        }
    }

    // Order of operations /////////////////////////////////////////////////////
    enum OperationPriority {
        ATOMIC,
        NEGATE,
        MULTIPLICATIVE,
        ADDITIVE,
        COMPARE,
        NOT,
        AND,
        OR
    }

    // Conjunctive Normal Form /////////////////////////////////////////////////

    /**
     * Converts `expr` into a list of expressions that, when AND'd together,
     * are equivalent to the original expression. Based off of the following
     * web page for conjunctive normal form:
     * https://www.cs.jhu.edu/~jason/tutorials/convert-to-CNF.html
     * @return a list of expressions that, when AND'd together,
     *         are equivalent to the original expression
     */
    public List<Expression> toCNF() {
        if (this instanceof AndExpression) {
            List<Expression> result = new ArrayList<>();
            for (Expression child: this.children) {
                result.addAll(child.toCNF());
            }
            return result;
        }
        if (this instanceof OrExpression) {
            List<List<Expression>> groups = new ArrayList<>();
            for (Expression e: this.children.get(0).toCNF()) {
                groups.add(Collections.singletonList(e));
            }
            for (int i = 1; i < this.children.size(); i++) {
                Expression curr = this.children.get(i);
                List<List<Expression>> newGroups = new ArrayList<>();
                for (List<Expression> oldGroup: groups) {
                    for (Expression e: curr.toCNF()) {
                        List<Expression> newGroup = new ArrayList<>();
                        newGroup.addAll(oldGroup);
                        newGroup.add(e);
                        newGroups.add(newGroup);
                    }
                }
                groups = newGroups;
            }
            List<Expression> result = new ArrayList<>();
            for (List<Expression> group: groups) {
                Expression[] children = new Expression[group.size()];
                group.toArray(children);
                result.add(new OrExpression(children));
            }
            return result;
        }
        if (this instanceof NotExpression) {
            Expression inner = this.children.get(0);
            if (inner instanceof NotExpression) {
                return Collections.singletonList(inner.children.get(0));
            }
            List<Expression> invertedChildrenList = new ArrayList<>();
            for (Expression child: inner.children) {
                invertedChildrenList.add(new NotExpression(child));
            }
            Expression[] invertedChildren = new Expression[invertedChildrenList.size()];
            invertedChildrenList.toArray(invertedChildren);
            if (inner instanceof OrExpression) {
                return new AndExpression(invertedChildren).toCNF();
            }
            if (inner instanceof AndExpression) {
                return new OrExpression(invertedChildren).toCNF();
            }
        }
        // Create a "copy" by reparsing, useful to prevent setSchema calls
        // from conflicting at multiple spots in the operator pipeline.
        return Collections.singletonList(Expression.fromString(toString()));
    }

    // Static lookup methods ///////////////////////////////////////////////////

    public static Expression compare(String op, Expression a, Expression b) {
        op = op.toUpperCase();
        switch(op) {
            case "=":
            case "==": return new EqualExpression(a, b);
            case "!=":
            case "<>": return new UnequalExpression(a, b);
            case ">=": return new GreaterThanEqualExpression(a, b);
            case ">": return new GreaterThanExpression(a, b);
            case "<=": return new LessThanEqualExpression(a, b);
            case "<": return new LessThanExpression(a, b);
        }
        throw new UnsupportedOperationException("Unknown operator `" + op + "`");
    }

    public static Expression function(String name, Expression... children) {
        name = name.toUpperCase().trim();
        switch (name) {
            // Regular functions
            case "UPPER": return new NamedFunction.UpperFunction(children);
            case "LOWER": return new NamedFunction.LowerFunction(children);
            case "REPLACE": return new NamedFunction.ReplaceFunction(children);
            case "ROUND": return new NamedFunction.RoundFunction(children);
            case "CEIL": return new NamedFunction.CeilFunction(children);
            case "FLOOR": return new NamedFunction.FloorFunction(children);
            case "NEGATE": return new NamedFunction.NegateFunction(children);
            // Aggregates
            case "FIRST": return new AggregateFunction.FirstAggregateFunction(children);
            case "SUM": return new AggregateFunction.SumAggregateFunction(children);
            case "COUNT": return new AggregateFunction.CountAggregateFunction(children);
            case "MAX": return new AggregateFunction.MaxAggregateFunction(children);
            case "MIN": return new AggregateFunction.MinAggregateFunction(children);
            case "AVG": return new AggregateFunction.AverageAggregateFunction(children);
            case "VARIANCE": return new AggregateFunction.VarianceAggregateFunction(children);
            case "STDDEV": return new AggregateFunction.StdDevAggregateFunction(children);
            case "RANGE": return new AggregateFunction.RangeAggregateFunction(children);
            case "RANDOM": return new AggregateFunction.RandomAggregateFunction(children);
            case "LAST": return new AggregateFunction.LastAggregateFunction(children);
        }
        throw new RuntimeException("Unknown function: " + name);
    }

    public static Expression literal(DataBox d) {
        return new Literal(d);
    }

    public static Expression column(String colName) {
        return new Column(colName);
    }

    public static Expression not(Expression... children) {
        return new NotExpression(children);
    }

    public static Expression and(Expression... children) {
        return new AndExpression(children);
    }

    public static Expression or(Expression... children) {
        return new OrExpression(children);
    }

    public static Expression additive(List<Character> ops, Expression... children) {
        return new AdditiveExpression(ops, children);
    }

    public static Expression multiplicative(List<Character> ops, Expression... children) {
        return new MultiplicativeExpression(ops, children);
    }

    public static Expression negate(Expression... children) {
        return new NegateExpression(children);
    }

    // DataBox casting utilities ///////////////////////////////////////////////

    /**
     * @param d: A databox
     * @return The boolean value of the databox, if it can be cast to a boolean.
     * This is effectively how "truthiness" of a databox is determined.
     */
    public static boolean toBool(DataBox d) {
        switch(d.getTypeId()) {
            case BOOL: return d.getBool();
            case LONG: return d.getLong() != 0;
            case INT: return d.getInt() != 0;
            case STRING: return !d.getString().equals("");
            case FLOAT: return d.getFloat() != 0.0;
            case BYTE_ARRAY: throw new UnsupportedOperationException("Cannot interpret byte array as true/false");
            default: throw new RuntimeException("Unreachable code");
        }
    }

    /**
     * @param d: A databox
     * @return The integer value of the databox, if it can be cast to an int
     */
    static int toInt(DataBox d) {
        switch (d.getTypeId()) {
            case BOOL: return d.getBool() ? 1 : 0;
            case INT: return d.getInt();
        }
        throw new UnsupportedOperationException("Cannot cast type `" + d.getTypeId() + "` to INT");
    }

    /**
     * @param d: A databox
     * @return The long value of the databox, if it can be cast to a long
     */
    static long toLong(DataBox d) {
        switch(d.getTypeId()) {
            case LONG: return d.getLong();
            case BOOL: return d.getBool() ? 1 : 0;
            case INT: return d.getInt();
        }
        throw new UnsupportedOperationException("Cannot cast type `" + d.getTypeId() + "` to LONG");
    }

    /**
     * @param d: A databox
     * @return The float value of the databox, if it can be cast to a float
     */
    static float toFloat(DataBox d) {
        switch(d.getTypeId()) {
            case FLOAT: return d.getFloat();
            case LONG: return d.getLong();
            case BOOL: return d.getBool() ? 1 : 0;
            case INT: return d.getInt();
        }
        throw new UnsupportedOperationException("Cannot cast type `" + d.getTypeId() + "` to INT");
    }

    // Comparison functions ////////////////////////////////////////////////////
    static class LessThanExpression extends Expression {
        public LessThanExpression(Expression a, Expression b) {
            super(new Expression[]{a, b});
        }

        @Override
        public Type getType() {
            return Type.boolType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox left = children.get(0).evaluate(record);
            DataBox right = children.get(1).evaluate(record);
            return new BoolDataBox(left.compareTo(right) < 0);
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.COMPARE;
        }

        @Override
        protected String subclassString() {
            return children.get(0).toString() + " < " + children.get(1).toString();
        }
    }

    static class LessThanEqualExpression extends Expression {
        public LessThanEqualExpression(Expression a, Expression b) {
            super(new Expression[]{a, b});
        }

        @Override
        public Type getType() {
            return Type.boolType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox left = children.get(0).evaluate(record);
            DataBox right = children.get(1).evaluate(record);
            return new BoolDataBox(left.compareTo(right) <= 0);
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.COMPARE;
        }

        @Override
        protected String subclassString() {
            return children.get(0).toString() + " <= " + children.get(1).toString();
        }
    }

    static class GreaterThanExpression extends Expression {
        public GreaterThanExpression(Expression a, Expression b) {
            super(new Expression[]{a, b});
        }

        @Override
        public Type getType() {
            return Type.boolType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox left = children.get(0).evaluate(record);
            DataBox right = children.get(1).evaluate(record);
            return new BoolDataBox(left.compareTo(right) > 0);
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.COMPARE;
        }

        @Override
        protected String subclassString() {
            return children.get(0).toString() + " > " + children.get(1).toString();
        }
    }

    static class GreaterThanEqualExpression extends Expression {
        public GreaterThanEqualExpression(Expression a, Expression b) {
            super(new Expression[]{a, b});
        }

        @Override
        public Type getType() {
            return Type.boolType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox left = children.get(0).evaluate(record);
            DataBox right = children.get(1).evaluate(record);
            return new BoolDataBox(left.compareTo(right) >= 0);
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.COMPARE;
        }

        @Override
        protected String subclassString() {
            return children.get(0).toString() + " >= " + children.get(1).toString();
        }
    }

    static class EqualExpression extends Expression {
        public EqualExpression(Expression a, Expression b) {
            super(new Expression[]{a, b});
        }

        @Override
        public Type getType() {
            return Type.boolType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox left = children.get(0).evaluate(record);
            DataBox right = children.get(1).evaluate(record);
            return new BoolDataBox(left.compareTo(right) == 0);
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.COMPARE;
        }

        @Override
        protected String subclassString() {
            return children.get(0).toString() + " = " + children.get(1).toString();
        }
    }

    static class UnequalExpression extends Expression {
        public UnequalExpression(Expression a, Expression b) {
            super(new Expression[]{a, b});
        }

        @Override
        public Type getType() {
            return Type.boolType();
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox left = children.get(0).evaluate(record);
            DataBox right = children.get(1).evaluate(record);
            return new BoolDataBox(left.compareTo(right) != 0);
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.COMPARE;
        }

        @Override
        protected String subclassString() {
            return children.get(0).toString() + " != " + children.get(1).toString();
        }
    }

    // Operator expressions ////////////////////////////////////////////////////

    /*
     * The expression types below are all of the expressions that involve
     * operators, that is: (AND/&&, OR/||, NOT/!, +, -, *, /, and %).
     */

    static class AndExpression extends Expression {
        public AndExpression(Expression... children) {
            super(children);
        }

        @Override
        public Type getType() {
            return Type.boolType();
        }

        @Override
        public DataBox evaluate(Record record) {
            for (Expression child: this.children) {
                // short circuit
                if (!toBool(child.evaluate(record))) {
                    return new BoolDataBox(false);
                }
            }
            return new BoolDataBox(true);
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.AND;
        }

        @Override
        protected String subclassString() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < this.children.size(); i++) {
                builder.append(this.children.get(i).toString());
                if (i  != this.children.size() - 1) {
                    builder.append(" AND ");
                }
            }
            return builder.toString();
        }
    }

    static class OrExpression extends Expression {
        public OrExpression(Expression... children) {
            super(children);
        }

        @Override
        public Type getType() {
            return Type.boolType();
        }

        @Override
        public DataBox evaluate(Record record) {
            for (Expression child: this.children) {
                // short circuit
                if (toBool(child.evaluate(record))) {
                    return new BoolDataBox(true);
                }
            }
            return new BoolDataBox(false);
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.OR;
        }

        @Override
        protected String subclassString() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < this.children.size(); i++) {
                builder.append(this.children.get(i).toString());
                if (i  != this.children.size() - 1) {
                    builder.append(" OR ");
                }
            }
            return builder.toString();
        }
    }

    static class NotExpression extends Expression {
        public NotExpression(Expression... children) {
            super(children);
            assert this.children.size() == 1;
        }

        @Override
        public Type getType() {
            return Type.boolType();
        }

        @Override
        public DataBox evaluate(Record record) {
            boolean baseVal = toBool(this.children.get(0).evaluate(record));
            return new BoolDataBox(!baseVal);
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.NOT;
        }

        @Override
        protected String subclassString() {
            return "NOT " + this.children.get(0).toString();
        }
    }

    // Arithmetic Expressions (+, -, *, /, %) //////////////////////////////////

    // List containing different numeric types, in order of the "priority" that
    // an implicit cast would take place. For example, adding an int and a long
    // should return a long, while adding a long and a float would return a
    // float. Adding a float to something will always upcast it to a float,
    // so float is last in the list. Similarly, adding a long will always
    // upcast to a long unless it's a float, so long is second to last.
    private static List<TypeId> upcastPriority = new ArrayList<>();
    static {
        upcastPriority.add(TypeId.INT);
        upcastPriority.add(TypeId.LONG);
        upcastPriority.add(TypeId.FLOAT);
    }

    // Returns the least permissive type that can be obtained from applying
    // numeric operators to the evaluation of each child in children. For
    // example, the result type of (int + float + long) would be float, while
    // the result type of (int + int) would be int.
    public static Type resultType(List<Expression> children) {
        int index = 0;
        for (Expression child: children) {
            TypeId type = child.getType().getTypeId();
            switch (type) {
                case STRING: throw new RuntimeException("Cannot convert string to numeric type.");
                case BYTE_ARRAY: throw new RuntimeException("Cannot convert byte array to numeric type");
                case BOOL: type = TypeId.INT; // Treat booleans as integer 0/1
                default: break;
            }
            int curr = upcastPriority.indexOf(type);
            assert curr != -1; // curr should be one of INT, FLOAT, or LONG
            index = Math.max(index, curr);
        }
        switch (upcastPriority.get(index)) {
            case INT: return Type.intType();
            case LONG: return Type.longType();
            case FLOAT: return Type.floatType();
            default: throw new RuntimeException("Invalid state.");
        }
    }

    static abstract class ArithmeticExpression extends Expression {
        private List<Character> ops;
        private Type type;
        private Function<Record, DataBox> evalFunc;

        public ArithmeticExpression(List<Character> ops, Expression[] children) {
            super(children);
            this.ops = ops;
            char op = ops.get(0);
        }

        @Override
        public void setSchema(Schema s) {
            super.setSchema(s);
            this.type = resultType(children);
            if (this.type.getTypeId() == TypeId.INT) {
                this.evalFunc = (record) -> {
                    int result = toInt(this.children.get(0).evaluate(record));
                    for (int i = 1; i < this.children.size(); i++) {
                        int curr = toInt(this.children.get(i).evaluate(record));
                        switch (ops.get(i - 1)) {
                            case '+': result += curr; break;
                            case '-': result -= curr; break;
                            case '*': result *= curr; break;
                            case '/': result /= curr; break;
                            case '%': result %= curr; break;
                            default: throw new RuntimeException("Unexpected operator: " + ops.get(i-1))
                                    ;                        }
                    }
                    return new IntDataBox(result);
                };
            } else if (this.type.getTypeId() == TypeId.LONG) {
                this.evalFunc = (record) -> {
                    long result = toLong(this.children.get(0).evaluate(record));
                    for (int i = 1; i < this.children.size(); i++) {
                        long curr = toLong(this.children.get(i).evaluate(record));
                        switch (ops.get(i - 1)) {
                            case '+': result += curr; break;
                            case '-': result -= curr; break;
                            case '*': result *= curr; break;
                            case '/': result /= curr; break;
                            case '%': result %= curr; break;
                            default: throw new RuntimeException("Unexpected operator: " + ops.get(i-1));                        }
                    }
                    return new LongDataBox(result);
                };
            } else if (this.type.getTypeId() == TypeId.FLOAT) {
                this.evalFunc = (record) -> {
                    float result = toFloat(this.children.get(0).evaluate(record));
                    for (int i = 1; i < this.children.size(); i++) {
                        float curr = toFloat(this.children.get(i).evaluate(record));
                        switch (ops.get(i - 1)) {
                            case '+': result += curr; break;
                            case '-': result -= curr; break;
                            case '*': result *= curr; break;
                            case '/': result /= curr; break;
                            case '%': result %= curr; break;
                            default: throw new RuntimeException("Unexpected operator: " + ops.get(i - 1));
                        }
                    }
                    return new FloatDataBox(result);
                };
            } else {
                throw new RuntimeException("Invalid result type for numeric expression: " + this.getType().getTypeId());
            }
        }

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public DataBox evaluate(Record record) {
            return this.evalFunc.apply(record);
        }

        @Override
        protected String subclassString() {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < this.children.size(); i++) {
                builder.append(this.children.get(i).toString());
                if (i != this.children.size() - 1) {
                    builder.append(" " + this.ops.get(i) + " ");
                }
            }
            return builder.toString();
        }
    }

    static class AdditiveExpression extends ArithmeticExpression {
        public AdditiveExpression(List<Character> ops, Expression[] children) {
            super(ops, children);
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.ADDITIVE;
        }
    }

    static class MultiplicativeExpression extends ArithmeticExpression {
        public MultiplicativeExpression(List<Character> ops, Expression[] children) {
            super(ops, children);
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.MULTIPLICATIVE;
        }
    }

    static class NegateExpression extends Expression {
        private Type type;

        public NegateExpression(Expression... children) {
            super(children);
            assert this.children.size() == 1;
        }

        @Override
        public void setSchema(Schema s) {
            super.setSchema(s);
            this.type = resultType(children);
        }

        @Override
        public Type getType() {
            return this.type;
        }

        @Override
        public DataBox evaluate(Record record) {
            DataBox c = children.get(0).evaluate(record);
            switch (this.type.getTypeId()) {
                case INT: return new IntDataBox(-toInt(c));
                case LONG: return new LongDataBox(-toLong(c));
                case FLOAT: return new FloatDataBox(-toFloat(c));
                default: throw new RuntimeException("Unreachable code");
            }
        }

        @Override
        protected OperationPriority priority() {
            return OperationPriority.NEGATE;
        }

        @Override
        protected String subclassString() {
            return "-" + children.get(0).toString();
        }
    }
}
