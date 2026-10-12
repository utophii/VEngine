package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.AdvancedMathUtils
import org.bukkit.Location
import org.bukkit.util.Vector

// parametric effect of the Rössler Strange Attractor.
// https://en.wikipedia.org/wiki/R%C3%B6ssler_system
// dx/dt = -(y + z)
// dy/dt = x + a*y
// dz/dt = b + z*(x - c)
class RosslerAttractorEffect : AbstractParticleEffect("rossler") {

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val a = param(opts, PARAM_A, DEFAULT_A)
        val b = param(opts, PARAM_B, DEFAULT_B)
        val c = param(opts, PARAM_C, DEFAULT_C)
        val dt = param(opts, PARAM_DT, DEFAULT_DT)
        val steps = param(opts, PARAM_STEPS, DEFAULT_STEPS).toInt().coerceAtLeast(MIN_STEPS)
        // phase = time * angularSpeed: rotation of the attractor around the central vertical axis
        val phase = time * param(opts, PARAM_ANGULAR_SPEED, DEFAULT_ANGULAR_SPEED)

        buffer.acquire(steps)
        var current = Vector(INITIAL_X, INITIAL_Y, INITIAL_Z)
        for (index in 0 until steps) {
            // current = rosslerStep(current, a, b, c, dt): numerical integration of the next point
            current = AdvancedMathUtils.rosslerStep(current, a, b, c, dt)

            // local = (current - offset) * modelScale: centering the attractor at the origin and scaling to world units
            val localX = current.x * MODEL_SCALE
            val localY = (current.z - Z_CENTER_OFFSET) * MODEL_SCALE
            val localZ = current.y * MODEL_SCALE

            // apply scale, rotation, skew and stack modifiers
            writeParticle(buffer, index, localX, localY, localZ, center, opts, time, center, opts.rotationYaw + phase)
        }
    }

    companion object {
        private const val PARAM_A = "a"
        private const val PARAM_B = "b"
        private const val PARAM_C = "c"
        private const val PARAM_DT = "dt"
        private const val PARAM_STEPS = "steps"
        private const val PARAM_ANGULAR_SPEED = "angularSpeed"

        private const val DEFAULT_A = 0.1
        private const val DEFAULT_B = 0.2
        private const val DEFAULT_C = 5.7
        private const val DEFAULT_DT = 0.02
        private const val DEFAULT_STEPS = 220.0
        private const val DEFAULT_ANGULAR_SPEED = 0.03
        private const val INITIAL_X = 0.1
        private const val INITIAL_Y = 0.0
        private const val INITIAL_Z = 0.0
        private const val Z_CENTER_OFFSET = 12.0
        private const val MODEL_SCALE = 0.12
        private const val MIN_STEPS = 10
    }
}
