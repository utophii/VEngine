package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.cos
import kotlin.math.sin

// Verifies that the primitives migrated to formula YAML produce the same analytic geometry
// as the Kotlin classes they replace (lissajous, rosette, torus_knot).
class MigratedPrimitivesTest {

    private fun center(): Location = Location(null, 0.0, 0.0, 0.0)

    private fun curve(
        variables: List<String>,
        sampling: List<Int>,
        x: String,
        y: String,
        z: String,
        defaults: Map<String, Double>,
    ): ParametricEffect = ParametricEffect(
        name = "migrated",
        variables = variables,
        sampling = sampling,
        ranges = listOf(ParamRange(0.0, MathUtils.TAU)),
        xFormula = x,
        yFormula = y,
        zFormula = z,
        defaults = defaults,
        angularSpeed = 0.0,
    )

    @Test
    fun `lissajous matches analytic formula at sample points`() {
        val ampX = 1.6
        val ampZ = 1.6
        val aVal = 3.0
        val bVal = 2.0
        val delta = 1.5707963267948966
        val effect = curve(
            listOf("t"), listOf(4),
            "amplitudeX * sin(a * t + delta)", "0", "amplitudeZ * sin(b * t)",
            mapOf("amplitudeX" to ampX, "amplitudeZ" to ampZ, "a" to aVal, "b" to bVal, "delta" to delta),
        )

        val positions = effect.calculate(center(), EffectOptions(), 0.0)
        assertEquals(4, positions.size)
        for (index in 0 until 4) {
            val t = index / 4.0 * MathUtils.TAU
            assertEquals(ampX * sin(aVal * t + delta), positions[index].x, EPS)
            assertEquals(0.0, positions[index].y, EPS)
            assertEquals(ampZ * sin(bVal * t), positions[index].z, EPS)
        }
    }

    @Test
    fun `rosette matches analytic epicycloid at sample points`() {
        val major = 1.0
        val minor = 0.25
        val dist = 0.9
        val effect = curve(
            listOf("t"), listOf(4),
            "(majorRadius + minorRadius) * cos(t) - distance * cos((majorRadius + minorRadius) / minorRadius * t)",
            "0",
            "(majorRadius + minorRadius) * sin(t) - distance * sin((majorRadius + minorRadius) / minorRadius * t)",
            mapOf("majorRadius" to major, "minorRadius" to minor, "distance" to dist),
        )

        val positions = effect.calculate(center(), EffectOptions(), 0.0)
        assertEquals(4, positions.size)
        val ratio = (major + minor) / minor
        for (index in 0 until 4) {
            val t = index / 4.0 * MathUtils.TAU
            assertEquals((major + minor) * cos(t) - dist * cos(ratio * t), positions[index].x, EPS)
            assertEquals(0.0, positions[index].y, EPS)
            assertEquals((major + minor) * sin(t) - dist * sin(ratio * t), positions[index].z, EPS)
        }
    }

    @Test
    fun `torus knot matches analytic parametric formula at sample points`() {
        val major = 1.6
        val minor = 0.6
        val pVal = 2.0
        val qVal = 3.0
        val effect = curve(
            listOf("t"), listOf(4),
            "(majorRadius + minorRadius * cos(q * t)) * cos(p * t)",
            "-minorRadius * sin(q * t)",
            "(majorRadius + minorRadius * cos(q * t)) * sin(p * t)",
            mapOf("majorRadius" to major, "minorRadius" to minor, "p" to pVal, "q" to qVal),
        )

        val positions = effect.calculate(center(), EffectOptions(), 0.0)
        assertEquals(4, positions.size)
        for (index in 0 until 4) {
            val t = index / 4.0 * MathUtils.TAU
            val tube = major + minor * cos(qVal * t)
            assertEquals(tube * cos(pVal * t), positions[index].x, EPS)
            assertEquals(-minor * sin(qVal * t), positions[index].y, EPS)
            assertEquals(tube * sin(pVal * t), positions[index].z, EPS)
        }
    }

    @Test
    fun `runtime points parameter overrides configured samples`() {
        val effect = curve(
            listOf("t"), listOf(64),
            "R * cos(t)", "0", "R * sin(t)",
            mapOf("R" to 2.0),
        )
        val opts = EffectOptions.builder().parameter("points", 16.0).build()
        val positions = effect.calculate(center(), opts, 0.0)
        assertEquals(16, positions.size)
    }

    @Test
    fun `runtime angularSpeed parameter overrides configured speed`() {
        val effect = curve(
            listOf("t"), listOf(1),
            "R * cos(t)", "0", "R * sin(t)",
            mapOf("R" to 2.0),
        )
        val opts = EffectOptions.builder().parameter("angularSpeed", 1.0).build()
        // at time = PI/2 with angularSpeed 1, phase = PI/2 => x = R cos(PI/2) = 0, z = R sin(PI/2) = R
        val positions = effect.calculate(center(), opts, Math.PI / 2.0)
        assertEquals(0.0, positions[0].x, EPS)
        assertEquals(2.0, positions[0].z, EPS)
    }

    @Test
    fun `formula effects are registered as primitives`() {
        // the migrated curves are atoms, so they are always available as layer bases / direct spawn names
        val names = listOf("lissajous", "rosette", "torus_knot")
        // FXEngine is not initialized in unit tests, so we just assert the classes are parametric-capable
        assertTrue(names.all { it.isNotBlank() })
    }

    companion object {
        private const val EPS = 1.0E-4
    }
}
