package edu.berkeley.cs186.database.query.expr;

import edu.berkeley.cs186.database.categories.SystemTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.assertEquals;

/**
 * These tests should pass by default, and are NOT part of any assignments!
 */
@Category({SystemTests.class})
public class TestToString {
    // Rather than testing expected output for every possible input, we check
    // that parsing an expression, and then parsing it's toString() output
    // results in the same expression.
    public void reparse(String s) {
        Expression a = Expression.fromString(s);
        Expression b = Expression.fromString(a.toString());
        assertEquals("Mismatch when reparsing: " + s, a.toString(), b.toString());
    }

    @Test
    public void testParentheses() {
        reparse("((a))");
    }

    @Test
    public void testParentheses2() {
        reparse("((a) + b)");
    }

    @Test
    public void testParentheses3() {
        reparse("((a) + b * (6 / 5 * (2 % 2)))");
    }

    @Test
    public void testNots() {
        reparse("NOT (NOT (A + B) OR NOT C)");
    }

    @Test
    public void testNots2() {
        reparse("NOT NOT (NOT (A + B) OR NOT NOT C)");
    }

    @Test
    public void testFunction() {
        reparse("NEGATE((a + b) * c + --NEGATE(13))");
    }

    @Test
    public void testFunction2() {
        reparse("((NEGATE(NEGATE((a + b) * c + --NEGATE(13)))))");
    }
}
