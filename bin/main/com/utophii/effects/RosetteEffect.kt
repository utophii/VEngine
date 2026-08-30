package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location

// Epicycloid rosette effect
class RosetteEffect : AbstractParticleEffect("rosette") {
    // writes rosette positions from an epicycloid into the pooled buffer
    // uses x = (R + r) cos(t) - d cos((R + r) / r t)
    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val majorRadius = param(opts, "majorRadius", DEFAULT_MAJOR_RADIUS)
        val minorRadius = param(opts, "minorRadius", DEFAULT_MINOR_RADIUS)
        val distance = param(opts, "distance", DEFAULT_DISTANCE)
        val points = param(opts, "points", DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val phase = time * param(opts, "angularSpeed", DEFAULT_ANGULAR_SPEED)

        buffer.acquire(points)
        for (index in 0 until points) {
            // t = i / points * tau + phase: samples one animated epicycloid loop
            val t = index / points.toDouble() * MathUtils.TAU + phase
            // ratio = (R + r) / r: angular speed multiplier for the tracing point
            val ratio = (majorRadius + minorRadius) / minorRadius
            val x = (majorRadius + minorRadius) * kotlin.math.cos(t) - distance * kotlin.math.cos(ratio * t)
            val z = (majorRadius + minorRadius) * kotlin.math.sin(t) - distance * kotlin.math.sin(ratio * t)
            writeParticle(buffer, index, x, 0.0, z, center, opts, time)
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
