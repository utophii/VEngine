package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location

// Lissajous curve effect in the XZ plane
class LissajousEffect : AbstractParticleEffect("lissajous") {
    // calculates Lissajous curve samples
    // uses x = A sin(a t + delta), z = B sin(b t)
    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> {
        val amplitudeX = param(opts, "amplitudeX", DEFAULT_AMPLITUDE_X)
        val amplitudeZ = param(opts, "amplitudeZ", DEFAULT_AMPLITUDE_Z)
        val a = param(opts, "a", DEFAULT_A)
        val b = param(opts, "b", DEFAULT_B)
        val delta = param(opts, "delta", DEFAULT_DELTA)
        val points = param(opts, "points", DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val phase = time * param(opts, "angularSpeed", DEFAULT_ANGULAR_SPEED)

        return List(points) { index ->
            // t = i / points * tau + phase: samples the animated harmonic curve
            val t = index / points.toDouble() * MathUtils.TAU + phase
            val local = MathUtils.lissajous(amplitudeX, amplitudeZ, a, b, delta, t)
            applyModifiers(MathUtils.transform(local, center, opts.scale, opts.rotationYaw, opts.tiltAxis, opts.tiltAngle), opts, time)
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
