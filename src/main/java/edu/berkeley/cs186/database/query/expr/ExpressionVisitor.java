package edu.berkeley.cs186.database.query.expr;

import edu.berkeley.cs186.database.cli.PrettyPrinter;
import edu.berkeley.cs186.database.cli.parser.*;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.databox.IntDataBox;

import java.util.ArrayList;
import java.util.List;

public class ExpressionVisitor extends RookieParserDefaultVisitor {
    Expression expr;

    @Override
    public void visit(ASTOrExpression node, Object data) {
        OrExpressionVisitor oev = new OrExpressionVisitor();
        node.jjtAccept(oev, data);
        this.expr = oev.build();
    }

    public Expression build() {
        return this.expr;
    }

    private class OrExpressionVisitor extends RookieParserDefaultVisitor {
        private List<Expression> operands = new ArrayList<>();

        @Override
        public void visit(ASTAndExpression node, Object data) {
            AndExpressionVisitor aev = new AndExpressionVisitor();
            node.jjtAccept(aev, data);
            this.operands.add(aev.build());
        }

        public Expression build() {
            if (this.operands.size() == 1) return this.operands.get(0);
            Expression[] children = new Expression[operands.size()];
            operands.toArray(children);
            return Expression.or(children);
        }
    }

    private class AndExpressionVisitor extends RookieParserDefaultVisitor {
        private List<Expression> operands = new ArrayList<>();

        @Override
        public void visit(ASTNotExpression node, Object data) {
            NotExpressionVisitor nev = new NotExpressionVisitor();
            node.jjtAccept(nev, data);
            this.operands.add(nev.build());
        }

        public Expression build() {
            if (this.operands.size() == 1) return this.operands.get(0);
            Expression[] children = new Expression[operands.size()];
            operands.toArray(children);
            return Expression.and(children);
        }
    }

    private class NotExpressionVisitor extends RookieParserDefaultVisitor {
        private boolean hasNot = false;
        private Expression expr;

        @Override
        public void visit(ASTNotOperator node, Object data) {
            this.hasNot ^= true;
        }

        @Override
        public void visit(ASTComparisonExpression node, Object data) {
            ComparisonExpressionVisitor cev = new ComparisonExpressionVisitor();
            node.jjtAccept(cev, data);
            this.expr = cev.build();
        }

        public Expression build() {
            if (this.hasNot) return Expression.not(this.expr);
            return this.expr;
        }
    }

    private class ComparisonExpressionVisitor extends RookieParserDefaultVisitor {
        private List<Expression> operands = new ArrayList<>();
        private List<String> operators = new ArrayList<>();

        @Override
        public void visit(ASTComparisonOperator node, Object data) {
            this.operators.add((String) node.jjtGetValue());
        }

        @Override
        public void visit(ASTAdditiveExpression node, Object data) {
            AdditiveExpressionVisitor aev = new AdditiveExpressionVisitor();
            node.jjtAccept(aev, data);
            this.operands.add(aev.build());
        }

        public Expression build() {
            if (this.operands.size() == 1) return this.operands.get(0);
            Expression prev = this.operands.get(0);
            for (int i = 0; i < operators.size(); i++) {
                Expression curr = this.operands.get(i+1);
                prev = Expression.compare(this.operators.get(i), prev, curr);
            }
            return prev;
        }
    }

    private class AdditiveExpressionVisitor extends RookieParserDefaultVisitor {
        private List<Expression> operands = new ArrayList<>();
        private List<Character> operators = new ArrayList<>();

        @Override
        public void visit(ASTAdditiveOperator node, Object data) {
            this.operators.add(((String) node.jjtGetValue()).charAt(0));
        }

        @Override
        public void visit(ASTMultiplicativeExpression node, Object data) {
            MultiplicativeExpressionVisitor mev = new MultiplicativeExpressionVisitor();
            node.jjtAccept(mev, data);
            this.operands.add(mev.build());
        }

