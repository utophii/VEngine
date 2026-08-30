package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location

// cubic Bezier beam effect
class BeamEffect : AbstractParticleEffect("beam") {
    // writes a cubic Bezier beam from relative control points into the pooled buffer
    // uses B(t) = (1 - t)^3 P0 + 3(1 - t)^2 t P1 + 3(1 - t)t^2 P2 + t^3 P3
    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val length = param(opts, "length", DEFAULT_LENGTH)
        val height = param(opts, "height", DEFAULT_HEIGHT)
        val points = param(opts, "points", DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val wave = time * param(opts, "waveSpeed", DEFAULT_WAVE_SPEED)
        val waveAmplitude = param(opts, "waveAmplitude", DEFAULT_WAVE_AMPLITUDE)

        // control points expressed as offsets from the center (computed as scalars, no Location allocation)
        val p0x = center.x - length * 0.5
        val p1x = center.x - length * 0.25
        val p2x = center.x + length * 0.25
        val p3x = center.x + length * 0.5
        val p1y = center.y + height
        val p2y = center.y + height

        buffer.acquire(points)
        for (index in 0 until points) {
            // t = i / (points - 1): normalizes each sample along the Bezier curve
            val t = index / (points - 1.0)
            val u = 1.0 - t
            // B(t) = u^3 P0 + 3u^2 t P1 + 3u t^2 P2 + t^3 P3: cubic Bezier control point interpolation
            val u2 = u * u
            val t2 = t * t
            val wx = u2 * u * p0x + 3.0 * u2 * t * p1x + 3.0 * u * t2 * p2x + t2 * t * p3x
            val wy = u2 * u * center.y + 3.0 * u2 * t * p1y + 3.0 * u * t2 * p2y + t2 * t * center.y
            // y += sin(t * tau + wave) * waveAmplitude: adds animated beam ripple
            val ry = wy + kotlin.math.sin(t * MathUtils.TAU + wave) * waveAmplitude
            val rz = center.z
            writeParticle(buffer, index, wx - center.x, ry - center.y, rz - center.z, center, opts, time)
        }
    }

    companion object {
        private const val DEFAULT_LENGTH = 5.0
        private const val DEFAULT_HEIGHT = 1.2
        private const val DEFAULT_POINTS = 80.0
        private const val DEFAULT_WAVE_SPEED = 0.2
        private const val DEFAULT_WAVE_AMPLITUDE = 0.08
        private const val MIN_POINTS = 2
    }
}
