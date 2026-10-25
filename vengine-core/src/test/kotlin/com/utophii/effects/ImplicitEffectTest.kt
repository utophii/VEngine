package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.ExpressionEvaluator
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class ImplicitEffectTest {

    private fun center(): Location = Location(null, 0.0, 0.0, 0.0)

    private fun effect(formula: String, value: String? = null, parameters: Map<String, Double> = emptyMap()) = ImplicitEffect(
        name = "implicit",
        formula = formula,
        valueFormula = value,
        bounds = 1.5,
        defaultPoints = 300,
        defaults = parameters,
        angularSpeed = 0.0,
    )

    private fun frame(effect: ImplicitEffect, time: Double = 0.0): ParticleBuffer {
        val buffer = ParticleBuffer(center())
        effect.calculateInto(buffer, center(), EffectOptions(), time)
        return buffer
    }

    @Test
    fun `sphere sdf puts every point on the sphere`() {
        val buffer = frame(effect("sdSphere(x, y, z, r)", parameters = mapOf("r" to 1.2)))
        assertEquals(300, buffer.list().size, "an sdf reaches its surface from every seed")
        buffer.list().forEach { p -> assertEquals(1.2, sqrt(p.x * p.x + p.y * p.y + p.z * p.z), 1e-3) }
    }

    @Test
    fun `box sdf puts every point on a face`() {
        val buffer = frame(effect("sdBox(x, y, z, 1, 0.5, 0.8)"))
        buffer.list().forEach { p ->
            val outside = max(abs(p.x) - 1.0, max(abs(p.y) - 0.5, abs(p.z) - 0.8))
            assertEquals(0.0, outside, 1e-2)
        }
    }

    @Test
    fun `formulas may move with time`() {
        val moving = effect("sdSphere(x - time * 0.01, y, z, 0.5)")
        val buffer = frame(moving, time = 50.0)
        buffer.list().forEach { p -> assertEquals(0.5, sqrt((p.x - 0.5) * (p.x - 0.5) + p.y * p.y + p.z * p.z), 1e-3) }
    }

    @Test
    fun `seeds that miss the surface are dropped`() {
        // a sphere far outside the sampled cube
        val buffer = frame(effect("sdSphere(x - 10, y, z, 0.5)"))
        assertEquals(0, buffer.list().size)
    }

    @Test
    fun `value formula colors the points`() {
        val buffer = frame(effect("sdSphere(x, y, z, 1)", value = "(x + 1) / 2"))
        buffer.list().forEachIndexed { index, p -> assertEquals((p.x + 1) / 2, buffer.valueAt(index), 1e-9) }
    }

    @Test
    fun `sdf helpers match their closed forms`() {
        fun eval(formula: String) = ExpressionEvaluator.compile(formula)!!.evaluate(emptyMap())
        assertEquals(1.0, eval("sdSphere(2, 0, 0, 1)"), 1e-12)
        assertEquals(-0.5, eval("sdBox(0, 0, 0, 0.5, 1, 1)"), 1e-12)
        assertEquals(sqrt(2.0), eval("sdBox(2, 2, 0, 1, 1, 1)"), 1e-12)
        assertEquals(0.0, eval("sdTorus(1.25, 0, 0, 1, 0.25)"), 1e-12)
        assertEquals(1.0, eval("smin(1, 3, 0.5)"), 1e-12, "far apart values are unchanged")
        assertTrue(eval("smin(1, 1, 0.5)") < 1.0, "close values blend below the minimum")
        assertEquals(3.0, eval("smax(1, 3, 0.5)"), 1e-12)
    }
}
