package com.utophii.math

import net.objecthunter.exp4j.ExpressionBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BatchExpressionTest {
    @Test
    fun `shared expressions match scalar evaluation across state and frame constants`() {
        val functions = ExpressionFunctions(mapOf("wave(v, a)" to "a*sin(v)", "root(a,b)" to "sqrt(max(0,(sqrt(a*a+b*b)+a)/2))"))
        val formulas = mapOf("wave" to "wave(x, amplitude) + time", "root" to "root(x, y)",
            "guard" to "if(x == 0, 0, 1/x)", "blend" to "mix + mix(x,y,0.25)")
        val batch = ExpressionEvaluator.Batch(formulas, functions)
        val constants = mapOf("amplitude" to 2.0, "time" to 5.0, "mix" to 0.3)
        val frame = batch.frame(constants, setOf("x", "y"))
        for (x in listOf(-2.0, 0.0, 3.0)) {
            frame["x"] = x
            frame["y"] = x + 1
            formulas.forEach { (name, formula) ->
                val expected = ExpressionEvaluator.compileOrThrow(formula, functions).evaluate(constants + mapOf("x" to x, "y" to x+1))
                assertEquals(expected, frame.eval(name), 1e-12, name)
            }
        }
    }

    @Test
    fun `constant folding preserves lazy branches and eager function arguments`() {
        val functions = ExpressionFunctions(mapOf("ignore(v)" to "1"))
        val batch = ExpressionEvaluator.Batch(mapOf(
            "lazy" to "if(zero == 0, 7, 1/zero)", "constant" to "if(1, 9, 1/0)",
            "eager" to "ignore(1/zero)", "short" to "0 && 1/0",
        ), functions)
        val frame = batch.frame(emptyMap(), setOf("zero"))
        frame["zero"] = 0.0
        assertEquals(7.0, frame.eval("lazy"))
        assertEquals(9.0, frame.eval("constant"))
        assertEquals(0.0, frame.eval("short"))
        assertThrows(ArithmeticException::class.java) { frame.eval("eager") }
        frame["zero"] = 2.0
        assertEquals(0.5, frame.eval("lazy"))
        assertEquals(1.0, frame.eval("eager"))
    }

    @Test
    fun `scalar fast paths preserve exp4j arithmetic including signed zeros and invalid divisions`() {
        for (formula in listOf("a+b", "a-b", "a*b", "a/b", "a%b", "a^b", "-a", "+a", "sin(a)", "cos(a)", "sqrt(a)", "abs(a)")) {
            val reference = ExpressionBuilder(formula).variables("a", "b").build()
            val current = ExpressionEvaluator.compileOrThrow(formula)
            for (a in listOf(-2.0, -0.0, 0.0, 0.5, 2.0)) for (b in listOf(-2.0, -0.0, 0.0, 0.5, 2.0)) {
                val expected = runCatching { reference.setVariable("a", a).setVariable("b", b).evaluate() }
                val actual = runCatching { current.evaluate(mapOf("a" to a, "b" to b)) }
                assertEquals(expected.isSuccess, actual.isSuccess, "$formula $a $b")
                if (expected.isSuccess) assertEquals(expected.getOrThrow(), actual.getOrThrow(), "$formula $a $b")
            }
        }
    }

    @Test
    fun `nested functions keep expansion bounded and preserve the result`() {
        val functions = ExpressionFunctions(mapOf("quad(v)" to "v+v+v+v"))
        val formula = "quad(".repeat(10) + "x" + ")".repeat(10)
        val frame = ExpressionEvaluator.Batch(mapOf("value" to formula), functions).frame(emptyMap(), setOf("x"))
        frame["x"] = 1.0
        assertEquals(1048576.0, frame.eval("value"))
    }
}
