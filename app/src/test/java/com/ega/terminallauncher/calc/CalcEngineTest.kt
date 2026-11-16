package com.ega.terminallauncher.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcEngineTest {

    @Test
    fun testBasicAddition() {
        val result = CalcEngine.evaluate("2 + 2")
        assertTrue(result is CalcResult.Success)
        assertEquals("4", (result as CalcResult.Success).formatted)
        assertEquals(4.0, result.value, 0.0001)
    }

    @Test
    fun testParenthesesAndPrecedence() {
        val result = CalcEngine.evaluate("(10 - 2) * 3 / 4")
        assertTrue(result is CalcResult.Success)
        assertEquals("6", (result as CalcResult.Success).formatted)
        assertEquals(6.0, result.value, 0.0001)
    }

    @Test
    fun testExponentiationRightAssociativity() {
        val r1 = CalcEngine.evaluate("2 ^ 3")
        assertTrue(r1 is CalcResult.Success)
        assertEquals("8", (r1 as CalcResult.Success).formatted)

        val r2 = CalcEngine.evaluate("2 ^ 3 ^ 2")
        assertTrue(r2 is CalcResult.Success)
        assertEquals("512", (r2 as CalcResult.Success).formatted)
    }

    @Test
    fun testModulo() {
        val result = CalcEngine.evaluate("10 % 3")
        assertTrue(result is CalcResult.Success)
        assertEquals("1", (result as CalcResult.Success).formatted)
    }

    @Test
    fun testUnaryMinus() {
        val r1 = CalcEngine.evaluate("-5 + 10")
        assertTrue(r1 is CalcResult.Success)
        assertEquals("5", (r1 as CalcResult.Success).formatted)

        val r2 = CalcEngine.evaluate("2 * -3")
        assertTrue(r2 is CalcResult.Success)
        assertEquals("-6", (r2 as CalcResult.Success).formatted)

        val r3 = CalcEngine.evaluate("-(4 + 2)")
        assertTrue(r3 is CalcResult.Success)
        assertEquals("-6", (r3 as CalcResult.Success).formatted)
    }

    @Test
    fun testDecimals() {
        val r1 = CalcEngine.evaluate("3.5 * 2.0")
        assertTrue(r1 is CalcResult.Success)
        assertEquals("7", (r1 as CalcResult.Success).formatted)

        val r2 = CalcEngine.evaluate("3.5 * 3")
        assertTrue(r2 is CalcResult.Success)
        assertEquals("10.5", (r2 as CalcResult.Success).formatted)
    }

    @Test
    fun testDivisionByZero() {
        val r1 = CalcEngine.evaluate("10 / 0")
        assertTrue(r1 is CalcResult.Error)
        assertEquals("[Error: Division by zero]", (r1 as CalcResult.Error).message)

        val r2 = CalcEngine.evaluate("5 % 0")
        assertTrue(r2 is CalcResult.Error)
        assertEquals("[Error: Division by zero]", (r2 as CalcResult.Error).message)
    }

    @Test
    fun testMismatchedParentheses() {
        val r1 = CalcEngine.evaluate("(2 + 3 * 4")
        assertTrue(r1 is CalcResult.Error)
        assertEquals("[Error: Mismatched parentheses]", (r1 as CalcResult.Error).message)

        val r2 = CalcEngine.evaluate("2 + 3) * 4")
        assertTrue(r2 is CalcResult.Error)
        assertEquals("[Error: Mismatched parentheses]", (r2 as CalcResult.Error).message)
    }

    @Test
    fun testInvalidExpression() {
        val r1 = CalcEngine.evaluate("2 + * 3")
        assertTrue(r1 is CalcResult.Error)
        assertEquals("[Error: Invalid expression]", (r1 as CalcResult.Error).message)

        val r2 = CalcEngine.evaluate("abc")
        assertTrue(r2 is CalcResult.Error)
        assertEquals("[Error: Invalid expression]", (r2 as CalcResult.Error).message)

        val r3 = CalcEngine.evaluate("")
        assertTrue(r3 is CalcResult.Error)
        assertEquals("[Usage: calc <expression>]", (r3 as CalcResult.Error).message)
    }
}
