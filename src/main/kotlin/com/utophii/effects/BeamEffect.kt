package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector

// cubic Bezier beam effect
class BeamEffect : AbstractParticleEffect("beam") {
    // calculates a cubic Bezier beam from relative control points
    // uses B(t) = (1 - t)^3 P0 + 3(1 - t)^2 t P1 + 3(1 - t)t^2 P2 + t^3 P3
    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> {
        val length = param(opts, "length", DEFAULT_LENGTH)
        val height = param(opts, "height", DEFAULT_HEIGHT)
        val points = param(opts, "points", DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val wave = time * param(opts, "waveSpeed", DEFAULT_WAVE_SPEED)
        val p0 = center.clone().add(Vector(-length * 0.5, 0.0, 0.0))
        val p1 = center.clone().add(Vector(-length * 0.25, height, 0.0))
        val p2 = center.clone().add(Vector(length * 0.25, height, 0.0))
        val p3 = center.clone().add(Vector(length * 0.5, 0.0, 0.0))

        return List(points) { index ->
            // t = i / (points - 1): normalizes each sample along the Bezier curve
            val t = index / (points - 1.0)
            val location = MathUtils.bezier(p0, p1, p2, p3, t)
            // y += sin(t * tau + wave) * waveAmplitude: adds animated beam ripple
            location.y += kotlin.math.sin(t * MathUtils.TAU + wave) * param(opts, "waveAmplitude", DEFAULT_WAVE_AMPLITUDE)
            val local = location.toVector().subtract(center.toVector())
            applyModifiers(MathUtils.transform(local, center, opts.scale, opts.rotationYaw, opts.tiltAxis, opts.tiltAngle), opts, time)
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
