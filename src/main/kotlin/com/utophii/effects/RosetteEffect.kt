package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location

// Epicycloid rosette effect
class RosetteEffect : AbstractParticleEffect("rosette") {
    // calculates rosette positions from an epicycloid
    // uses x = (R + r) cos(t) - d cos((R + r) / r t)
    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> {
        val majorRadius = param(opts, "majorRadius", DEFAULT_MAJOR_RADIUS)
        val minorRadius = param(opts, "minorRadius", DEFAULT_MINOR_RADIUS)
        val distance = param(opts, "distance", DEFAULT_DISTANCE)
        val points = param(opts, "points", DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val phase = time * param(opts, "angularSpeed", DEFAULT_ANGULAR_SPEED)

        return List(points) { index ->
            // t = i / points * tau + phase: samples one animated epicycloid loop
            val t = index / points.toDouble() * MathUtils.TAU + phase
            val local = MathUtils.epicycloid(majorRadius, minorRadius, distance, t)
            applyModifiers(MathUtils.transform(local, center, opts.scale, opts.rotationYaw, opts.tiltAxis, opts.tiltAngle), opts, time)
        }
    }

    companion object {
        private const val DEFAULT_MAJOR_RADIUS = 1.0
        private const val DEFAULT_MINOR_RADIUS = 0.25
        private const val DEFAULT_DISTANCE = 0.9
        private const val DEFAULT_POINTS = 180.0
        private const val DEFAULT_ANGULAR_SPEED = 0.04
        private const val MIN_POINTS = 8
    }
}
