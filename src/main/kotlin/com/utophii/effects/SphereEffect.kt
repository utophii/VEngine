package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location

// Fibonacci-distributed particle sphere
class SphereEffect : AbstractParticleEffect("sphere") {
    // calculates uniformly distributed sphere points
    // uses y = 1 - (i / (n - 1)) * 2 and theta = goldenAngle * i
    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> {
        val radius = param(opts, "radius", DEFAULT_RADIUS)
        val points = param(opts, "points", DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val phase = time * param(opts, "angularSpeed", DEFAULT_ANGULAR_SPEED)

        return MathUtils.fibonacciSphere(points).map { unit ->
            // local = unit * radius: expands the unit Fibonacci sphere to the configured radius
            val local = unit.multiply(radius)
            val rotated = MathUtils.rotateRodrigues(local, org.bukkit.util.Vector(0.0, 1.0, 0.0), phase)
            applyModifiers(MathUtils.transform(rotated, center, opts.scale, opts.rotationYaw, opts.tiltAxis, opts.tiltAngle), opts, time)
        }
    }

    companion object {
        private const val DEFAULT_RADIUS = 1.5
        private const val DEFAULT_POINTS = 160.0
        private const val DEFAULT_ANGULAR_SPEED = 0.04
        private const val MIN_POINTS = 4
    }
}
