package com.utophii.effects

import com.utophii.api.ColorInterpolation
import com.utophii.api.DisplayOptions
import com.utophii.api.EffectConfig
import com.utophii.api.EffectOptions
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LifetimeAppearanceTest {
    private fun buffer(count: Int = 1) = ParticleBuffer(Location(null, 0.0, 0.0, 0.0)).also { it.acquire(count) }

    @Test
    fun `HSV stays saturated and crosses the hue seam in both directions`() {
        val colors = listOf(Color.RED, Color.BLUE)
        assertEquals(Color.fromRGB(128, 0, 128), GradientColors.sample(colors, 0.5))
        assertEquals(Color.FUCHSIA, GradientColors.sample(colors, 0.5, ColorInterpolation.HSV))
        assertEquals(Color.FUCHSIA, GradientColors.sample(colors.reversed(), 0.5, ColorInterpolation.HSV))
        val seam = listOf(Color.fromRGB(255, 0, 43), Color.fromRGB(255, 43, 0))
        assertEquals(Color.RED, GradientColors.sample(seam, 0.5, ColorInterpolation.HSV))
    }

    @Test
    fun `HSV borrows hue for gray and black endpoints`() {
        for (neutral in listOf(Color.BLACK, Color.WHITE)) {
            val forward = GradientColors.sample(listOf(neutral, Color.BLUE), 0.5, ColorInterpolation.HSV)
            val backward = GradientColors.sample(listOf(Color.BLUE, neutral), 0.5, ColorInterpolation.HSV)
            assertEquals(forward, backward)
            assertEquals(forward.red, forward.green)
            assertTrue(forward.blue > forward.red)
        }
        assertEquals(Color.GRAY, GradientColors.sample(listOf(Color.GRAY), 0.7, ColorInterpolation.HSV))
    }

    @Test
    fun `individual age overrides layer age without consuming the spatial value`() {
        val buffer = buffer(3)
        buffer.age(0, 1.0)
        buffer.age(1, 0.0)
        buffer.value(0, 0.0)
        buffer.color(0, Color.LIME.asRGB())
        val opts = EffectOptions(particle = Particle.DUST, duration = 11,
            gradient = listOf(Color.BLACK, Color.WHITE), colorOverLife = listOf(Color.RED, Color.BLUE),
            sizeOverLife = listOf(1f, 0f), dustSize = 2f)
        val colors = GradientColors()
        colors.resolve(buffer, opts, time = 10.0)
        assertEquals(Color.BLUE, (colors.dataAt(0) as Particle.DustOptions).color)
        assertEquals(Color.RED, (colors.dataAt(1) as Particle.DustOptions).color)
        assertEquals(2f, (colors.dataAt(1) as Particle.DustOptions).size)
        assertFalse(colors.visibleAt(0))
        assertTrue(colors.visibleAt(1))
        assertFalse(colors.visibleAt(2), "unassigned age uses the last frame of the layer")
        assertEquals(0.0, buffer.valueAt(0))
    }

    @Test
    fun `size only retains dust defaults and transition target`() {
        for (type in listOf(Particle.DUST, Particle.DUST_COLOR_TRANSITION)) {
            val colors = GradientColors()
            colors.resolve(buffer(), EffectOptions(particle = type, dustSize = 2f, sizeOverLife = listOf(0.5f)))
            val payload = colors.dataAt(0) as Particle.DustOptions
            assertEquals(Color.RED, payload.color)
            assertEquals(1f, payload.size)
            if (payload is Particle.DustTransition) assertEquals(Color.BLUE, payload.toColor)
        }
    }

    @Test
    fun `size combines with spatial colors and reuses payloads`() {
        val buffer = buffer(2)
        buffer.value(0, 1.0)
        buffer.value(1, 0.0)
        buffer.age(0, 0.0)
        buffer.age(1, 1.0)
        val opts = EffectOptions(particle = Particle.DUST, gradient = listOf(Color.RED, Color.BLUE),
            sizeOverLife = listOf(1f, 0.25f))
        val colors = GradientColors()
        colors.resolve(buffer, opts)
        val first = colors.dataAt(0) as Particle.DustOptions
        assertEquals(Color.BLUE, first.color)
        assertEquals(1f, first.size)
        val second = colors.dataAt(1) as Particle.DustOptions
        assertEquals(Color.RED, second.color)
        assertEquals(0.25f, second.size)
        colors.resolve(buffer, opts)
        assertSame(first, colors.dataAt(0))
        assertSame(second, colors.dataAt(1))
        colors.resolve(buffer, opts.copy(sizeOverLife = listOf(0.5f)))
        assertEquals(0.5f, (colors.dataAt(0) as Particle.DustOptions).size)
    }

    @Test
    fun `interpolation change invalidates both gradient caches`() {
        val colors = GradientColors()
        val buffer = buffer()
        buffer.value(0, 0.5)
        buffer.age(0, 0.5)
        for (lifetime in listOf(false, true)) {
            val stops = listOf(Color.RED, Color.BLUE)
            val opts = EffectOptions(particle = Particle.DUST,
                gradient = if (lifetime) emptyList() else stops,
                colorOverLife = if (lifetime) stops else emptyList())
            colors.resolve(buffer, opts)
            val rgb = (colors.dataAt(0) as Particle.DustOptions).color
            colors.resolve(buffer, opts.copy(gradientInterpolation = ColorInterpolation.HSV))
            val hsv = (colors.dataAt(0) as Particle.DustOptions).color
            assertTrue(hsv.red > rgb.red && hsv.blue > rgb.blue)
        }
    }

    @Test
    fun `age resets on acquire release and append even when the buffer grows`() {
        val buffer = buffer(32)
        buffer.age(0, 2.0)
        buffer.age(31, -1.0)
        buffer.add(0.0, 0.0, 0.0)
        assertEquals(1.0, buffer.ageAt(0))
        assertEquals(0.0, buffer.ageAt(31))
        assertTrue(buffer.ageAt(32).isNaN())
        buffer.age(32, 0.7)
        buffer.acquire(33)
        assertTrue(buffer.ageAt(32).isNaN())
        buffer.age(1, 0.5)
        assertTrue(buffer.ageAt(0).isNaN())
        buffer.release()
        buffer.add(0.0, 0.0, 0.0)
        assertTrue(buffer.ageAt(0).isNaN())
        assertThrows(IllegalArgumentException::class.java) { buffer.age(0, Double.NaN) }
    }

    @Test
    fun `layer age reaches endpoints and handles short infinite and clamped durations`() {
        assertEquals(0.0, GradientColors.normalizedAge(0.0, 11))
        assertEquals(0.5, GradientColors.normalizedAge(5.0, 11))
        assertEquals(1.0, GradientColors.normalizedAge(10.0, 11))
        assertEquals(0.0, GradientColors.normalizedAge(100.0, 1))
        assertEquals(0.0, GradientColors.normalizedAge(100.0, EffectOptions.INFINITE_DURATION))
        val colors = GradientColors()
        val opts = EffectOptions(colorOverLife = listOf(Color.RED, Color.BLUE), duration = 100)
        colors.resolve(buffer(), opts, time = 9.0, duration = 10)
        assertEquals(Color.BLUE.asARGB(), colors.argb()[0], "actual layer duration wins over configured duration")
    }

    @Test
    fun `piecewise size curve supports growth and fade and rejects invalid stops`() {
        val stops = listOf(0f, 2f, 0f)
        assertEquals(1f, GradientColors.sampleSize(stops, 0.25))
        assertEquals(2f, GradientColors.sampleSize(stops, 0.5))
        assertEquals(1f, GradientColors.sampleSize(stops, 0.75))
        assertEquals(0f, GradientColors.sampleSize(stops, 2.0))
        for (invalid in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { EffectOptions(sizeOverLife = listOf(invalid)) }
        }
    }

    @Test
    fun `DSL builder and layer merging preserve lifetime settings`() {
        val opts = EffectConfig().apply {
            gradientInterpolation(ColorInterpolation.HSV)
            colorOverLife(Color.RED, Color.BLUE)
            sizeOverLife(1f, 0f)
        }.build()
        assertEquals(opts, opts.toBuilder().build())
        val layer = ScriptedLayer(effect = "sphere", startTick = 0, durationTicks = null, options = opts)
        assertEquals(opts, layer.resolveOptions(EffectOptions(), 0.0))
        val overrides = opts.copy(colorOverLife = listOf(Color.LIME), sizeOverLife = listOf(0.5f))
        assertEquals(overrides, layer.resolveOptions(overrides, 0.0))
    }

    @Test
    fun `display size multiplier shrinks models around their center`() {
        for (type in DisplayOptions.Type.entries) {
            val display = DisplayOptions(type = type, size = Vector(2.0, 4.0, 6.0))
            val full = DisplayEntityPool.modelTransformation(display, EffectOptions(), 0, 0.0)
            val half = DisplayEntityPool.modelTransformation(display, EffectOptions(), 0, 0.0, sizeMultiplier = 0.5f)
            assertEquals(full.scale.x * 0.5f, half.scale.x)
            assertEquals(full.scale.y * 0.5f, half.scale.y)
            assertEquals(full.translation.x * 0.5f, half.translation.x)
            assertEquals(full.translation.y * 0.5f, half.translation.y)
            val hidden = DisplayEntityPool.modelTransformation(display, EffectOptions(), 0, 0.0, sizeMultiplier = 0f)
            assertEquals(0f, hidden.scale.x)
            assertEquals(0f, hidden.scale.y)
        }
    }
}
