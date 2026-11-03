package com.utophii.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class ExpressionFunctionsTest {
    @Test
    fun `functions support forward references nested calls and explicit argument order`() {
        val functions = ExpressionFunctions(linkedMapOf(
            "wave(u, a)" to "a * unit(u)",
            "unit(u)" to "sin(u * quarter())",
            "quarter()" to "PI / 2",
            "subtract(first, second)" to "first - second",
            "keepSecond(first, second)" to "second",
        ))
        val evaluator = requireNotNull(ExpressionEvaluator.compile("wave(t, R) + subtract(9, 4) + keepSecond(99, 2)", functions))
        assertEquals(setOf("t", "R"), evaluator.variableNames)
        assertEquals(10.0, evaluator.evaluate(mapOf("t" to 1.0, "R" to 3.0)), 1e-9)
    }

    @Test
    fun `lazy branches also work inside functions`() {
        val functions = ExpressionFunctions(mapOf("inverse(x)" to "if(x == 0, 0, 1 / x)"))
        val evaluator = requireNotNull(ExpressionEvaluator.compile("inverse(x)", functions))
        assertEquals(0.0, evaluator.evaluate(mapOf("x" to 0.0)))
        assertEquals(0.5, evaluator.evaluate(mapOf("x" to 2.0)))
    }

    @Test
    fun `definitions reject cycles unknown dependencies and implicit captures`() {
        listOf(
            mapOf("loop(x)" to "loop(x)"),
            mapOf("first(x)" to "second(x)", "second(x)" to "first(x)"),
            mapOf("wave(x)" to "sin(x + time)"),
            mapOf("wave(x)" to "missing(x)"),
            mapOf("wave(x,x)" to "x"),
            mapOf("wave(x,)" to "x"),
            mapOf("wave(x)" to "sin(x, 2)"),
            mapOf("sin(x)" to "x"),
            mapOf("if(x)" to "x"),
            mapOf("PI()" to "3"),
            mapOf("wave(sin)" to "sin"),
            mapOf("wave(x)" to "x", "wave(x,y)" to "x+y"),
            mapOf("wave(x)" to "x", "other(wave)" to "wave"),
            mapOf("bad signature" to "x"),
        ).forEach { definitions ->
            assertThrows(IllegalArgumentException::class.java, { ExpressionFunctions(definitions) }, definitions.toString())
        }
    }

    @Test
    fun `registries and forks do not share variable values even under concurrent calls`() {
        val functions = ExpressionFunctions(mapOf("difference(a, b)" to "a - b", "twice(x)" to "difference(x * 2, x) + x"))
        val evaluator = requireNotNull(ExpressionEvaluator.compile("twice(x) + difference(x + 3, x)", functions))
        val pool = Executors.newFixedThreadPool(4)
        try {
            val jobs = (0..15).map { task -> Callable {
                val fork = evaluator.fork()
                fork.bind(mapOf("x" to task.toDouble()))
                repeat(200) { i ->
                    val x = task * 1000.0 + i
                    fork.set("x", x)
                    assertEquals(x * 2 + 3, fork.evaluate(), 1e-9)
                }
                fork.bind(emptyMap())
                assertEquals(3.0, fork.evaluate())
            } }
            pool.invokeAll(jobs).forEach { it.get() }
        } finally {
            pool.shutdownNow()
        }
        val alternate = ExpressionFunctions(mapOf("twice(x)" to "x * 10"))
        assertEquals(20.0, ExpressionEvaluator.compile("twice(x)", alternate)!!.evaluate(mapOf("x" to 2.0)))
        assertEquals(7.0, evaluator.evaluate(mapOf("x" to 2.0)))
        assertTrue(ExpressionEvaluator.compile("twice(2)") == null)
    }

    @Test
    fun `acyclic definitions cannot expand into exponential work`() {
        val definitions = linkedMapOf("level0(x)" to "x")
        for (level in 1..30) {
            definitions["level$level(x)"] = "level${level - 1}(x) + level${level - 1}(x)"
        }
        val error = assertThrows(IllegalArgumentException::class.java) { ExpressionFunctions(definitions) }
        assertTrue(error.message!!.contains("evaluation steps"), error.message)
    }
}
