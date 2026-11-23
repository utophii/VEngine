package com.utophii.modifiers

import com.utophii.api.EffectOptions
import com.utophii.effects.PointEffect
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlockingModifierTest {
    private val center = Location(null, 5.0, 0.0, 7.0)
    private val pair = PointEffect("pair", "2", mapOf("x" to "index", "y" to "0", "z" to "0"))

    private fun positions(modifier: FlockingModifier, time: Double) =
        pair.calculate(center, EffectOptions(modifiers = listOf(modifier)), time)

    @Test
    fun `neighbours steer apart together or toward a common velocity`() {
        fun rule(separation: Double = 0.0, alignment: Double = 0.0, cohesion: Double = 0.0, radius: Double = 2.0) =
            FlockingModifier(radius, separation, alignment, cohesion, maxSpeed = 1.0)
        val start = positions(rule(), 0.0)
        val free = positions(rule(), 1.0)
        val apart = positions(rule(separation = 0.1), 1.0)
        val together = positions(rule(cohesion = 0.1), 1.0)
        assertTrue(apart.first().x < free.first().x && apart.last().x > free.last().x)
        assertTrue(together.first().x > free.first().x && together.last().x < free.last().x)
        val aligned = positions(rule(alignment = 0.5), 1.0)
        val firstStep = aligned.first().toVector().subtract(start.first().toVector())
        val lastStep = aligned.last().toVector().subtract(start.last().toVector())
        assertEquals(0.0, firstStep.distance(lastStep), 1e-10)
        assertEquals(free, positions(rule(2.0, 2.0, 2.0, radius = 0.5), 1.0))
    }

    @Test
    fun `flock previews restart independently and obey speed limit`() {
        val modifier = FlockingModifier(cohesion = 10.0, maxSpeed = 0.1)
        val later = positions(modifier, 5.5)
        val earlier = positions(modifier, 5.0)
        assertEquals(later, positions(modifier, 5.5))
        assertTrue(later.indices.all { later[it].toVector().distance(earlier[it].toVector()) <= 0.05000001 })
    }
}
