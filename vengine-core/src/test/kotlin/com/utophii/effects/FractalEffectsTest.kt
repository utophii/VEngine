package com.utophii.effects

import com.utophii.api.EffectOptions
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

// Verifies the fractal primitives against sets whose geometry is known in closed form.
class FractalEffectsTest {

    private fun center(): Location = Location(null, 0.0, 0.0, 0.0)

    private fun frame(effect: AbstractParticleEffect, parameters: Map<String, Double>, time: Double = 0.0): ParticleBuffer {
        val buffer = ParticleBuffer(center())
        effect.calculateInto(buffer, center(), EffectOptions(parameters = parameters), time)
        return buffer
    }

    @Test
    fun `julia set of c = 0 is the unit circle`() {
        val buffer = frame(JuliaEffect(), mapOf("cRe" to 0.0, "cIm" to 0.0, "points" to 200.0, "radius" to 2.0))
        assertEquals(200, buffer.list().size)
        buffer.list().forEach { point ->
            assertEquals(1.0, sqrt(point.x * point.x + point.y * point.y), EPS)
            assertEquals(0.0, point.z, EPS)
        }
    }

    @Test
    fun `julia set of c = -2 is the real segment`() {
        val buffer = frame(JuliaEffect(), mapOf("cRe" to -2.0, "cIm" to 0.0, "points" to 200.0, "radius" to 2.0))
        buffer.list().forEach { point ->
            assertEquals(0.0, point.y, LOOSE_EPS)
            assertTrue(abs(point.x) <= 2.0 + EPS)
        }
    }

    @Test
    fun `julia points stay in the bounding disc and carry gradient values`() {
        val buffer = frame(JuliaEffect(), mapOf("radius" to 2.0, "cOrbit" to 0.1), time = 17.0)
        assertTrue(buffer.hasValues())
        buffer.list().forEachIndexed { index, point ->
            assertTrue(sqrt(point.x * point.x + point.y * point.y) <= 2.0)
            assertTrue(buffer.valueAt(index) in 0.0..1.0)
        }
    }

    @Test
    fun `julia particles keep their place between frames for a fixed c`() {
        val effect = JuliaEffect()
        val first = frame(effect, emptyMap(), time = 0.0).list().map { it.clone() }
        val second = frame(effect, emptyMap(), time = 40.0).list()
        first.indices.forEach { index -> assertEquals(first[index].x, second[index].x, EPS) }
    }

    @Test
    fun `sierpinski triangle leaves the middle triangle empty`() {
        val buffer = frame(SierpinskiEffect.triangle(), mapOf("radius" to 1.0, "angularSpeed" to 0.0))
        // the removed middle triangle has its vertices at the edge midpoints: apex down, top edge at y = 0.25
        buffer.list().forEach { point ->
            val insideMiddle = point.y < 0.25 - EPS && point.y > -0.5 + EPS &&
                abs(point.x) < (point.y + 0.5) / sqrt(3.0) - EPS
            assertTrue(!insideMiddle, "point $point lies in the removed middle triangle")
            assertTrue(sqrt(point.x * point.x + point.y * point.y) <= 1.0 + EPS)
        }
    }

    @Test
    fun `sierpinski carpet covers its eight cells evenly and skips the center`() {
        val buffer = frame(SierpinskiEffect.carpet(), mapOf("radius" to 1.0, "points" to 64.0, "angularSpeed" to 0.0))
        val half = 1.0 / sqrt(2.0)
        val counts = IntArray(9)
        buffer.list().forEach { point ->
            val column = ((point.x / half + 1.0) * 1.5).toInt().coerceIn(0, 2)
            val row = ((point.y / half + 1.0) * 1.5).toInt().coerceIn(0, 2)
            counts[row * 3 + column]++
        }
        assertEquals(0, counts[4], "center cell")
        counts.forEachIndexed { cell, count -> if (cell != 4) assertEquals(8, count, "cell $cell") }
    }

    @Test
    fun `menger sponge lies on the cube faces outside the tunnels`() {
        val buffer = frame(SierpinskiEffect.mengerSponge(), mapOf("radius" to 1.0, "angularSpeed" to 0.0))
        val half = 1.0 / sqrt(3.0)
        buffer.list().forEachIndexed { index, point ->
            val coordinates = listOf(point.x, point.y, point.z)
            assertTrue(coordinates.any { abs(abs(it) - half) < EPS }, "point $point is off the cube surface")
            val middle = coordinates.count { abs(it) < half / 3.0 - EPS }
            assertTrue(middle < 2, "point $point lies on a tunnel opening")
            assertTrue(buffer.valueAt(index) in 0.0..1.0)
        }
    }

    @Test
    fun `chladni grains start spread out and settle on the nodal lines`() {
        val effect = ChladniEffect()
        val parameters = mapOf("size" to 1.0, "jitter" to 0.0, "settle" to 20.0)
        val start = frame(effect, parameters, time = 0.0)
        val settled = frame(effect, parameters, time = 20.0)
        // f(u, v) of the default mode (n = 3, m = 5, mix = -1)
        fun plate(u: Double, v: Double): Double {
            val x = (u + 1.0) / 2.0 * Math.PI
            val y = (v + 1.0) / 2.0 * Math.PI
            return kotlin.math.cos(3 * x) * kotlin.math.cos(5 * y) - kotlin.math.cos(5 * x) * kotlin.math.cos(3 * y)
        }
        val startOnLines = start.list().count { abs(plate(it.x, it.z)) < 1e-3 }
        val settledOnLines = settled.list().count { abs(plate(it.x, it.z)) < 1e-3 }
        assertTrue(startOnLines < settled.list().size / 10, "grains must start off the lines")
        assertTrue(settledOnLines > settled.list().size * 95 / 100, "only $settledOnLines grains settled")
        settled.list().forEach { point ->
            assertEquals(0.0, point.y, EPS)
            assertTrue(abs(point.x) <= 1.0 && abs(point.z) <= 1.0)
        }
    }

    companion object {
        private const val EPS = 1e-9
        private const val LOOSE_EPS = 1e-6
    }
}
