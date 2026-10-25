package com.utophii.effects

import com.utophii.api.EffectOptions
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Verifies gradient sampling and the per-particle payloads built from it.
class GradientColorsTest {

    private val black = Color.fromRGB(0, 0, 0)
    private val white = Color.fromRGB(255, 255, 255)
    private val red = Color.fromRGB(255, 0, 0)

    @Test
    fun `sample interpolates between evenly spaced stops`() {
        val stops = listOf(black, white, red)
        assertEquals(black, GradientColors.sample(stops, 0.0))
        assertEquals(white, GradientColors.sample(stops, 0.5))
        assertEquals(red, GradientColors.sample(stops, 1.0))
        assertEquals(Color.fromRGB(128, 128, 128), GradientColors.sample(stops, 0.25))
        assertEquals(red, GradientColors.sample(stops, 7.0), "t is clamped")
    }

    @Test
    fun `no gradient keeps the uniform color`() {
        val buffer = buffer(2)
        assertFalse(GradientColors().resolve(buffer, EffectOptions(particle = Particle.DUST)))
    }

    @Test
    fun `assigned values pick the dust color`() {
        val buffer = buffer(2)
        buffer.value(0, 1.0)
        buffer.value(1, 0.0)
        val colors = GradientColors()
        assertTrue(colors.resolve(buffer, EffectOptions(particle = Particle.DUST, gradient = listOf(black, white))))
        assertEquals(white, (colors.dataAt(0) as Particle.DustOptions).color)
        assertEquals(black, (colors.dataAt(1) as Particle.DustOptions).color)
        assertEquals(white.asARGB(), colors.argb()[0])
    }

    @Test
    fun `particles without values are colored along their index`() {
        val buffer = buffer(3)
        val colors = GradientColors()
        colors.resolve(buffer, EffectOptions(particle = Particle.DUST, gradient = listOf(black, white)))
        assertEquals(black, (colors.dataAt(0) as Particle.DustOptions).color)
        assertEquals(white, (colors.dataAt(2) as Particle.DustOptions).color)
    }

    @Test
    fun `payloads are reused across frames`() {
        val buffer = buffer(1)
        val colors = GradientColors()
        val opts = EffectOptions(particle = Particle.DUST, gradient = listOf(black, white))
        colors.resolve(buffer, opts)
        val first = colors.dataAt(0)
        buffer.acquire(1)
        colors.resolve(buffer, opts)
        assertSame(first, colors.dataAt(0))
    }

    @Test
    fun `uncolorable particles carry no payload`() {
        val colors = GradientColors()
        colors.resolve(buffer(1), EffectOptions(particle = Particle.FLAME, gradient = listOf(black, white)))
        assertFalse(colors.colorsParticles())
        assertNull(colors.dataAt(0))
    }

    private fun buffer(size: Int): ParticleBuffer = ParticleBuffer(Location(null, 0.0, 0.0, 0.0)).also { it.acquire(size) }
}
