package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector
import java.util.concurrent.ConcurrentHashMap

// Fibonacci-distributed particle sphere
class SphereEffect : AbstractParticleEffect("sphere") {
    // invariant unit spheres sampled once per point count and reused across frames
    // the effect is a shared singleton whose frames are calculated concurrently on async threads
    private val unitSpheres = ConcurrentHashMap<Int, List<Vector>>()

    // writes uniformly distributed sphere points into the pooled buffer
    // uses y = 1 - (i / (n - 1)) * 2 and theta = goldenAngle * i
    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val radius = param(opts, "radius", DEFAULT_RADIUS)
        val points = param(opts, "points", DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val phase = time * param(opts, "angularSpeed", DEFAULT_ANGULAR_SPEED)

        val units = unitSpheres(points)
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

    private fun unitSpheres(points: Int): List<Vector> {
        unitSpheres[points]?.let { return it }
        // animated point counts could otherwise grow the cache without bound
        if (unitSpheres.size >= MAX_CACHED_SPHERES) {
            unitSpheres.clear()
        }
        return unitSpheres.computeIfAbsent(points, MathUtils::fibonacciSphere)
    }

    companion object {
        private const val MAX_CACHED_SPHERES = 32
        private const val DEFAULT_RADIUS = 1.5
        private const val DEFAULT_POINTS = 160.0
        private const val DEFAULT_ANGULAR_SPEED = 0.04
        private const val MIN_POINTS = 4
    }
}
