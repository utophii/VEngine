package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location

// Lissajous curve effect in the XZ plane
class LissajousEffect : AbstractParticleEffect("lissajous") {
    // writes Lissajous curve samples into the pooled buffer
    // uses x = A sin(a t + delta), z = B sin(b t)
    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val amplitudeX = param(opts, "amplitudeX", DEFAULT_AMPLITUDE_X)
        val amplitudeZ = param(opts, "amplitudeZ", DEFAULT_AMPLITUDE_Z)
        val a = param(opts, "a", DEFAULT_A)
        val b = param(opts, "b", DEFAULT_B)
        val delta = param(opts, "delta", DEFAULT_DELTA)
        val points = param(opts, "points", DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val phase = time * param(opts, "angularSpeed", DEFAULT_ANGULAR_SPEED)

        buffer.acquire(points)
        for (index in 0 until points) {
            // t = i / points * tau + phase: samples the animated harmonic curve
            val t = index / points.toDouble() * MathUtils.TAU + phase
            // x = A sin(a t + delta), y = 0, z = B sin(b t): Lissajous point in the XZ plane
            val x = amplitudeX * kotlin.math.sin(a * t + delta)
            val z = amplitudeZ * kotlin.math.sin(b * t)
            writeParticle(buffer, index, x, 0.0, z, center, opts, time)
        }
    }

    companion object {
        private const val DEFAULT_AMPLITUDE_X = 1.6
        private const val DEFAULT_AMPLITUDE_Z = 1.6
        private const val DEFAULT_A = 3.0
        private const val DEFAULT_B = 2.0
        private const val DEFAULT_DELTA = 1.5707963267948966
        private const val DEFAULT_POINTS = 140.0
        private const val DEFAULT_ANGULAR_SPEED = 0.06
        private const val MIN_POINTS = 4
    }
}
