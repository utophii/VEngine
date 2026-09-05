package com.utophii.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

// verifies the safe formula evaluator parses, compiles and evaluates math expressions
class ExpressionEvaluatorTest {

    @Test
    fun `compiles and evaluates a formula with parameters`() {
        val evaluator = requireNotNull(ExpressionEvaluator.compile("R * cos(t)"))
        assertTrue("R" in evaluator.variableNames && "t" in evaluator.variableNames)
        assertEquals(2.0, evaluator.evaluate(mapOf("R" to 2.0, "t" to 0.0)), EPS)
        assertEquals(-2.0, evaluator.evaluate(mapOf("R" to 2.0, "t" to PI)), EPS)
    }

    @Test
    fun `missing variables default to zero`() {
        val evaluator = requireNotNull(ExpressionEvaluator.compile("x + y"))
        assertEquals(0.0, evaluator.evaluate(emptyMap()), EPS)
        assertEquals(5.0, evaluator.evaluate(mapOf("x" to 5.0, "y" to 0.0)), EPS)
    }

    @Test
    fun `built-in constants are available`() {
        val evaluator = requireNotNull(ExpressionEvaluator.compile("tau"))
        assertEquals(MathUtils.TAU, evaluator.evaluate(emptyMap()), EPS)
    }

    @Test
    fun `invalid formula returns null instead of throwing`() {
        assertNull(ExpressionEvaluator.compile("R * "))
        assertNull(ExpressionEvaluator.compile(""))
    }

    @Test
    fun `untracked keys are ignored`() {
        val evaluator = requireNotNull(ExpressionEvaluator.compile("t"))
        assertEquals(1.5, evaluator.evaluate(mapOf("t" to 1.5, "R" to 99.0, "unused" to 3.0)), EPS)
    }

    companion object {
        private const val EPS = 1.0E-6
    }
}