        public Expression build() {
            if (this.operands.size() == 1) return this.operands.get(0);
            Expression[] children = new Expression[operands.size()];
            operands.toArray(children);
            return Expression.additive(operators, children);
        }
    }

    private class MultiplicativeExpressionVisitor extends RookieParserDefaultVisitor {
        private List<Expression> operands = new ArrayList<>();
        private List<Character> operators = new ArrayList<>();

        @Override
        public void visit(ASTMultiplicativeOperator node, Object data) {
            this.operators.add(((String) node.jjtGetValue()).charAt(0));
        }

        @Override
        public void visit(ASTPrimaryExpression node, Object data) {
            PrimaryExpressionVisitor pev = new PrimaryExpressionVisitor();
            node.jjtAccept(pev, data);
            this.operands.add(pev.build());
        }

        public Expression build() {
            if (this.operands.size() == 1) return this.operands.get(0);
            Expression[] children = new Expression[operands.size()];
            operands.toArray(children);
            return Expression.multiplicative(operators, children);
        }
    }

    private class PrimaryExpressionVisitor extends RookieParserDefaultVisitor {
        private boolean negated = false;
        boolean seenRoot = false;
        private Expression childExpr;

        @Override
        public void visit(ASTPrimaryExpression node, Object data) {
            if (!seenRoot) {
                seenRoot = true;
                super.visit(node, data);
            } else {
                PrimaryExpressionVisitor pev = new PrimaryExpressionVisitor();
                node.jjtAccept(pev, data);
                this.childExpr = pev.build();
                if (negated) {
                    this.childExpr = Expression.negate(pev.build());
                }
            }
        }

        @Override
        public void visit(ASTExpression node, Object data) {
            ExpressionVisitor ev = new ExpressionVisitor();
            node.jjtAccept(ev, data);
            this.childExpr = ev.build();
            this.childExpr.needsParentheses = true;
        }

        @Override
        public void visit(ASTLiteral node, Object data) {
            DataBox value = PrettyPrinter.parseLiteral((String) node.jjtGetValue());
            this.childExpr = Expression.literal(value);
        }

        @Override
        public void visit(ASTColumnName node, Object data) {
            String columnName = (String) node.jjtGetValue();
            this.childExpr = Expression.column(columnName);
        }

        @Override
        public void visit(ASTAdditiveOperator node, Object data) {
            String symbol = (String) node.jjtGetValue();
            if (symbol == "-") this.negated ^= true;
        }

        @Override
        public void visit(ASTFunctionCallExpression node, Object data) {
            FunctionCallVisitor fcv = new FunctionCallVisitor();
            node.jjtAccept(fcv, data);
            this.childExpr = fcv.build();
        }

        public Expression build() {
            return this.childExpr;
        }
    }

    private class FunctionCallVisitor extends RookieParserDefaultVisitor {
        String functionName;
        List<Expression> operands = new ArrayList<>();

        @Override
        public void visit(ASTIdentifier node, Object data) {
            functionName = (String) node.jjtGetValue();
        }

        @Override
        public void visit(ASTExpression node, Object data) {
            ExpressionVisitor ev = new ExpressionVisitor();
            node.jjtAccept(ev, data);
            this.operands.add(ev.build());
        }

        public Expression build() {
            if (this.operands.size() == 1 || (this.functionName.toUpperCase().equals("COUNT"))) {
                Expression child;
                if (this.operands.size() == 1) child = this.operands.get(0);
                else child = Expression.literal(new IntDataBox(0));
                Expression agg = Expression.function(this.functionName, child);
                if (agg != null) return agg;
            }
            Expression[] children = new Expression[this.operands.size()];
            for (int i = 0; i < operands.size(); ++i) {
                children[i] = operands.get(i);
            }
            Expression reg = Expression.function(functionName, children);
            if (reg != null) return reg;
            throw new UnsupportedOperationException("Unknown function `" + functionName + "`" + " (with " + this.operands.size() + " argument(s))");
        }
    }
}
