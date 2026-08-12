package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.AdvancedMathUtils
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector

// parametric effect of the Lorenz Strange Attractor.
// https://en.wikipedia.org/wiki/Lorenz_system
// dx/dt = σ(y - x)
// dy/dt = x(ρ - z) - y
// dz/dt = xy - βz
class LorenzAttractorEffect : AbstractParticleEffect("lorenz") {

    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> {
        val sigma = param(opts, PARAM_SIGMA, DEFAULT_SIGMA)
        val rho = param(opts, PARAM_RHO, DEFAULT_RHO)
        val beta = param(opts, PARAM_BETA, DEFAULT_BETA)
        val dt = param(opts, PARAM_DT, DEFAULT_DT)
        val steps = param(opts, PARAM_STEPS, DEFAULT_STEPS).toInt().coerceAtLeast(MIN_STEPS)
        // phase = time * angularSpeed: rotation of the attractor around the central vertical axis
        val phase = time * param(opts, PARAM_ANGULAR_SPEED, DEFAULT_ANGULAR_SPEED)

        val positions = ArrayList<Location>(steps)
        var current = Vector(INITIAL_X, INITIAL_Y, INITIAL_Z)

        repeat(steps) {
            // current = lorenzStep(current, sigma, rho, beta, dt): numerical integration of the next point
            current = AdvancedMathUtils.lorenzStep(current, sigma, rho, beta, dt)

            // local = (current - offset) * modelScale: centering the two hemispheres of the attractor at the origin
            val local = Vector(
                current.x * MODEL_SCALE,
                (current.z - RHO_CENTER_OFFSET) * MODEL_SCALE,
                current.y * MODEL_SCALE
            )

            // apply scale, rotation, skew and stack modifiers
            positions += applyModifiers(
                MathUtils.transform(local, center, opts.scale, opts.rotationYaw + phase, opts.tiltAxis, opts.tiltAngle),
                opts,
                time,
                center
            )
        }

        return positions
    }

    companion object {
        private const val PARAM_SIGMA = "sigma"
        private const val PARAM_RHO = "rho"
        private const val PARAM_BETA = "beta"
        private const val PARAM_DT = "dt"
        private const val PARAM_STEPS = "steps"
        private const val PARAM_ANGULAR_SPEED = "angularSpeed"

        private const val DEFAULT_SIGMA = 10.0
        private const val DEFAULT_RHO = 28.0
        private const val DEFAULT_BETA = 8.0 / 3.0
        private const val DEFAULT_DT = 0.015
        private const val DEFAULT_STEPS = 220.0
        private const val DEFAULT_ANGULAR_SPEED = 0.03
        private const val INITIAL_X = 0.1
        private const val INITIAL_Y = 0.0
        private const val INITIAL_Z = 0.0
        private const val RHO_CENTER_OFFSET = 25.0
        private const val MODEL_SCALE = 0.085
        private const val MIN_STEPS = 10
    }
}