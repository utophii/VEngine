package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.ExpressionEvaluator
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

// verifies the newly converted formula atoms reproduce their analytic geometry and compile cleanly
class FormulaAtomsTest {

    private fun center(): Location = Location(null, 0.0, 0.0, 0.0)
    private val tau = MathUtils.TAU

    @Test
    fun `helix formula matches geometry at sample points`() {
        val helix = ParametricEffect(
            name = "helix",
            variables = listOf("t"),
            sampling = listOf(4),
            ranges = listOf(ParamRange(0.0, tau)),
            xFormula = "radius * cos(turns * t)",
            yFormula = "height * (t / tau) - height * 0.5",
            zFormula = "radius * sin(turns * t)",
            defaults = mapOf("radius" to 1.0, "height" to 3.0, "turns" to 3.0),
            angularSpeed = 0.0,
        )
        val p = helix.calculate(center(), EffectOptions(), 0.0)
        assertEquals(4, p.size)
        // t = 0 => angle 0 => x = R, z = 0, y = -height/2
        assertEquals(1.0, p[0].x, EPS)
        assertEquals(-1.5, p[0].y, EPS)
        assertEquals(0.0, p[0].z, EPS)
    }

    @Test
    fun `torus surface formula matches geometry`() {
        val torus = ParametricEffect(
            name = "torus",
            variables = listOf("theta", "phi"),
            sampling = listOf(4, 4),
            ranges = listOf(ParamRange(0.0, tau), ParamRange(0.0, tau)),
            xFormula = "(majorRadius + minorRadius * cos(phi)) * cos(theta)",
            yFormula = "minorRadius * sin(phi)",
            zFormula = "(majorRadius + minorRadius * cos(phi)) * sin(theta)",
            defaults = mapOf("majorRadius" to 1.4, "minorRadius" to 0.45),
            angularSpeed = 0.0,
        )
        val p = torus.calculate(center(), EffectOptions(), 0.0)
        assertEquals(16, p.size)
        // theta = phi = 0 => x = R + r, y = 0, z = 0
        assertEquals(1.85, p[0].x, EPS)
        assertEquals(0.0, p[0].y, EPS)
        assertEquals(0.0, p[0].z, EPS)
    }

    @Test
    fun `beam formula matches cubic bezier midpoint`() {
        val beam = ParametricEffect(
            name = "beam",
            variables = listOf("t"),
            sampling = listOf(4),
            ranges = listOf(ParamRange(0.0, 1.0)),
            xFormula = "(1 - t)^3 * (-0.5 * length) + 3 * (1 - t)^2 * t * (-0.25 * length) + 3 * (1 - t) * t^2 * (0.25 * length) + t^3 * (0.5 * length)",
            yFormula = "3 * (1 - t)^2 * t * height + 3 * (1 - t) * t^2 * height + sin(t * tau + time * waveSpeed) * waveAmplitude",
            zFormula = "0",
            defaults = mapOf("length" to 5.0, "height" to 1.2, "waveSpeed" to 0.2, "waveAmplitude" to 0.08),
            angularSpeed = 0.0,
        )
        val p = beam.calculate(center(), EffectOptions(), 0.0)
        assertEquals(4, p.size)
        // t = 0.5 at index 2 => x = 0, y = 0.75*height + sin(pi)*amp = 0.9
        assertEquals(0.0, p[2].x, EPS)
        assertEquals(0.9, p[2].y, EPS)
        assertEquals(0.0, p[2].z, EPS)
    }

    @Test
    fun `supershape formulas compile to valid expressions`() {
        val r1 = "(abs(cos(m1 * theta / 4) / a1)^n12 + abs(sin(m1 * theta / 4) / b1)^n13)^(-1 / n11)"
        val r2 = "(abs(cos(m2 * phi / 4) / a2)^n22 + abs(sin(m2 * phi / 4) / b2)^n23)^(-1 / n21)"
        val x = "radius * $r1 * cos(theta) * $r2 * cos(phi)"
        val y = "radius * $r2 * sin(phi)"
        val z = "radius * $r1 * sin(theta) * $r2 * cos(phi)"
        assertNotNull(ExpressionEvaluator.compile(x))
        assertNotNull(ExpressionEvaluator.compile(y))
        assertNotNull(ExpressionEvaluator.compile(z))
    }

    companion object {
        private const val EPS = 1.0E-3
    }
}
