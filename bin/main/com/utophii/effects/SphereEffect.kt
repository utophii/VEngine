package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location

// Fibonacci-distributed particle sphere
class SphereEffect : AbstractParticleEffect("sphere") {
    // invariant unit sphere sampled once and reused across frames to avoid reallocating per tick
    private var cachedUnits: List<org.bukkit.util.Vector>? = null

    // writes uniformly distributed sphere points into the pooled buffer
    // uses y = 1 - (i / (n - 1)) * 2 and theta = goldenAngle * i
    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val radius = param(opts, "radius", DEFAULT_RADIUS)
        val points = param(opts, "points", DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val phase = time * param(opts, "angularSpeed", DEFAULT_ANGULAR_SPEED)

        val units = cachedUnits ?: MathUtils.fibonacciSphere(points).also { cachedUnits = it }
        buffer.acquire(points)
        for (index in 0 until points) {
            val unit = units[index]
            // local = unit * radius: expands the unit Fibonacci sphere to the configured radius
            val x0 = unit.x * radius
            val y0 = unit.y * radius
            val z0 = unit.z * radius
            // x' = x cos - z sin, z' = x sin + z cos: rotates the sphere around the Y axis by the animation phase
            val cos = kotlin.math.cos(phase)
            val sin = kotlin.math.sin(phase)
            val x = x0 * cos - z0 * sin
            val z = x0 * sin + z0 * cos
            writeParticle(buffer, index, x, y0, z, center, opts, time)
        }
    }

    companion object {
        private const val DEFAULT_RADIUS = 1.5
        private const val DEFAULT_POINTS = 160.0
        private const val DEFAULT_ANGULAR_SPEED = 0.04
        private const val MIN_POINTS = 4
    }
}
