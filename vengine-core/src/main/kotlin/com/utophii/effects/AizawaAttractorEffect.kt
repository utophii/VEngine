package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.AdvancedMathUtils
import org.bukkit.Location
import org.bukkit.util.Vector

// parametric effect of the Aizawa Strange Attractor.
// https://en.wikipedia.org/wiki/Aizawa_system
// dx/dt = x(cos β + sin α) - z sin(α - β)
// dy/dt = y(cos β - sin α) + z(cos(α - β) + 1)
// dz/dt = z sin γ + (x² + y²)(α - cos β)
class AizawaAttractorEffect : AbstractParticleEffect("aizawa") {

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val alpha = param(opts, PARAM_ALPHA, DEFAULT_ALPHA)
        val beta = param(opts, PARAM_BETA, DEFAULT_BETA)
        val gamma = param(opts, PARAM_GAMMA, DEFAULT_GAMMA)
        val dt = param(opts, PARAM_DT, DEFAULT_DT)
        val steps = param(opts, PARAM_STEPS, DEFAULT_STEPS).toInt().coerceAtLeast(MIN_STEPS)
        // phase = time * angularSpeed: rotation of the attractor around the central vertical axis
        val phase = time * param(opts, PARAM_ANGULAR_SPEED, DEFAULT_ANGULAR_SPEED)

        buffer.acquire(steps)
        var current = Vector(INITIAL_X, INITIAL_Y, INITIAL_Z)
        for (index in 0 until steps) {
            // current = aizawaStep(current, alpha, beta, gamma, dt): numerical integration of the next point
            current = AdvancedMathUtils.aizawaStep(current, alpha, beta, gamma, dt)

            // local = (current - offset) * modelScale: centering the attractor at the origin
            val localX = current.x * MODEL_SCALE
            val localY = (current.z - Z_CENTER_OFFSET) * MODEL_SCALE
            val localZ = current.y * MODEL_SCALE

            // apply scale, rotation, skew and stack modifiers
            writeParticle(buffer, index, localX, localY, localZ, center, opts, time, center, opts.rotationYaw + phase)
        }
    }

    companion object {
        private const val PARAM_ALPHA = "alpha"
        private const val PARAM_BETA = "beta"
        private const val PARAM_GAMMA = "gamma"
        private const val PARAM_DT = "dt"
        private const val PARAM_STEPS = "steps"
        private const val PARAM_ANGULAR_SPEED = "angularSpeed"

        private const val DEFAULT_ALPHA = 0.9 / 15.0
        private const val DEFAULT_BETA = 0.235
        private const val DEFAULT_GAMMA = 187.0 / 15.0
        private const val DEFAULT_DT = 0.015
        private const val DEFAULT_STEPS = 220.0
        private const val DEFAULT_ANGULAR_SPEED = 0.03
        private const val INITIAL_X = 0.1
        private const val INITIAL_Y = 0.0
        private const val INITIAL_Z = 0.0
        private const val Z_CENTER_OFFSET = 100.0
        private const val MODEL_SCALE = 0.03
        private const val MIN_STEPS = 10
    }
}
