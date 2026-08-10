package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.AdvancedMathUtils
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector

// parametric effect of toroidal node T(p, q) in 3D space
// https://en.wikipedia.org/wiki/Torus_knot
// x(t) = (R + r * cos(q * t)) * cos(p * t)
// y(t) = -r * sin(q * t)
// z(t) = (R + r * cos(q * t)) * sin(p * t)
class TorusKnotEffect : AbstractParticleEffect("torus_knot") {

    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> {
        val majorRadius = param(opts, PARAM_MAJOR_RADIUS, DEFAULT_MAJOR_RADIUS)
        val minorRadius = param(opts, PARAM_MINOR_RADIUS, DEFAULT_MINOR_RADIUS)
        val p = param(opts, PARAM_P, DEFAULT_P)
        val q = param(opts, PARAM_Q, DEFAULT_Q)
        val points = param(opts, PARAM_POINTS, DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val speed = param(opts, PARAM_ANGULAR_SPEED, DEFAULT_ANGULAR_SPEED)
        // phase = time * speed: phase shift for continuous node motion animation
        val phase = time * speed

        return List(points) { index ->
            // t = (i / points) * 2pi + phase: parametric angle of curve traverse
            val t = (index.toDouble() / points.toDouble()) * MathUtils.TAU + phase
            // local = torusKnot(R, r, p, q, t): calculating the coordinates of a node point
            val local: Vector = AdvancedMathUtils.torusKnot(majorRadius, minorRadius, p, q, t)
            applyModifiers(
                MathUtils.transform(local, center, opts.scale, opts.rotationYaw, opts.tiltAxis, opts.tiltAngle),
                opts,
                time,
                center
            )
        }
    }

    companion object {
        private const val PARAM_MAJOR_RADIUS = "majorRadius"
        private const val PARAM_MINOR_RADIUS = "minorRadius"
        private const val PARAM_P = "p"
        private const val PARAM_Q = "q"
        private const val PARAM_POINTS = "points"
        private const val PARAM_ANGULAR_SPEED = "angularSpeed"

        private const val DEFAULT_MAJOR_RADIUS = 1.6
        private const val DEFAULT_MINOR_RADIUS = 0.6
        private const val DEFAULT_P = 2.0
        private const val DEFAULT_Q = 3.0
        private const val DEFAULT_POINTS = 160.0
        private const val DEFAULT_ANGULAR_SPEED = 0.03
        private const val MIN_POINTS = 16
    }
}