package com.utophii.effects

import com.utophii.api.ColorInterpolation
import com.utophii.api.EffectOptions
import org.bukkit.Color
import org.bukkit.Particle

// per-particle spatial/lifetime appearance of one running effect. Gradients and ages use STEPS samples.
// Gradient payloads are reused without per-particle allocations after warmup. Own colors use
// PALETTE_BITS per channel. Combining them with size uses a bounded cache.
class GradientColors {
    private var key: Key? = null
    private val stepColors = IntArray(STEPS)
    private val stepData = arrayOfNulls<Any>(STEPS)

    private val lifeColors = IntArray(STEPS)
    private val lifeData = arrayOfNulls<Any>(STEPS)
    private var gradientSizeData: Array<Any?>? = null
    private val lifeSizes = FloatArray(STEPS)
    private var sizedCache: SizedCache? = null
    private var sizes = FloatArray(0)

    // payloads of quantized own colors, allocated on the first frame with own colors
    private var paletteData: Array<Any?>? = null

    // payload and ARGB color of every particle of the current frame
    private var data = arrayOfNulls<Any>(0)
    private var argb = IntArray(0)
    private var colorsParticles = false

    // samples the frame
    fun resolve(buffer: ParticleBuffer, opts: EffectOptions, time: Double = 0.0, duration: Long = opts.duration): Boolean {
        val own = buffer.hasColors()
        if (opts.gradient.isEmpty() && opts.colorOverLife.isEmpty() && opts.sizeOverLife.isEmpty() && !own) {
            return false
        }
        rebuild(opts)
        colorsParticles = isDust(opts) || (opts.particle == Particle.ENTITY_EFFECT &&
            (own || opts.gradient.isNotEmpty() || opts.colorOverLife.isNotEmpty()))
        val count = buffer.list().size
        if (data.size < count) {
            data = arrayOfNulls(count)
            argb = IntArray(count)
            sizes = FloatArray(count)
        }
        // particles without an own color and without a gradient keep the layer color
        val fallbackData = if (opts.gradient.isEmpty()) {
            if (opts.particle == Particle.ENTITY_EFFECT) opts.color ?: Color.WHITE
            else AbstractParticleEffect.resolveParticleData(opts)
        } else null
        val fallbackArgb = (opts.color ?: Color.WHITE).asARGB()
        val defaultAge = normalizedAge(time, duration)
        val last = (count - 1).coerceAtLeast(1).toDouble()
        for (index in 0 until count) {
            val rgb = if (own) buffer.colorAt(index) else ParticleBuffer.NO_COLOR
            val age = buffer.ageAt(index).takeUnless(Double::isNaN) ?: defaultAge
            val ageStep = step(age)
            sizes[index] = lifeSizes[ageStep]
            when {
                opts.colorOverLife.isNotEmpty() -> {
                    argb[index] = lifeColors[ageStep]
                    data[index] = lifeData[ageStep]
                }
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
                    val step = step(value)
                    argb[index] = stepColors[step]
                    data[index] = if (opts.sizeOverLife.isNotEmpty() && isDust(opts)) {
                        gradientSizeEntry(step, ageStep, opts)
                    } else stepData[step]
                }
            }
            if (opts.sizeOverLife.isNotEmpty() && opts.colorOverLife.isEmpty() && isDust(opts) &&
                (rgb != ParticleBuffer.NO_COLOR || opts.gradient.isEmpty())
            ) {
                // Use the base payload's color to preserve own-color quantization and dust defaults.
                val color = (data[index] as? Particle.DustOptions)?.color
                    ?: opts.color ?: Color.RED
                data[index] = sizedEntry(color.asARGB(), ageStep, opts,
                    defaultTransition = rgb == ParticleBuffer.NO_COLOR && opts.gradient.isEmpty())
            }
        }
        return true
    }

    fun sizes(): FloatArray = sizes

    fun visibleAt(index: Int): Boolean = sizes[index] > 0f

    // particle payload of the particle at index, or null
    fun dataAt(index: Int): Any? = data[index]

    // true when the particle type can take per-particle colors
    fun colorsParticles(): Boolean = colorsParticles

    // ARGB colors of the resolved frame, for PIXEL displays
    fun argb(): IntArray = argb

    private fun rebuild(opts: EffectOptions) {
        val next = Key(opts.gradient, opts.particle, opts.dustSize, opts.toColor, opts.gradientInterpolation, opts.colorOverLife, opts.sizeOverLife)
        if (next == key) {
            return
        }
        key = next
        paletteData?.fill(null)
        sizedCache?.clear()
        gradientSizeData?.fill(null)
        for (step in 0 until STEPS) {
            val age = step / (STEPS - 1).toDouble()
            lifeSizes[step] = sampleSize(opts.sizeOverLife, age)
            if (opts.colorOverLife.isNotEmpty()) {
                val color = sample(opts.colorOverLife, age, opts.gradientInterpolation)
                lifeColors[step] = color.asARGB()
                lifeData[step] = payload(color, opts, dustSize(opts, step))
            }
        }
        if (opts.gradient.isEmpty()) {
            return
        }
        for (step in 0 until STEPS) {
            val color = sample(opts.gradient, step / (STEPS - 1).toDouble(), opts.gradientInterpolation)
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

    private fun gradientSizeEntry(step: Int, ageStep: Int, opts: EffectOptions): Any? {
        val entries = gradientSizeData ?: arrayOfNulls<Any>(STEPS * STEPS).also { gradientSizeData = it }
        val slot = step * STEPS + ageStep
        return entries[slot] ?: payload(Color.fromARGB(stepColors[step]), opts, dustSize(opts, ageStep))
            .also { entries[slot] = it }
    }

    private class SizedCache {
        val keys = LongArray(SIZED_CACHE_SIZE) { -1L }
        val data = arrayOfNulls<Any>(SIZED_CACHE_SIZE)

        fun clear() {
            keys.fill(-1L)
            data.fill(null)
        }
    }

    private fun sizedEntry(argb: Int, ageStep: Int, opts: EffectOptions, defaultTransition: Boolean = false): Any? {
        val sizeStep = if (opts.sizeOverLife.isEmpty()) 0 else ageStep
        val key = ((argb.toLong() and 0xFFFFFF) shl 6) or sizeStep.toLong() or (if (defaultTransition) 1L shl 30 else 0L)
        val slot = ((key xor (key ushr 12) xor (key ushr 24)) and (SIZED_CACHE_SIZE - 1).toLong()).toInt()
        val cache = sizedCache ?: SizedCache().also { sizedCache = it }
        if (cache.keys[slot] == key) return cache.data[slot]
        val size = dustSize(opts, ageStep)
        val color = Color.fromARGB(argb)
        val result = if (defaultTransition && opts.particle == Particle.DUST_COLOR_TRANSITION) {
            Particle.DustTransition(color, opts.toColor ?: Color.BLUE, size)
        } else payload(color, opts, size)
        cache.keys[slot] = key
        cache.data[slot] = result
        return result
    }

    private fun dustSize(opts: EffectOptions, ageStep: Int): Float =
        if (opts.sizeOverLife.isEmpty()) opts.dustSize
        else (opts.dustSize.toDouble() * lifeSizes[ageStep]).coerceIn(0.01, 4.0).toFloat()

    private fun isDust(opts: EffectOptions): Boolean =
        opts.particle == Particle.DUST || opts.particle == Particle.DUST_COLOR_TRANSITION

    private fun payload(color: Color, opts: EffectOptions, size: Float = opts.dustSize): Any? = when (opts.particle) {
        Particle.DUST -> Particle.DustOptions(color, size)
        Particle.DUST_COLOR_TRANSITION -> Particle.DustTransition(color, opts.toColor ?: color, size)
        Particle.ENTITY_EFFECT -> color
        else -> null
    }

    private data class Key(
        val gradient: List<Color>, val particle: Particle, val dustSize: Float, val toColor: Color?,
        val interpolation: ColorInterpolation, val colorOverLife: List<Color>, val sizeOverLife: List<Float>,
    )

    companion object {
        const val STEPS = 64
        private const val SIZED_CACHE_SIZE = 4096
        private const val HALF = 0.5

        // 5 bits per channel: 32768 colors, enough to hide banding on photos
        private const val PALETTE_BITS = 5
        private const val OPAQUE = 0xFF shl 24

        // piecewise linear color at t in 0..1 across evenly spaced stops
        fun sample(stops: List<Color>, t: Double, interpolation: ColorInterpolation = ColorInterpolation.RGB): Color {
            require(stops.isNotEmpty()) { "A gradient needs at least one color stop" }
            require(t.isFinite()) { "Gradient position must be finite" }
            if (stops.size == 1) {
                return stops[0]
            }
            // position = t * (n - 1): the segment index is its integer part, the blend weight its fraction
            val position = t.coerceIn(0.0, 1.0) * (stops.size - 1)
            val segment = position.toInt().coerceAtMost(stops.size - 2)
            val weight = position - segment
            val from = stops[segment]
            val to = stops[segment + 1]
            if (weight == 0.0) return from
            if (weight == 1.0) return to
            if (interpolation == ColorInterpolation.HSV) return hsv(from, to, weight)
            return Color.fromRGB(
                lerp(from.red, to.red, weight),
                lerp(from.green, to.green, weight),
                lerp(from.blue, to.blue, weight),
            )
        }

        // grayscale has no hue: borrow the chromatic endpoint's hue to avoid a detour through red
        private fun hsv(from: Color, to: Color, weight: Double): Color {
            val a = java.awt.Color.RGBtoHSB(from.red, from.green, from.blue, null)
            val b = java.awt.Color.RGBtoHSB(to.red, to.green, to.blue, null)
            if (a[1] == 0f) a[0] = b[0]
            if (b[1] == 0f) b[0] = a[0]
            var delta = b[0] - a[0]
            if (delta > 0.5f) delta -= 1f
            if (delta < -0.5f) delta += 1f
            val hue = ((a[0] + delta * weight + 1.0) % 1.0).toFloat()
            val saturation = (a[1] + (b[1] - a[1]) * weight).toFloat()
            val value = (a[2] + (b[2] - a[2]) * weight).toFloat()
            return Color.fromRGB(java.awt.Color.HSBtoRGB(hue, saturation, value) and 0xFFFFFF)
        }

        fun normalizedAge(time: Double, duration: Long): Double = when {
            duration == EffectOptions.INFINITE_DURATION || duration <= 1L -> 0.0
            !time.isFinite() -> 0.0
            else -> (time / (duration - 1).toDouble()).coerceIn(0.0, 1.0)
        }

        fun sampleSize(stops: List<Float>, t: Double): Float {
            require(t.isFinite()) { "Size curve position must be finite" }
            if (stops.isEmpty()) return 1f
            if (stops.size == 1) return stops[0]
            val position = t.coerceIn(0.0, 1.0) * (stops.size - 1)
            val segment = position.toInt().coerceAtMost(stops.size - 2)
            return (stops[segment] + (stops[segment + 1] - stops[segment]) * (position - segment)).toFloat()
        }

        private fun step(value: Double): Int = (value.coerceIn(0.0, 1.0) * (STEPS - 1) + HALF).toInt()

        private fun lerp(a: Int, b: Int, t: Double): Int = (a + (b - a) * t + HALF).toInt().coerceIn(0, 255)
    }
}
