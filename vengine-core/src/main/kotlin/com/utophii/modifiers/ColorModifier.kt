package com.utophii.modifiers

import com.utophii.api.EffectModifier
import org.bukkit.Color
import org.bukkit.Location

// time-aware color helper that also satisfies the modifier pipeline
class ColorModifier(
    private val from: Color,
    private val to: Color,
    private val periodTicks: Double = DEFAULT_PERIOD_TICKS,
) : EffectModifier {
    // leaves position unchanged; color sampling is exposed through [colorAt]
    override fun modify(loc: Location, time: Double): Location = loc

    // interpolates between two colors over time
    // channel = a + (b - a) * t
    fun colorAt(time: Double): Color {
        // t = (sin(time / period * tau) + 1) / 2: oscillates interpolation weight between 0 and 1
        val t = (kotlin.math.sin(time / periodTicks * Math.PI * 2.0) + 1.0) * 0.5
        return Color.fromRGB(
            lerp(from.red, to.red, t),
            lerp(from.green, to.green, t),
            lerp(from.blue, to.blue, t),
        )
    }

    private fun lerp(a: Int, b: Int, t: Double): Int {
        return (a + (b - a) * t).toInt().coerceIn(MIN_COLOR_CHANNEL, MAX_COLOR_CHANNEL)
    }

    companion object {
        const val DEFAULT_PERIOD_TICKS = 40.0
        private const val MIN_COLOR_CHANNEL = 0
        private const val MAX_COLOR_CHANNEL = 255
    }
}
