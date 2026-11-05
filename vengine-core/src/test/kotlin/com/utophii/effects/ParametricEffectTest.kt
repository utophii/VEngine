package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.ParametricConfig
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

// Verifies a user-defined parametric effect produces the expected analytic coordinates.
class ParametricEffectTest {

    private fun center(): Location = Location(null, 0.0, 0.0, 0.0)

    // a flat circle in the XZ plane: x = R cos(t), y = 0, z = R sin(t)
    private fun circleEffect(samples: Int = 8): ParametricEffect {
        return ParametricEffect(
            name = "test_circle",
            variables = listOf("t"),
            sampling = listOf(samples),
            ranges = listOf(ParamRange(0.0, MathUtils.TAU)),
            xFormula = "R * cos(t)",
            yFormula = "0",
            zFormula = "R * sin(t)",
            defaults = mapOf("R" to 2.0),
            angularSpeed = 0.0,
        )
    }

    @Test
    fun `circle curve matches analytic points`() {
        val effect = circleEffect(4)
        val positions = effect.calculate(center(), EffectOptions(), time = 0.0)
        assertEquals(4, positions.size)

        // t = i / samples * TAU: sample points at 0, 90, 180, 270 degrees
        assertEquals(2.0, positions[0].x, EPS)
        assertEquals(0.0, positions[0].z, EPS)
        assertEquals(0.0, positions[1].x, EPS)
        assertEquals(2.0, positions[1].z, EPS)
        assertEquals(-2.0, positions[2].x, EPS)
        assertEquals(0.0, positions[2].z, EPS)
        assertEquals(0.0, positions[3].x, EPS)
        assertEquals(-2.0, positions[3].z, EPS)
    }

    @Test
    fun `radius parameter is translated to the center`() {
        val center = Location(null, 5.0, -1.0, 3.0)
        val effect = circleEffect(4)
        val positions = effect.calculate(center, EffectOptions(), time = 0.0)
        assertEquals(4, positions.size)
        assertEquals(7.0, positions[0].x, EPS)   // 5 + 2
        assertEquals(-1.0, positions[0].y, EPS)  // unchanged
        assertEquals(3.0, positions[0].z, EPS)
    }

    @Test
    fun `runtime parameters override formula defaults`() {
        val effect = circleEffect(4)
        val opts = EffectOptions.builder().parameter("R", 5.0).build()
        val positions = effect.calculate(center(), opts, time = 0.0)
        assertEquals(5.0, positions[0].x, EPS)
    }

    @Test
    fun `phase advances the curve over time`() {
        val effect = ParametricEffect(
            name = "test_phase",
            variables = listOf("t"),
            sampling = listOf(1),
            ranges = listOf(ParamRange(0.0, MathUtils.TAU)),
            xFormula = "R * cos(t)",
            yFormula = "0",
            zFormula = "R * sin(t)",
            defaults = mapOf("R" to 2.0),
            angularSpeed = 1.0,
        )
        // at t = phase = time * angularSpeed = PI/2, cos = 0 and sin = 1
        val positions = effect.calculate(center(), EffectOptions(), time = Math.PI / 2.0)
        assertEquals(0.0, positions[0].x, EPS)
        assertEquals(2.0, positions[0].z, EPS)
    }

    @Test
    fun `surface produces theta by phi samples`() {
        val surface = ParametricEffect(
            name = "test_torus",
            variables = listOf("theta", "phi"),
            sampling = listOf(4, 4),
            ranges = listOf(ParamRange(0.0, MathUtils.TAU), ParamRange(0.0, MathUtils.TAU)),
            xFormula = "(R + r * cos(phi)) * cos(theta)",
            yFormula = "r * sin(phi)",
            zFormula = "(R + r * cos(phi)) * sin(theta)",
            defaults = mapOf("R" to 1.0, "r" to 0.5),
            angularSpeed = 0.0,
        )
        val positions = surface.calculate(center(), EffectOptions(), time = 0.0)
        assertEquals(16, positions.size)
        assertTrue(positions.all { it.x >= -1.5 - EPS && it.x <= 1.5 + EPS }, "x stays within (R+r)")
    }

    @Test
    fun `age only formula preserves spatial fallback and resets each frame`() {
        val effect = ParametricConfig().apply {
            samples(4)
            range(0.0, 1.0)
            angularSpeed(0.0)
            x("t")
            age("(time - t * 40) / life")
            default("life", 40.0)
        }.buildEffect()
        val buffer = ParticleBuffer(center())
        effect.calculateInto(buffer, center(), EffectOptions(), 40.0)
        assertTrue(!buffer.hasValues(), "age must not occupy the optional spatial value slot")
        for (index in 0 until 4) assertEquals(1.0 - index * 0.25, buffer.ageAt(index), EPS)

        effect.calculateInto(buffer, center(), EffectOptions(parameters = mapOf("life" to 80.0)), 40.0)
        assertEquals(0.5, buffer.ageAt(0), EPS)
        assertEquals(0.125, buffer.ageAt(3), EPS)

        effect.calculateInto(buffer, center(), EffectOptions(), 0.0)
        for (index in 0 until 4) assertEquals(0.0, buffer.ageAt(index), EPS)
        circleEffect(4).calculateInto(buffer, center(), EffectOptions(), 10.0)
        for (index in 0 until 4) assertTrue(buffer.ageAt(index).isNaN(), "no formula restores layer age")
    }

    @Test
    fun `surface age and spatial value use both variables and local functions independently`() {
        val effect = ParametricConfig().apply {
            variables("u", "v")
            samples(4, 4)
            ranges(ParamRange(0.0, 1.0), ParamRange(0.0, 1.0))
            angularSpeed(0.0)
            function("lifetime(a, b, tick)", "a + b + tick / 10")
            value("1 - u")
            age("lifetime(u, v, time)")
        }.buildEffect()
        val buffer = ParticleBuffer(center())
        effect.calculateInto(buffer, center(), EffectOptions(), 0.0)
        for (u in 0 until 4) for (v in 0 until 4) {
            val index = u * 4 + v
            assertEquals(1.0 - u * 0.25, buffer.valueAt(index), EPS)
            assertEquals(((u + v) * 0.25).coerceAtMost(1.0), buffer.ageAt(index), EPS)
        }
        effect.calculateInto(buffer, center(), EffectOptions(), 10.0)
        for (index in 0 until 16) assertEquals(1.0, buffer.ageAt(index), EPS)
    }

    @Test
    fun `nonfinite age fails with a clear error and pooled state remains reusable`() {
        val effect = ParametricConfig().apply { age("sqrt(time - 1)") }.buildEffect()
        val buffer = ParticleBuffer(center())
        val error = assertThrows(IllegalArgumentException::class.java) {
            effect.calculateInto(buffer, center(), EffectOptions(), 0.0)
        }
        assertEquals("Particle age must be finite", error.message)
        effect.calculateInto(buffer, center(), EffectOptions(), 2.0)
        assertEquals(1.0, buffer.ageAt(0), EPS)
    }

    companion object {
        private const val EPS = 1.0E-4
    }
}
