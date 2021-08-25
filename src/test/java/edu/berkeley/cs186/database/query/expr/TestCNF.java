package edu.berkeley.cs186.database.query.expr;

import edu.berkeley.cs186.database.categories.SystemTests;
import edu.berkeley.cs186.database.databox.Type;
import edu.berkeley.cs186.database.table.Record;
import edu.berkeley.cs186.database.table.Schema;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * These tests should pass by default, and are NOT part of any assignments!
 */
@Category({SystemTests.class})
public class TestCNF {
    @Test
    public void testNoChange() {
        Expression e = Expression.fromString("a");
        List<Expression> cnf = e.toCNF();
        assertEquals(1, cnf.size());
        assertEquals("a", cnf.get(0).toString());
    }

    @Test
    public void testNoChange2() {
        Expression e = Expression.fromString("a OR b");
        List<Expression> cnf = e.toCNF();
        assertEquals(1, cnf.size());
        assertEquals("a OR b", cnf.get(0).toString());
    }

    @Test
    public void testNoChange3() {
        Expression e = Expression.fromString("NOT a");
        List<Expression> cnf = e.toCNF();
        assertEquals(1, cnf.size());
        assertEquals("NOT a", cnf.get(0).toString());
    }

    @Test
    public void testBasicAnd() {
        Expression e = Expression.fromString("a AND b");
        List<Expression> cnf = e.toCNF();
        assertEquals(2, cnf.size());
        assertEquals("a", cnf.get(0).toString());
        assertEquals("b", cnf.get(1).toString());
    }

    @Test
    public void testBasicNot() {
        Expression e = Expression.fromString("NOT (a OR b)");
        List<Expression> cnf = e.toCNF();
        assertEquals(2, cnf.size());
        assertEquals("NOT a", cnf.get(0).toString());
        assertEquals("NOT b", cnf.get(1).toString());
    }

    @Test
    public void testMulipleNot() {
        Expression e = Expression.fromString("NOT (a OR b OR c OR d)");
        List<Expression> cnf = e.toCNF();
        assertEquals(4, cnf.size());
        assertEquals("NOT a", cnf.get(0).toString());
        assertEquals("NOT b", cnf.get(1).toString());
        assertEquals("NOT c", cnf.get(2).toString());
        assertEquals("NOT d", cnf.get(3).toString());
    }

    @Test
    public void testNotAnd() {
        Expression e = Expression.fromString("NOT (a AND b)");
        List<Expression> cnf = e.toCNF();
        assertEquals(1, cnf.size());
        assertEquals("NOT a OR NOT b", cnf.get(0).toString());
    }

    @Test
    public void testNotNot() {
        Expression e = Expression.fromString("NOT (NOT a)");
        List<Expression> cnf = e.toCNF();
        assertEquals(1, cnf.size());
        assertEquals("a", cnf.get(0).toString());
    }

    @Test
    public void testNotNot2() {
        Expression e = Expression.fromString("NOT NOT a");
        List<Expression> cnf = e.toCNF();
        assertEquals(1, cnf.size());
        assertEquals("a", cnf.get(0).toString());
    }

    @Test
    public void testNotNotNot() {
        Expression e = Expression.fromString("NOT (NOT (NOT a))");
        List<Expression> cnf = e.toCNF();
        assertEquals(1, cnf.size());
        assertEquals("(NOT a)", cnf.get(0).toString());
    }

    @Test
    public void testNotNotNot2() {
        Expression e = Expression.fromString("NOT NOT NOT a");
        List<Expression> cnf = e.toCNF();
        assertEquals(1, cnf.size());
        assertEquals("NOT a", cnf.get(0).toString());
    }

    @Test
    public void testMergeNestedAnds() {
        Expression e = Expression.fromString("a AND (b AND c)");
        List<Expression> cnf = e.toCNF();
        assertEquals(3, cnf.size());
        assertEquals("a", cnf.get(0).toString());
        assertEquals("b", cnf.get(1).toString());
        assertEquals("c", cnf.get(2).toString());
    }

    @Test
    public void testMergeNestedAnds2() {
        Expression e = Expression.fromString("a AND (b AND c AND d)");
        List<Expression> cnf = e.toCNF();
        assertEquals(4, cnf.size());
        assertEquals("a", cnf.get(0).toString());
        assertEquals("b", cnf.get(1).toString());
        assertEquals("c", cnf.get(2).toString());
        assertEquals("d", cnf.get(3).toString());
    }

    @Test
    public void testMergeNestedAnds3() {
        Expression e = Expression.fromString("((a AND b) AND (c AND d))");
        List<Expression> cnf = e.toCNF();
        assertEquals(4, cnf.size());
        assertEquals("a", cnf.get(0).toString());
        assertEquals("b", cnf.get(1).toString());
        assertEquals("c", cnf.get(2).toString());
        assertEquals("d", cnf.get(3).toString());
    }

    @Test
    public void testMergeNestedOrs() {
        Expression e = Expression.fromString("a OR (b OR c)");
        List<Expression> cnf = e.toCNF();
        assertEquals(1, cnf.size());
        assertEquals("a OR b OR c", cnf.get(0).toString());
    }

    @Test
    public void testMergeNestedOrs2() {
        Expression e = Expression.fromString("a OR (b OR c OR d)");
        List<Expression> cnf = e.toCNF();
        assertEquals(1, cnf.size());
        assertEquals("a OR b OR c OR d", cnf.get(0).toString());
    }

