package com.utophii.math

import net.objecthunter.exp4j.ExpressionBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs

// Verifies the safe formula evaluator parses, compiles and evaluates math expressions.
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

    @Test
    fun `forks evaluate independently of each other and the template`() {
        val evaluator = requireNotNull(ExpressionEvaluator.compile("R * t"))
        val first = evaluator.fork()
        val second = evaluator.fork()
        assertEquals(6.0, first.evaluate(mapOf("R" to 2.0, "t" to 3.0)), EPS)
        assertEquals(20.0, second.evaluate(mapOf("R" to 4.0, "t" to 5.0)), EPS)
        assertEquals(6.0, first.evaluate(mapOf("R" to 2.0, "t" to 3.0)), EPS)
        assertEquals(0.0, evaluator.evaluate(emptyMap()), EPS)
    }

    private fun evaluate(formula: String, variables: Map<String, Double> = emptyMap()) =
        requireNotNull(ExpressionEvaluator.compile(formula)) { formula }.evaluate(variables)

    @Test
    fun `clamp limits values and mix supports interpolation and extrapolation`() {
        assertEquals(0.0, evaluate("clamp(-2, 0, 1)"), EPS)
        assertEquals(0.4, evaluate("clamp(0.4, 0, 1)"), EPS)
        assertEquals(1.0, evaluate("clamp(2, 0, 1)"), EPS)
        assertEquals(12.5, evaluate("mix(10, 20, 0.25)"), EPS)
        assertEquals(25.0, evaluate("mix(10, 20, 1.5)"), EPS)
        assertEquals(3.0, evaluate("max(min(9, 3), round(1.6))"), EPS)
    }

    @Test
    fun `noise is bounded deterministic continuous and varies with position`() {
        val expression = requireNotNull(ExpressionEvaluator.compile("noise(x, y, z)"))
        val values = (0..80).map { i -> expression.evaluate(mapOf("x" to i * 0.13, "y" to -0.7, "z" to 2.3)) }
        assertTrue(values.all { it in -1.0..1.0 })
        assertTrue(values.max() - values.min() > 0.1)
        assertEquals(values[10], expression.evaluate(mapOf("x" to 1.3, "y" to -0.7, "z" to 2.3)), EPS)
        val near = expression.evaluate(mapOf("x" to 1.300001, "y" to -0.7, "z" to 2.3))
        assertTrue(abs(near - values[10]) < 1e-4)
    }

    @Test
    fun `conditions evaluate only the selected branch`() {
        assertEquals(0.0, evaluate("if(x != 0, 1 / x, 0)", mapOf("x" to 0.0)), EPS)
        assertEquals(0.5, evaluate("if(x != 0, 1 / x, 0)", mapOf("x" to 2.0)), EPS)
        assertEquals(7.0, evaluate("if(-1, 7, 1 / 0)"), EPS)
        assertEquals(3.0, evaluate("2 + if(0, 1 / 0, if(2, 1, sqrt(-1)))"), EPS)
        assertEquals(0.0, evaluate("0 && 1 / 0"), EPS)
        assertEquals(1.0, evaluate("2 || 1 / 0"), EPS)
        assertEquals(2.0, evaluate("if(x > 0 && x <= 2, 2, 4)", mapOf("x" to 1.0)), EPS)
    }

    @Test
    fun `comparison and logical precedence follows arithmetic`() {
        mapOf(
            "1 < 2 + 3" to 1.0, "2 + 3 > 1" to 1.0, "2 >= 2" to 1.0,
            "2 <= 1" to 0.0, "2 != 2" to 0.0, "2 == 2" to 1.0,
            "1 == 2 < 3" to 1.0, "1 || 0 && 0" to 1.0,
            "0 || 2 && 3" to 1.0, "1 > -2" to 1.0,
        ).forEach { (formula, expected) -> assertEquals(expected, evaluate(formula), EPS, formula) }
    }

    @Test
    fun `arithmetic matches exp4j including unary powers and implicit products`() {
        listOf("-2^2", "2^-2", "2^3^2", "(-2)^2", "2*-3+4", "2--3", "-2^-2",
            "2(3+4)", "(2+3)(4+5)", "2sin(pi/2)", "1e-3 + 2E+2", "5%2", "pow(2,3)",
            "sin(0.4)^2+cos(0.4)^2", "sqrt(9)+cbrt(8)").forEach { formula ->
            assertEquals(ExpressionBuilder(formula).build().evaluate(), evaluate(formula), EPS, formula)
        }
        assertEquals(PI, evaluate("pi"), EPS)
        assertEquals(PI, evaluate("π"), EPS)
        assertEquals(Math.E, evaluate("e"), EPS)
    }

    @Test
    fun `compilation validates syntax without guessing variable values`() {
        assertEquals(1.0, evaluate("1 / (x - 1)", mapOf("x" to 2.0)), EPS)
        assertNotNull(ExpressionEvaluator.compile("clamp(x, low, high)"))
        listOf("sin(1, 2)", "1 + sin(2, 3)", "mix(1, 2)", "if(1, 2, 3, 4)", "noise(1)",
            "unknown(2)", "if(0, unknown(2), 1)", "sin()", "sin(,1)", "sin(1,)",
            "1 +", "(1 + 2", "1,2", "()", "sin 2", "1 = 2").forEach { formula ->
            assertNull(ExpressionEvaluator.compile(formula), formula)
        }
        assertNull(ExpressionEvaluator.compile("(".repeat(200) + "1" + ")".repeat(200)))
    }

    companion object {
        private const val EPS = 1.0E-6
    }
}
