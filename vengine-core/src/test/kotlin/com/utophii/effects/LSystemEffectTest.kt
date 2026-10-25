package com.utophii.effects

import com.utophii.api.EffectOptions
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class LSystemEffectTest {

    private fun center(): Location = Location(null, 0.0, 0.0, 0.0)

    private fun lsystem(
        axiom: String,
        rules: Map<Char, String>,
        iterations: Int,
        angle: Double = 90.0,
        growth: Double = 0.0,
        anchorCenter: Boolean = false,
        points: Int = 4096,
        stochastic: Map<Char, List<LSystemEffect.Production>> = emptyMap(),
        seed: Long? = null,
    ) = LSystemEffect(
        name = "lsystem",
        axiom = axiom,
        rules = rules.mapValues { (_, successor) -> listOf(LSystemEffect.Production(successor)) } + stochastic,
        iterations = iterations,
        angleDegrees = angle,
        size = 2.0,
        spacing = 0.05,
        maxPoints = points,
        growthTicks = growth,
        anchorCenter = anchorCenter,
        angularSpeed = 0.0,
        seed = seed,
    )

    private fun frame(effect: LSystemEffect, time: Double = 0.0, parameters: Map<String, Double> = emptyMap()): ParticleBuffer {
        val buffer = ParticleBuffer(center())
        effect.calculateInto(buffer, center(), EffectOptions(parameters = parameters), time)
        return buffer
    }

    @Test
    fun `koch curve is planar and fitted to the size`() {
        val buffer = frame(lsystem("F", mapOf('F' to "F+F--F+F"), iterations = 2, angle = 60.0, anchorCenter = true))
        val points = buffer.list()
        assertTrue(points.isNotEmpty())
        points.forEach { assertEquals(0.0, it.z, 1e-9) }
        val width = points.maxOf { it.x } - points.minOf { it.x }
        val height = points.maxOf { it.y } - points.minOf { it.y }
        assertEquals(2.0, maxOf(width, height), 0.06)
    }

    @Test
    fun `base anchor puts the root at the origin`() {
        val buffer = frame(lsystem("FF", emptyMap(), iterations = 0))
        val points = buffer.list()
        assertTrue(points.all { abs(it.x) < 1e-9 && it.y >= 0.0 })
        assertEquals(2.0, points.maxOf { it.y }, 1e-9)
    }

    @Test
    fun `branches measure their distance from the root`() {
        // trunk of 1, a side branch of 1 starting at height 1, and a trunk continuation of 1: the deepest point is 2 away
        val buffer = frame(lsystem("F[+F]F", emptyMap(), iterations = 0))
        val values = buffer.list().indices.map(buffer::valueAt)
        assertEquals(1.0, values.max(), 1e-9)
        val tips = buffer.list().filterIndexed { index, _ -> values[index] > 1.0 - 1e-9 }
        assertEquals(2, tips.size, "both the branch tip and the trunk top are at the full root distance")
    }

    @Test
    fun `growth reveals the plant from the root outwards`() {
        val effect = lsystem("F[+F]F", emptyMap(), iterations = 0, growth = 100.0)
        val half = frame(effect, time = 50.0)
        val full = frame(effect, time = 100.0)
        // the same number of particles every tick, nearest to the root first
        assertEquals(full.list().size / 2, half.list().size)
        val shown = half.list().indices.map(half::valueAt)
        assertEquals(shown.sorted(), shown)
        assertTrue(shown.max() < 1.0)
        assertEquals(full.list().size, frame(effect, time = 0.0, parameters = mapOf("progress" to 1.0)).list().size)
    }

    @Test
    fun `pitch leaves the plane`() {
        val buffer = frame(lsystem("F&F", emptyMap(), iterations = 0))
        assertTrue(buffer.list().any { abs(it.z) > 0.1 })
    }

    @Test
    fun `point cap thins the samples`() {
        val buffer = frame(lsystem("F", mapOf('F' to "F+F--F+F"), iterations = 4, angle = 60.0, points = 100))
        assertTrue(buffer.list().size <= 100)
    }

    @Test
    fun `rewriting stops before the string explodes`() {
        val effect = lsystem("F", mapOf('F' to "FFFFFFFFFF"), iterations = 10, points = 10)
        assertTrue(effect.appliedIterations < 10)
        assertEquals(6, effect.appliedIterations, "10^6 symbols still fit, 10^7 do not")
    }

    @Test
    fun `stochastic rules grow a different plant per seed`() {
        val branch = mapOf('F' to listOf(LSystemEffect.Production("F[+F]F"), LSystemEffect.Production("F[-F]F")))
        val effect = lsystem("F", emptyMap(), iterations = 4, angle = 25.0, stochastic = branch)
        fun shape(seed: Double) = frame(effect, parameters = mapOf("seed" to seed)).list().map { it.x to it.y }
        assertEquals(shape(1.0), shape(1.0))
        assertTrue(shape(1.0) != shape(2.0))
    }

    @Test
    fun `productions are picked in proportion to their weights`() {
        // 1000 symbols, each drawing a segment (F) with weight 3 or moving without drawing (f) with weight 1;
        // the spacing is far coarser than a segment, so every drawn segment is one particle
        val coin = mapOf('A' to listOf(LSystemEffect.Production("F", 3.0), LSystemEffect.Production("f", 1.0)))
        val effect = lsystem("A".repeat(1000), emptyMap(), iterations = 1, stochastic = coin, seed = 7)
        val drawn = frame(effect).list().size
        // expected 750, standard deviation √(1000 · 0.75 · 0.25) ≈ 14
        assertTrue(drawn in 690..810, "drew $drawn segments")
    }

    @Test
    fun `every play rolls a seed unless one is fixed`() {
        val branch = mapOf('F' to listOf(LSystemEffect.Production("F[+F]F"), LSystemEffect.Production("F[-F]F")))
        val random = lsystem("F", emptyMap(), iterations = 2, stochastic = branch)
        assertTrue("seed" in random.playParameters(emptyMap()))
        assertTrue(random.playParameters(mapOf("seed" to 5.0)).isEmpty(), "a seed set by the layer wins")
        assertTrue(lsystem("F", emptyMap(), iterations = 2, stochastic = branch, seed = 3).playParameters(emptyMap()).isEmpty())
        assertTrue(lsystem("F", mapOf('F' to "F+F"), iterations = 2).playParameters(emptyMap()).isEmpty())
    }
}
