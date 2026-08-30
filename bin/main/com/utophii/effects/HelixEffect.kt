package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector

// animated vertical helix
class HelixEffect : AbstractParticleEffect("helix") {
    // writes helix particle positions into the pooled buffer
    // uses x = r cos(t), y = h t, z = r sin(t)
    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val radius = param(opts, "radius", DEFAULT_RADIUS)
        val height = param(opts, "height", DEFAULT_HEIGHT)
        val turns = param(opts, "turns", DEFAULT_TURNS)
        val points = param(opts, "points", DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val phase = time * param(opts, "angularSpeed", DEFAULT_ANGULAR_SPEED)
        val heightStep = height / (MathUtils.TAU * turns)

        buffer.acquire(points)
        for (index in 0 until points) {
            // t = i / points * turns * tau + phase: spreads samples across the animated helix
            val t = (index / points.toDouble()) * turns * MathUtils.TAU + phase
            val local = MathUtils.helix(radius, heightStep, t)
            // shifted = local - (0, height/2, 0): centers the helix vertically around the effect origin
            val shifted = local.subtract(Vector(0.0, height * 0.5, 0.0))
            writeParticle(buffer, index, shifted, center, opts, time)
        }
    }

    companion object {
        private const val DEFAULT_RADIUS = 1.0
        private const val DEFAULT_HEIGHT = 3.0
        private const val DEFAULT_TURNS = 3.0
        private const val DEFAULT_POINTS = 96.0
        private const val DEFAULT_ANGULAR_SPEED = 0.12
        private const val MIN_POINTS = 4
    }
}
