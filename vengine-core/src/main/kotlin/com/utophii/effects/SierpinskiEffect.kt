package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.Hash
import org.bukkit.Location
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

// self-similar fractals built from an iterated function system of k maps p' = s * p + (1 - s) * v_j.
// https://en.wikipedia.org/wiki/Iterated_function_system
// Every particle has a fixed address: the digits of its index pick the coarse levels,
// so the particles are spread evenly over the first k, k², ... cells, and a hash of the index picks the finer ones.
// The gradient value is that address read as a base-k fraction
class SierpinskiEffect private constructor(
    name: String,
    // fixed points v_j of the maps, inside the unit ball
    private val vertices: Array<DoubleArray>,
    // contraction ratio s
    private val ratio: Double,
    private val defaultPoints: Double,
    // wraps the planar attractor around the six faces of a cube
    private val cubeFaces: Boolean = false,
) : AbstractParticleEffect(name) {

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val points = param(opts, PARAM_POINTS, defaultPoints).toInt().coerceIn(MIN_POINTS, MAX_POINTS)
        val depth = param(opts, PARAM_DEPTH, DEFAULT_DEPTH).toInt().coerceIn(MIN_DEPTH, MAX_DEPTH)
        val radius = param(opts, PARAM_RADIUS, DEFAULT_RADIUS)
        // spread = 1 + pulse * sin(ωt): pushes the copies apart (> 1) or together (< 1) on every level
        val spread = 1.0 + param(opts, PARAM_PULSE, 0.0) * sin(time * param(opts, PARAM_PULSE_SPEED, DEFAULT_PULSE_SPEED))
        val phase = time * param(opts, PARAM_ANGULAR_SPEED, DEFAULT_ANGULAR_SPEED)
        val maps = vertices.size
        val pull = (1.0 - ratio) * spread
        val copies = if (cubeFaces) CUBE_FACES else 1
        // complete levels addressed by the index digits
        val evenLevels = (ln(points.toDouble() / copies) / ln(maps.toDouble()) + LOG_EPSILON).toInt().coerceAtLeast(0)
        // the address is read coarse to fine, but the maps are composed fine to coarse
        val digits = IntArray(depth)

        buffer.acquire(points)
        for (index in 0 until points) {
            var hash = Hash.mix(index.toLong())
            var rest = index / copies
            var x = 0.0
            var y = 0.0
            var z = 0.0
            var value = 0.0
            var weight = 1.0
            for (level in 0 until depth) {
                val digit = if (level < evenLevels) {
                    rest % maps
                } else {
                    ((hash ushr 1) % maps).toInt().also { hash = Hash.mix(hash) }
                }
                if (level < evenLevels) rest /= maps
                digits[level] = digit
                weight /= maps
                value += digit * weight
            }
            for (level in depth - 1 downTo 0) {
                val vertex = vertices[digits[level]]
                x = ratio * x + pull * vertex[0]
                y = ratio * y + pull * vertex[1]
                z = ratio * z + pull * vertex[2]
            }
            if (cubeFaces) {
                // face = i mod 6: axis (face / 2) is held at ±1 and the planar point spans the other two
                val face = index % CUBE_FACES
                val side = if (face % 2 == 0) 1.0 else -1.0
                val u = x
                val v = y
                when (face / 2) {
                    0 -> { x = side; y = u; z = v }
                    1 -> { x = u; y = side; z = v }
                    else -> { x = u; y = v; z = side }
                }
                // half-edge 1/√3 puts the cube corners on the unit sphere
                x *= CUBE_HALF_EDGE
                y *= CUBE_HALF_EDGE
                z *= CUBE_HALF_EDGE
                value = (face + value) / CUBE_FACES
            }
            writeParticle(buffer, index, x * radius, y * radius, z * radius, center, opts, time, center, opts.rotationYaw + phase)
            buffer.value(index, value)
        }
    }


    companion object {
        private const val PARAM_POINTS = "points"
        private const val PARAM_DEPTH = "depth"
        private const val PARAM_RADIUS = "radius"
        private const val PARAM_PULSE = "pulse"
        private const val PARAM_PULSE_SPEED = "pulseSpeed"
        private const val PARAM_ANGULAR_SPEED = "angularSpeed"

        private const val DEFAULT_DEPTH = 6.0
        private const val DEFAULT_RADIUS = 1.5
        private const val DEFAULT_PULSE_SPEED = 0.08
        private const val DEFAULT_ANGULAR_SPEED = 0.02
        private const val MIN_POINTS = 1
        private const val MAX_POINTS = 4096
        private const val MIN_DEPTH = 1
        private const val MAX_DEPTH = 16
        private const val HALF = 0.5
        private const val THIRD = 1.0 / 3.0

        private const val CUBE_FACES = 6
        private val CUBE_HALF_EDGE = 1.0 / sqrt(3.0)

        // keeps exact powers (e.g. 729 = 3^6) from rounding down a level
        private const val LOG_EPSILON = 1e-9

        // triangle in the vertical XY plane, apex up
        fun triangle() = SierpinskiEffect(
            "sierpinski_triangle",
            arrayOf(
                doubleArrayOf(0.0, 1.0, 0.0),
                doubleArrayOf(-sqrt(3.0) / 2.0, -HALF, 0.0),
                doubleArrayOf(sqrt(3.0) / 2.0, -HALF, 0.0),
            ),
            HALF,
            defaultPoints = 400.0,
        )

        // regular tetrahedron standing on its base
        fun tetrahedron(): SierpinskiEffect {
            // base vertices at y = -1/3 on a circle of radius √8 / 3
            val baseRadius = sqrt(8.0) / 3.0
            val base = (0 until 3).map { corner ->
                val angle = corner * 2.0 * Math.PI / 3.0
                doubleArrayOf(baseRadius * cos(angle), -THIRD, baseRadius * sin(angle))
            }
            return SierpinskiEffect(
                "sierpinski_tetrahedron",
                (listOf(doubleArrayOf(0.0, 1.0, 0.0)) + base).toTypedArray(),
                HALF,
                defaultPoints = 600.0,
            )
        }

        // square carpet in the vertical XY plane: the 8 outer cells of a 3 x 3 grid, corners on the unit circle
        fun carpet() = SierpinskiEffect("sierpinski_carpet", carpetCells(1.0 / sqrt(2.0)), THIRD, defaultPoints = 512.0)

        // Menger sponge seen from outside: a point cloud of the whole sponge fills its tunnels in
        fun mengerSponge() = SierpinskiEffect("menger_sponge", carpetCells(1.0), THIRD, defaultPoints = 1200.0, cubeFaces = true)

        // the 8 cells of a 3 x 3 grid around the empty center, with offsets in {-unit, 0, unit}.
        // The map p' = p/3 + (2/3) v puts each cell at (2/3) v inside a square of half-edge unit
        private fun carpetCells(unit: Double): Array<DoubleArray> {
            val cells = ArrayList<DoubleArray>()
            for (i in -1..1) for (j in -1..1) {
                if (i != 0 || j != 0) cells += doubleArrayOf(i * unit, j * unit, 0.0)
            }
            return cells.toTypedArray()
        }
    }
}
