package edu.berkeley.cs186.database.query;

import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.query.expr.Expression;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.Schema;
import edu.berkeley.cs186.database.table.stats.TableStats;

import java.util.*;

public class ProjectOperator extends QueryOperator {
    // A list of column names to use in the output of this operator
    private List<String> outputColumns;

    // The names of columns in the GROUP BY clause of this query.
    private List<String> groupByColumns;

    // Schema of the source operator
    private Schema sourceSchema;

    // List of expressions that will be evaluated for each record. Each
    // expression corresponds to one of the column names in outputColumns.
    private List<Expression> expressions;

    /**
     * Creates a new ProjectOperator that reads tuples from source and filters
     * out columns. Optionally computes an aggregate if it is specified.
     *
     * @param source
     * @param columns
     * @param groupByColumns
     */
    public ProjectOperator(QueryOperator source, List<String> columns, List<String> groupByColumns) {
        super(OperatorType.PROJECT);
        List<Expression> expressions = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            // No expression objects provided, manually parse each input column
            expressions.add(Expression.fromString(columns.get(i)));
        }
        initialize(source, columns, expressions, groupByColumns);
    }

    public ProjectOperator(QueryOperator source, List<String> columns, List<Expression> expressions, List<String> groupByColumns) {
        super(OperatorType.PROJECT);
        initialize(source, columns, expressions, groupByColumns);
    }

    public void initialize(QueryOperator source, List<String> columns, List<Expression> expressions, List<String> groupByColumns) {
        this.outputColumns = columns;
        this.groupByColumns = groupByColumns;
        this.expressions = expressions;
        this.sourceSchema = source.getSchema();
        this.source = source;
        Schema schema = new Schema();
        for (int i = 0; i < columns.size(); i++) {
            expressions.get(i).setSchema(this.sourceSchema);
            schema.add(columns.get(i), expressions.get(i).getType());
        }
        this.outputSchema = schema;

        Set<Integer> groupByIndices = new HashSet<>();
        for (String colName: groupByColumns) {
            groupByIndices.add(this.sourceSchema.findField(colName));
        }
        boolean hasAgg = false;
        for (int i = 0; i < expressions.size(); i++) {
            hasAgg |= expressions.get(i).hasAgg();
        }
        if (!hasAgg) return;

        for (int i = 0; i < expressions.size(); i++) {
            Set<Integer> dependencyIndices = new HashSet<>();
            for (String colName: expressions.get(i).getDependencies()) {
                dependencyIndices.add(this.sourceSchema.findField(colName));
            }
            if (!expressions.get(i).hasAgg()) {
                dependencyIndices.removeAll(groupByIndices);
                if (dependencyIndices.size() != 0) {
                    int any = dependencyIndices.iterator().next();
                    throw new UnsupportedOperationException(
                            "Non aggregate expression `" + columns.get(i) +
                                    "` refers to ungrouped field `" + sourceSchema.getFieldName(any) + "`"
                    );
                }
            }
        }
    }

    @Override
    public boolean isProject() { return true; }

    @Override
    protected Schema computeSchema() {
        return this.outputSchema;
    }

    @Override
    public Iterator<Record> iterator() {
        return new ProjectIterator();
    }

    @Override
    public String str() {
        String columns = "(" + String.join(", ", this.outputColumns) + ")";
        return "Project (cost=" + this.estimateIOCost() + ")" +
                "\n\tcolumns: " + columns;
    }

    @Override
    public TableStats estimateStats() {
        return this.getSource().estimateStats();
    }

    @Override
    public int estimateIOCost() {
        return this.getSource().estimateIOCost();
    }

    private class ProjectIterator implements Iterator<Record> {
        private Iterator<Record> sourceIterator;
        private boolean hasAgg = false;

        private ProjectIterator() {
            this.sourceIterator = ProjectOperator.this.getSource().iterator();
            for (Expression func: expressions) {
                this.hasAgg |= func.hasAgg();
            }
        }

        @Override
        public boolean hasNext() {
            return this.sourceIterator.hasNext();
        }

        @Override
        public Record next() {
            if (!this.hasNext()) throw new NoSuchElementException();
            Record curr = this.sourceIterator.next();
            if (!this.hasAgg && groupByColumns.size() == 0 ) {
                List<DataBox> newValues = new ArrayList<>();
                for (Expression f: expressions) {
                    newValues.add(f.evaluate(curr));
                }
                return new Record(newValues);
            }

            // Everything after here is to handle aggregation
            Record base = curr; // We'll draw the GROUP BY values from here
            while (curr != GroupByOperator.MARKER) {
                for (Expression dataFunction: expressions) {
                    if (dataFunction.hasAgg()) dataFunction.update(curr);
                }
                if (!sourceIterator.hasNext()) break;
                curr = this.sourceIterator.next();
            }

            // Figure out where to get each value in the output record from
            List<DataBox> values = new ArrayList<>();
            for (Expression dataFunction: expressions) {
                if (dataFunction.hasAgg()) {
                    values.add(dataFunction.evaluate(base));
                    dataFunction.reset();
                } else {
                    values.add(dataFunction.evaluate(base));
                }
            }
            return new Record(values);
        }
    }
}
