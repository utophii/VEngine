package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.AdvancedMathUtils
import org.bukkit.Location
import org.bukkit.util.Vector

// parametric effect of the Halvorsen Strange Attractor.
// https://en.wikipedia.org/wiki/Halvorsen_system
// dx/dt = -a x - y - z + a z
// dy/dt = x - a y + z
// dz/dt = x + a (y - x)
class HalvorsenAttractorEffect : AbstractParticleEffect("halvorsen") {

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val a = param(opts, PARAM_A, DEFAULT_A)
        val dt = param(opts, PARAM_DT, DEFAULT_DT)
        val steps = param(opts, PARAM_STEPS, DEFAULT_STEPS).toInt().coerceAtLeast(MIN_STEPS)
        // phase = time * angularSpeed: rotation of the attractor around the central vertical axis
        val phase = time * param(opts, PARAM_ANGULAR_SPEED, DEFAULT_ANGULAR_SPEED)

        buffer.acquire(steps)
        var current = Vector(INITIAL_X, INITIAL_Y, INITIAL_Z)
        for (index in 0 until steps) {
            // current = halvorsenStep(current, a, dt): numerical integration of the next point
            current = AdvancedMathUtils.halvorsenStep(current, a, dt)

            // local = current * modelScale: centering the attractor at the origin and scaling to world units
            val localX = current.x * MODEL_SCALE
            val localY = current.y * MODEL_SCALE
            val localZ = current.z * MODEL_SCALE

            // apply scale, rotation, skew and stack modifiers
            writeParticle(buffer, index, localX, localY, localZ, center, opts, time, center, opts.rotationYaw + phase)
        }
    }

    companion object {
        private const val PARAM_A = "a"
        private const val PARAM_DT = "dt"
        private const val PARAM_STEPS = "steps"
        private const val PARAM_ANGULAR_SPEED = "angularSpeed"

        private const val DEFAULT_A = 1.1
        private const val DEFAULT_DT = 0.01
        private const val DEFAULT_STEPS = 220.0
        private const val DEFAULT_ANGULAR_SPEED = 0.03
        private const val INITIAL_X = 1.0
        private const val INITIAL_Y = 1.0
        private const val INITIAL_Z = 1.0
        private const val MODEL_SCALE = 0.12
        private const val MIN_STEPS = 10
    }
}
