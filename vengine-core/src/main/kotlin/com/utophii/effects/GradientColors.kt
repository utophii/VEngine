package com.utophii.effects

import com.utophii.api.EffectOptions
import org.bukkit.Color
import org.bukkit.Particle

// per-particle colors of one running effect, sampled from EffectOptions.gradient.
// The gradient is quantized into STEPS colors whose particle payloads are built once and reused,
// so a colored frame allocates nothing per particle
class GradientColors {
    private var key: Key? = null
    private val stepColors = IntArray(STEPS)
    private val stepData = arrayOfNulls<Any>(STEPS)

    // gradient step of every particle of the current frame
    private var steps = IntArray(0)
    private var argb = IntArray(0)
    private var count = 0

    // samples the frame
    fun resolve(buffer: ParticleBuffer, opts: EffectOptions): Boolean {
        if (opts.gradient.isEmpty()) {
            count = 0
            return false
        }
        rebuild(opts)
        count = buffer.list().size
        if (steps.size < count) {
            steps = IntArray(count)
            argb = IntArray(count)
        }
        val last = (count - 1).coerceAtLeast(1).toDouble()
        for (index in 0 until count) {
            // t = assigned value, or i / (n - 1) along the shape when the effect assigned none
            val value = buffer.valueAt(index).takeUnless(Double::isNaN) ?: (index / last)
            val step = (value.coerceIn(0.0, 1.0) * (STEPS - 1) + HALF).toInt()
            steps[index] = step
            argb[index] = stepColors[step]
        }
        return true
    }

    // particle payload of the particle at index, or null
    fun dataAt(index: Int): Any? = stepData[steps[index]]

    // true when the particle type can take per-particle colors
    fun colorsParticles(): Boolean = stepData[0] != null

    // ARGB colors of the resolved frame, for PIXEL displays
    fun argb(): IntArray = argb

    private fun rebuild(opts: EffectOptions) {
        val next = Key(opts.gradient, opts.particle, opts.dustSize, opts.toColor)
        if (next == key) {
            return
        }
        key = next
        for (step in 0 until STEPS) {
            val color = sample(opts.gradient, step / (STEPS - 1).toDouble())
            stepColors[step] = color.asARGB()
            stepData[step] = when (opts.particle) {
                Particle.DUST -> Particle.DustOptions(color, opts.dustSize)
                Particle.DUST_COLOR_TRANSITION -> Particle.DustTransition(color, opts.toColor ?: color, opts.dustSize)
                Particle.ENTITY_EFFECT -> color
                else -> null
            }
        }
    }

    private data class Key(val gradient: List<Color>, val particle: Particle, val dustSize: Float, val toColor: Color?)

    companion object {
        const val STEPS = 64
        private const val HALF = 0.5

        // piecewise linear color at t in 0..1 across evenly spaced stops
        fun sample(stops: List<Color>, t: Double): Color {
            if (stops.size == 1) {
                return stops[0]
            }
            // position = t * (n - 1): the segment index is its integer part, the blend weight its fraction
            val position = t.coerceIn(0.0, 1.0) * (stops.size - 1)
            val segment = position.toInt().coerceAtMost(stops.size - 2)
            val weight = position - segment
            val from = stops[segment]
            val to = stops[segment + 1]
            return Color.fromRGB(
                lerp(from.red, to.red, weight),
                lerp(from.green, to.green, weight),
                lerp(from.blue, to.blue, weight),
            )
        }

        private fun lerp(a: Int, b: Int, t: Double): Int = (a + (b - a) * t + HALF).toInt().coerceIn(0, 255)
    }
}