    @Test
    public void testMergeNestedOrs3() {
        Expression e = Expression.fromString("((a OR b) OR (c OR d))");
        List<Expression> cnf = e.toCNF();
        assertEquals(1, cnf.size());
        assertEquals("a OR b OR c OR d", cnf.get(0).toString());
    }

    @Test
    public void testOrOf2Ands() {
        Expression e = Expression.fromString("(a AND b) OR (c AND d)");
        List<Expression> cnf = e.toCNF();
        assertEquals(4, cnf.size());
        assertEquals("a OR c", cnf.get(0).toString());
        assertEquals("a OR d", cnf.get(1).toString());
        assertEquals("b OR c", cnf.get(2).toString());
        assertEquals("b OR d", cnf.get(3).toString());
    }

    @Test
    public void testOrOf3Ands() {
        Expression e = Expression.fromString("(a AND b) OR (c AND d) OR (e AND f)");
        List<Expression> cnf = e.toCNF();
        assertEquals(8, cnf.size());
        assertEquals("a OR c OR e", cnf.get(0).toString());
        assertEquals("a OR c OR f", cnf.get(1).toString());
        assertEquals("a OR d OR e", cnf.get(2).toString());
        assertEquals("a OR d OR f", cnf.get(3).toString());
        assertEquals("b OR c OR e", cnf.get(4).toString());
        assertEquals("b OR c OR f", cnf.get(5).toString());
        assertEquals("b OR d OR e", cnf.get(6).toString());
        assertEquals("b OR d OR f", cnf.get(7).toString());
    }

    @Test
    public void testOrderOfOperations() {
        Expression e = Expression.fromString("a AND b OR c AND d OR e AND f");
        List<Expression> cnf = e.toCNF();
        assertEquals(8, cnf.size());
        assertEquals("a OR c OR e", cnf.get(0).toString());
        assertEquals("a OR c OR f", cnf.get(1).toString());
        assertEquals("a OR d OR e", cnf.get(2).toString());
        assertEquals("a OR d OR f", cnf.get(3).toString());
        assertEquals("b OR c OR e", cnf.get(4).toString());
        assertEquals("b OR c OR f", cnf.get(5).toString());
        assertEquals("b OR d OR e", cnf.get(6).toString());
        assertEquals("b OR d OR f", cnf.get(7).toString());
    }

    @Test
    public void test3x2OrOfAnds() {
        Expression e = Expression.fromString("a AND b AND c OR d AND e");
        List<Expression> cnf = e.toCNF();
        assertEquals(6, cnf.size());
        assertEquals("a OR d", cnf.get(0).toString());
        assertEquals("a OR e", cnf.get(1).toString());
        assertEquals("b OR d", cnf.get(2).toString());
        assertEquals("b OR e", cnf.get(3).toString());
        assertEquals("c OR d", cnf.get(4).toString());
        assertEquals("c OR e", cnf.get(5).toString());
    }

    @Test
    public void test2x3OrOfAnds() {
        Expression e = Expression.fromString("a AND b OR c AND d AND e");
        List<Expression> cnf = e.toCNF();
        assertEquals(6, cnf.size());
        assertEquals("a OR c", cnf.get(0).toString());
        assertEquals("a OR d", cnf.get(1).toString());
        assertEquals("a OR e", cnf.get(2).toString());
        assertEquals("b OR c", cnf.get(3).toString());
        assertEquals("b OR d", cnf.get(4).toString());
        assertEquals("b OR e", cnf.get(5).toString());
    }

    public boolean evalCNF(List<Expression> cnf, Schema s, Record r) {
        boolean result = true;
        for (Expression e: cnf) {
            e.setSchema(s);
            result &= e.evaluate(r).getBool();
        }
        return result;
    }

    @Test
    public void testFuzz() {
        String base = "%s %s %s %s %s %s %s %s %s";
        boolean[] vals = {true, false};
        Schema s = new Schema()
                .add("a", Type.boolType())
                .add("b", Type.boolType())
                .add("c", Type.boolType())
                .add("d", Type.boolType())
                .add("e", Type.boolType());
        for (int i = 0; i < 512; i++) {
            String a = (i >> 0) % 2 == 0 ? "a" : "NOT a";
            String b = (i >> 1) % 2 == 0 ? "b" : "NOT b";
            String c = (i >> 2) % 2 == 0 ? "c" : "NOT c";
            String d = (i >> 3) % 2 == 0 ? "d" : "NOT d";
            String e = (i >> 4) % 2 == 0 ? "d" : "NOT d";
            String op1 = (i >> 5) % 2 == 0 ? "AND" : "OR";
            String op2 = (i >> 6) % 2 == 0 ? "AND" : "OR";
            String op3 = (i >> 7) % 2 == 0 ? "AND" : "OR";
            String op4 = (i >> 8) % 2 == 0 ? "AND" : "OR";
            String expr = String.format(base, a, op1, b, op2, c, op3, d, op4, e);
            Expression exp = Expression.fromString(expr);
            exp.setSchema(s);
            List<Expression> cnf = exp.toCNF();
            for (Expression cnfe: cnf) {
                cnfe.setSchema(s);
            }
            for (int j = 0; j < 32; j++) {
                Record r = new Record(
                    vals[(j >> 0) % 2],
                    vals[(j >> 1) % 2],
                    vals[(j >> 2) % 2],
                    vals[(j >> 3) % 2],
                    vals[(j >> 4) % 2]
                );
                if (exp.evaluate(r).getBool() != evalCNF(cnf, s, r)) {
                    throw new AssertionError("Failed with expression: " + expr + " and CNF: " + cnf);
                }
            }
        }
    }
}
