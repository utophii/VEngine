package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.FollowDefaults
import org.bukkit.Location
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

// A Lindenmayer system drawn by a 3D turtle.
// https://en.wikipedia.org/wiki/L-system  (symbols as in "The Algorithmic Beauty of Plants", ch. 1.5)
//   F, G  move forward and draw      f  move forward without drawing
//   + -   turn left / right (yaw)     & ^  pitch down / up     \ /  roll left / right
//   |     turn around                 [ ]  push / pop the turtle state
// Any other symbol only takes part in the rewriting. The turtle starts at the origin heading up (+Y), so
// planar systems (only + and -) lie in the vertical XY plane. The drawing is scaled so its largest extent is
// size blocks and anchored at the bottom center (or the center). Every particle knows its distance from the
// root along the branches: the plant grows outwards in that order, all branches at once, and it is the gradient value
class LSystemEffect(
    name: String,
    axiom: String,
    rules: Map<Char, String>,
    iterations: Int,
    private val angleDegrees: Double,
    private val size: Double,
    private val spacing: Double,
    private val maxPoints: Int,
    private val growthTicks: Double,
    private val anchorCenter: Boolean,
    private val angularSpeed: Double,
    override val followDefaults: FollowDefaults? = null,
) : AbstractParticleEffect(name), ConfiguredPrimitive {

    // the rewritten string, expanded once
    private val program: String

    // how many rewriting steps fit under MAX_SYMBOLS
    val appliedIterations: Int

    init {
        var current = axiom
        var applied = 0
        while (applied < iterations) {
            val next = StringBuilder()
            var tooLong = false
            for (symbol in current) {
                next.append(rules[symbol] ?: symbol)
                if (next.length > MAX_SYMBOLS) {
                    tooLong = true
                    break
                }
            }
            if (tooLong) break
            current = next.toString()
            applied++
        }
        program = current
        appliedIterations = applied
    }

    // drawings per turning angle
    private val drawings = ConcurrentHashMap<Double, Drawing>()

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val angle = opts.parameters[ANGLE_PARAM] ?: angleDegrees
        val drawing = drawings[angle] ?: draw(angle).also { drawn ->
            // an animated angle could otherwise grow the cache without bound
            if (drawings.size >= MAX_CACHED_DRAWINGS) drawings.clear()
            drawings[angle] = drawn
        }
        // progress = t / growth, or set directly
        val growth = opts.parameters[GROWTH_PARAM] ?: growthTicks
        val progress = (opts.parameters[PROGRESS_PARAM] ?: if (growth > 0.0) time / growth else 1.0).coerceIn(0.0, 1.0)
        val phase = time * (opts.parameters[ANGULAR_SPEED_PARAM] ?: angularSpeed)

        // the points are sorted by root distance
        val visible = (progress * drawing.count).toInt()
        buffer.acquire(visible)
        for (index in 0 until visible) {
            val offset = index * STRIDE
            writeParticle(
                buffer, index, drawing.points[offset], drawing.points[offset + 1], drawing.points[offset + 2],
                center, opts, time, center, opts.rotationYaw + phase,
            )
            buffer.value(index, if (drawing.maxDistance > 0.0) drawing.points[offset + DISTANCE_OFFSET] / drawing.maxDistance else 0.0)
        }
    }

    // walks the turtle over the program and samples particles along the drawn segments
    private fun draw(angleDegrees: Double): Drawing {
        val turn = Math.toRadians(angleDegrees)
        val segments = walk(turn)
        val segmentCount = segments.size / SEGMENT_STRIDE
        if (segmentCount == 0) {
            return Drawing(DoubleArray(0), 0.0)
        }

        // bounding box of the segment ends, for the fit and the anchor
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var minZ = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        var maxZ = Double.NEGATIVE_INFINITY
        var totalLength = 0.0
        for (segment in 0 until segmentCount) {
            val base = segment * SEGMENT_STRIDE
            for (end in 0..1) {
                val x = segments[base + end * 3]
                val y = segments[base + end * 3 + 1]
                val z = segments[base + end * 3 + 2]
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
                minZ = minOf(minZ, z); maxZ = maxOf(maxZ, z)
            }
            totalLength += STEP
        }
        val extent = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(MIN_EXTENT)
        // fit = size / extent: the largest side of the drawing becomes size blocks
        val fit = size / extent
        val anchorX = (minX + maxX) * HALF
        val anchorY = if (anchorCenter) (minY + maxY) * HALF else minY
        val anchorZ = (minZ + maxZ) * HALF

        // spacing in turtle units, widened when the drawing would need more than maxPoints particles
        val gap = maxOf(spacing / fit, totalLength / maxPoints)
        val perSegment = ceil(STEP / gap).toInt().coerceAtLeast(1)
        val pointCount = minOf(segmentCount * perSegment, maxPoints)
        val stride = segmentCount * perSegment / pointCount.toDouble()

        val points = DoubleArray(pointCount * STRIDE)
        var maxDistance = 0.0
        for (index in 0 until pointCount) {
            // sample index -> (segment, step along it), spread evenly when the cap thins the samples out
            val sample = (index * stride).toInt()
            val segment = sample / perSegment
            val t = (sample % perSegment + 1) / perSegment.toDouble()
            val base = segment * SEGMENT_STRIDE
            val offset = index * STRIDE
            // p = a + (b - a) t, then moved to the anchor and fitted
            points[offset] = (segments[base] + (segments[base + 3] - segments[base]) * t - anchorX) * fit
            points[offset + 1] = (segments[base + 1] + (segments[base + 4] - segments[base + 1]) * t - anchorY) * fit
            points[offset + 2] = (segments[base + 2] + (segments[base + 5] - segments[base + 2]) * t - anchorZ) * fit
            val distance = segments[base + SEGMENT_DISTANCE_OFFSET] + STEP * t
            points[offset + DISTANCE_OFFSET] = distance
            maxDistance = maxOf(maxDistance, distance)
        }
        return Drawing(sortByDistance(points, pointCount), maxDistance)
    }

    // runs the turtle, returns per drawn segment: start xyz, end xyz, root distance at the start
    private fun walk(turn: Double): DoubleArray {
        var segments = DoubleArray(INITIAL_SEGMENTS * SEGMENT_STRIDE)
        var segmentCount = 0
        // state: position (3), heading H (3), left L (3), up U (3), root distance (1)
        val state = DoubleArray(STATE_SIZE)
        state[H + 1] = 1.0
        state[L] = -1.0
        state[U + 2] = 1.0
        val stack = ArrayDeque<DoubleArray>()
        val cos = cos(turn)
        val sin = sin(turn)

        for (symbol in program) {
            when (symbol) {
                'F', 'G', 'f' -> {
                    val startX = state[0]
                    val startY = state[1]
                    val startZ = state[2]
                    state[0] += state[H] * STEP
                    state[1] += state[H + 1] * STEP
                    state[2] += state[H + 2] * STEP
                    if (symbol != 'f') {
                        if ((segmentCount + 1) * SEGMENT_STRIDE > segments.size) {
                            segments = segments.copyOf(segments.size * 2)
                        }
                        val base = segmentCount * SEGMENT_STRIDE
                        segments[base] = startX
                        segments[base + 1] = startY
                        segments[base + 2] = startZ
                        segments[base + 3] = state[0]
                        segments[base + 4] = state[1]
                        segments[base + 5] = state[2]
                        segments[base + SEGMENT_DISTANCE_OFFSET] = state[DISTANCE]
                        segmentCount++
                    }
                    state[DISTANCE] += STEP
                }
                // yaw around U: H' = H cos + L sin, L' = L cos - H sin
                '+' -> rotate(state, H, L, cos, sin)
                '-' -> rotate(state, H, L, cos, -sin)
                // pitch around L: H' = H cos - U sin, U' = U cos + H sin
                '&' -> rotate(state, H, U, cos, -sin)
                '^' -> rotate(state, H, U, cos, sin)
                // roll around H: L' = L cos + U sin, U' = U cos - L sin
                '\\' -> rotate(state, L, U, cos, sin)
                '/' -> rotate(state, L, U, cos, -sin)
                '|' -> rotate(state, H, L, -1.0, 0.0)
                '[' -> if (stack.size < MAX_STACK_DEPTH) stack.addLast(state.copyOf())
                ']' -> stack.removeLastOrNull()?.copyInto(state)
            }
        }
        return segments.copyOf(segmentCount * SEGMENT_STRIDE)
    }

    // rotates the turtle axes a and b within their plane: a' = a cos + b sin, b' = b cos - a sin
    private fun rotate(state: DoubleArray, a: Int, b: Int, cos: Double, sin: Double) {
        for (axis in 0 until 3) {
            val first = state[a + axis]
            val second = state[b + axis]
            state[a + axis] = first * cos + second * sin
            state[b + axis] = second * cos - first * sin
        }
    }

    // reorders the points by root distance
    private fun sortByDistance(points: DoubleArray, count: Int): DoubleArray {
        val order = (0 until count).sortedBy { points[it * STRIDE + DISTANCE_OFFSET] }
        val sorted = DoubleArray(count * STRIDE)
        order.forEachIndexed { target, source -> points.copyInto(sorted, target * STRIDE, source * STRIDE, source * STRIDE + STRIDE) }
        return sorted
    }

    // particle points (x, y, z, root distance) sorted by root distance
    private class Drawing(val points: DoubleArray, val maxDistance: Double) {
        val count = points.size / STRIDE
    }

    companion object {
        private const val ANGLE_PARAM = "angle"
        private const val GROWTH_PARAM = "growth"
        private const val PROGRESS_PARAM = "progress"
        private const val ANGULAR_SPEED_PARAM = "angularSpeed"

        // longest rewritten string
        const val MAX_SYMBOLS = 1_000_000
        private const val MAX_CACHED_DRAWINGS = 32
        private const val MAX_STACK_DEPTH = 4096
        private const val INITIAL_SEGMENTS = 256

        // turtle units: every F is one unit long before the fit
        private const val STEP = 1.0
        private const val MIN_EXTENT = 1e-9
        private const val HALF = 0.5

        // x, y, z, root distance per particle
        private const val STRIDE = 4
        private const val DISTANCE_OFFSET = 3

        // start xyz, end xyz, root distance per segment
        private const val SEGMENT_STRIDE = 7
        private const val SEGMENT_DISTANCE_OFFSET = 6

        // offsets into the turtle state
        private const val H = 3
        private const val L = 6
        private const val U = 9
        private const val DISTANCE = 12
        private const val STATE_SIZE = 13
    }
}
