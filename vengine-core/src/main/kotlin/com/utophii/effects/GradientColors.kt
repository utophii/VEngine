package com.utophii.effects

import com.utophii.api.EffectOptions
import org.bukkit.Color
import org.bukkit.Particle

// per-particle colors of one running effect: the effect's own RGB colors, or samples of EffectOptions.gradient.
// The gradient is quantized into STEPS colors and own colors into PALETTE_BITS bits per channel, whose particle
// payloads are built once and reused, so a colored frame allocates nothing per particle
class GradientColors {
    private var key: Key? = null
    private val stepColors = IntArray(STEPS)
    private val stepData = arrayOfNulls<Any>(STEPS)

    // payloads of quantized own colors, allocated on the first frame with own colors
    private var paletteData: Array<Any?>? = null

    // payload and ARGB color of every particle of the current frame
    private var data = arrayOfNulls<Any>(0)
    private var argb = IntArray(0)
    private var colorsParticles = false

    // samples the frame
    fun resolve(buffer: ParticleBuffer, opts: EffectOptions): Boolean {
        val own = buffer.hasColors()
        if (opts.gradient.isEmpty() && !own) {
            return false
        }
        rebuild(opts)
        val count = buffer.list().size
        if (data.size < count) {
            data = arrayOfNulls(count)
            argb = IntArray(count)
        }
        // particles without an own color and without a gradient keep the layer color
        val fallbackData = if (opts.gradient.isEmpty()) AbstractParticleEffect.resolveParticleData(opts) else null
        val fallbackArgb = (opts.color ?: Color.WHITE).asARGB()
        val last = (count - 1).coerceAtLeast(1).toDouble()
        for (index in 0 until count) {
            val rgb = if (own) buffer.colorAt(index) else ParticleBuffer.NO_COLOR
            when {
                rgb != ParticleBuffer.NO_COLOR -> {
                    argb[index] = OPAQUE or rgb
                    data[index] = paletteEntry(rgb, opts)
                }
                opts.gradient.isEmpty() -> {
                    argb[index] = fallbackArgb
                    data[index] = fallbackData
                }
                else -> {
                    // t = assigned value, or i / (n - 1) along the shape when the effect assigned none
                    val value = buffer.valueAt(index).takeUnless(Double::isNaN) ?: (index / last)
                    val step = (value.coerceIn(0.0, 1.0) * (STEPS - 1) + HALF).toInt()
                    argb[index] = stepColors[step]
                    data[index] = stepData[step]
                }
            }
        }
        return true
    }

    // particle payload of the particle at index, or null
    fun dataAt(index: Int): Any? = data[index]

    // true when the particle type can take per-particle colors
    fun colorsParticles(): Boolean = colorsParticles

    // ARGB colors of the resolved frame, for PIXEL displays
    fun argb(): IntArray = argb

    private fun rebuild(opts: EffectOptions) {
        val next = Key(opts.gradient, opts.particle, opts.dustSize, opts.toColor)
        if (next == key) {
            return
        }
        key = next
        paletteData?.fill(null)
        colorsParticles = payload(Color.WHITE, opts) != null
        if (opts.gradient.isEmpty()) {
            return
        }
        for (step in 0 until STEPS) {
            val color = sample(opts.gradient, step / (STEPS - 1).toDouble())
            stepColors[step] = color.asARGB()
            stepData[step] = payload(color, opts)
        }
    }

    // payload of an own color, rounded to PALETTE_BITS per channel
    private fun paletteEntry(rgb: Int, opts: EffectOptions): Any? {
        val palette = paletteData ?: arrayOfNulls<Any>(1 shl (PALETTE_BITS * 3)).also { paletteData = it }
        val shift = 8 - PALETTE_BITS
        val r = (rgb shr 16 and 0xFF) shr shift
        val g = (rgb shr 8 and 0xFF) shr shift
        val b = (rgb and 0xFF) shr shift
        val slot = (r shl (PALETTE_BITS * 2)) or (g shl PALETTE_BITS) or b
        palette[slot]?.let { return it }
        // the slot's color is the middle of its quantization cell
        val half = (1 shl shift) / 2
        val color = Color.fromRGB((r shl shift) + half, (g shl shift) + half, (b shl shift) + half)
        return payload(color, opts).also { palette[slot] = it }
    }

    private fun payload(color: Color, opts: EffectOptions): Any? = when (opts.particle) {
        Particle.DUST -> Particle.DustOptions(color, opts.dustSize)
        Particle.DUST_COLOR_TRANSITION -> Particle.DustTransition(color, opts.toColor ?: color, opts.dustSize)
        Particle.ENTITY_EFFECT -> color
        else -> null
    }

    private data class Key(val gradient: List<Color>, val particle: Particle, val dustSize: Float, val toColor: Color?)

    companion object {
        const val STEPS = 64
        private const val HALF = 0.5

        // 5 bits per channel: 32768 colors, enough to hide banding on photos
        private const val PALETTE_BITS = 5
        private const val OPAQUE = 0xFF shl 24

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
