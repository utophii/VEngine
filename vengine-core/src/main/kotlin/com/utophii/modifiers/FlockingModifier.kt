package com.utophii.modifiers

import com.utophii.api.EffectModifier
import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

// boids steering for the indexed positions of a whole layer
class FlockingModifier(
    val radius: Double = DEFAULT_RADIUS,
    val separation: Double = DEFAULT_SEPARATION,
    val alignment: Double = DEFAULT_ALIGNMENT,
    val cohesion: Double = DEFAULT_COHESION,
    val maxSpeed: Double = DEFAULT_MAX_SPEED,
    val seed: Int = DEFAULT_SEED,
) : EffectModifier {
    companion object {
        const val DEFAULT_RADIUS = 1.5
        const val DEFAULT_SEPARATION = 0.04
        const val DEFAULT_ALIGNMENT = 0.12
        const val DEFAULT_COHESION = 0.015
        const val DEFAULT_MAX_SPEED = 0.15
        const val DEFAULT_SEED = 0
        private const val MAX_CELL_SAMPLES = 32
        private const val MAX_CATCH_UP_TICKS = 4.0
    }

    init {
        require(radius.isFinite() && radius > 0 && maxSpeed.isFinite() && maxSpeed > 0) { "radius and maxSpeed must be finite and positive" }
        require(listOf(separation, alignment, cohesion).all { it.isFinite() && it >= 0 }) { "steering weights must be finite and nonnegative" }
    }

    // single-point callbacks have no neighbourhood
    override fun modify(loc: Location, time: Double) = loc

    internal inner class Simulation {
        private val offsets = mutableListOf<Vector>()
        private val velocities = mutableListOf<Vector>()
        private var lastTime = 0.0

        fun apply(points: List<Location>, time: Double) {
            while (offsets.size < points.size) {
                val random = Random(seed.toLong() + offsets.size)
                val direction = Vector(random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0))
                offsets.add(Vector())
                velocities.add(direction.multiply(maxSpeed * 0.5 / direction.length().coerceAtLeast(1e-9)))
            }
            offsets.subList(points.size, offsets.size).clear()
            velocities.subList(points.size, velocities.size).clear()
            points.forEachIndexed { i, p -> p.add(offsets[i]) }
            var remaining = (time - lastTime).coerceIn(0.0, MAX_CATCH_UP_TICKS)
            while (remaining > 0.0 && points.isNotEmpty()) {
                val step = remaining.coerceAtMost(1.0)
                advance(points, step)
                remaining -= step
            }
            lastTime = time
        }

        private fun advance(points: List<Location>, step: Double) {
            val cells = points.indices.groupBy { i -> cell(points[i]) }
            val neighboursByCell = HashMap<Cell, List<List<Int>>>()
            val next = points.mapIndexed { i, p ->
                val origin = cell(p)
                val steer = Vector()
                var neighbours = 0
                val nearby = neighboursByCell.getOrPut(origin) {
                    buildList {
                        for (x in -1..1) for (y in -1..1) for (z in -1..1) {
                            cells[Cell(origin.x + x, origin.y + y, origin.z + z)]?.let(::add)
                        }
                    }
                }
                for (bucket in nearby) {
                    // bound work in dense cells while sampling across the full index range
                    val samples = minOf(bucket.size, MAX_CELL_SAMPLES)
                    for (k in 0 until samples) {
                        val j = bucket[(k.toLong() * bucket.size / samples).toInt()]
                        if (i == j) continue
                        val dx = points[j].x - p.x
                        val dy = points[j].y - p.y
                        val dz = points[j].z - p.z
                        val distance2 = dx * dx + dy * dy + dz * dz
                        if (distance2 > radius * radius) continue
                        neighbours++
                        val pull = cohesion - separation / distance2.coerceAtLeast(1e-6)
                        val other = velocities[j]
                        val own = velocities[i]
                        steer.x += dx * pull + (other.x - own.x) * alignment
                        steer.y += dy * pull + (other.y - own.y) * alignment
                        steer.z += dz * pull + (other.z - own.z) * alignment
                    }
                }
                val velocity = velocities[i].clone()
                if (neighbours > 0) velocity.add(steer.multiply(step / neighbours))
                val speed2 = velocity.lengthSquared()
                if (speed2 > maxSpeed * maxSpeed) velocity.multiply(maxSpeed / sqrt(speed2))
                velocity
            }
            next.forEachIndexed { i, velocity ->
                velocities[i] = velocity
                val delta = velocity.clone().multiply(step)
                offsets[i].add(delta)
                points[i].add(delta)
            }
        }

        private fun cell(p: Location) = Cell(floor(p.x / radius).toLong(), floor(p.y / radius).toLong(), floor(p.z / radius).toLong())
    }

    private data class Cell(val x: Long, val y: Long, val z: Long)
}