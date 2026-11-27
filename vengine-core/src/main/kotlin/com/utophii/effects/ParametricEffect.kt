package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.FollowDefaults
import com.utophii.math.ExpressionEvaluator
import com.utophii.math.ExpressionFunctions
import com.utophii.math.MathUtils
import com.utophii.math.Orientation
import org.bukkit.Location
import org.joml.Quaternionf
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.sqrt

// A user-defined parametric effect expressed purely as math formulas.
// Supports one or more variables (curve: `t`; surface: `theta`, `phi`) and evaluates `x`, `y`, `z`
// with the shared expression evaluator, so arbitrary parametric shapes need no Kotlin code.
// The effect inherits the pooled render buffer and the standard modifier/particle pipeline from [AbstractParticleEffect].
class ParametricEffect(
    name: String,
    private val variables: List<String>,
    private val sampling: List<Int>,
    private val ranges: List<ParamRange>,
    private val xFormula: String,
    private val yFormula: String,
    private val zFormula: String,
    private val defaults: Map<String, Double>,
    private val angularSpeed: Double,
    override val followDefaults: FollowDefaults? = null,
    // optional 0..1 gradient value of each sample
    private val valueFormula: String? = null,
    functions: ExpressionFunctions = ExpressionFunctions.EMPTY,
    // optional normalized age, independent of the spatial gradient value
    private val ageFormula: String? = null,
    // optional rotation of each fixed display model
    private val orientation: DisplayOrientation? = null,
) : AbstractParticleEffect(name), ConfiguredPrimitive {

    init {
        require(orientation !is DisplayOrientation.AlignToNormal || variables.size == 2) {
            "alignToNormal needs a surface with exactly two variables"
        }
    }

    private val orientationFormulas: List<String> = when (orientation) {
        is DisplayOrientation.Normal -> listOf(orientation.x, orientation.y, orientation.z)
        is DisplayOrientation.Angles -> listOf(orientation.x, orientation.y, orientation.z)
        else -> emptyList()
    }

    // compiled once per axis and shared across concurrent plays
    private val evaluators: List<ExpressionEvaluator> =
        (listOfNotNull(xFormula, yFormula, zFormula, valueFormula, ageFormula) + orientationFormulas)
            .map { formula -> ExpressionEvaluator.compileOrThrow(formula, functions) }
    private val ageAxis = VALUE_AXIS + if (valueFormula == null) 0 else 1
    private val orientationAxis = ageAxis + if (ageFormula == null) 0 else 1

    // writes the sampled parametric positions into the pooled buffer
    override fun calculateGeometry(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        // angularSpeed may be overridden at runtime (e.g. by a layered config parameter) for backward compatibility
        val speed = opts.parameters[ANGULAR_SPEED_PARAM] ?: angularSpeed
        val phase = time * speed
        // params = defaults + runtime options: runtime values override the configured formula defaults.
        // Reuse variable maps and per-axis forks between frames. Each concurrent calculation owns its values
        val state = borrowState(opts)
        try {
            val params = state.params
            // expose the current effect time as a formula variable for live animation
            params[TIME_VARIABLE] = time
            when (variables.size) {
                1 -> calculateCurve(buffer, center, opts, time, phase, params, samplingFor(opts), state)
                else -> calculateSurface(buffer, center, opts, time, phase, params, samplingFor(opts), state)
            }
        } finally {
            releaseState(state)
        }
    }

    // one calculation's scratch state: the formula variables, one fork per axis and orientation scratch
    private class FrameState(val params: HashMap<String, Double>, val axes: List<ExpressionEvaluator.Fork>) {
        val geometry = Quaternionf()
        val point = Quaternionf()
        // local x y z of every surface sample, kept for numerical normals
        var grid = DoubleArray(0)
    }

    // concurrent plays of the same effect borrow separate states, the pool is bounded by the calculator threads
    private val states = ConcurrentLinkedQueue<FrameState>()

    private fun borrowState(opts: EffectOptions): FrameState {
        val pooled = states.poll()
        if (pooled != null) {
            val params = pooled.params
            params.clear()
            params.putAll(defaults)
            params.putAll(opts.parameters)
            CONSTANT_NAMES.forEach { (name, value) -> params[name] = value }
            return pooled
        }
        val params = HashMap<String, Double>(defaults.size + opts.parameters.size + CONSTANT_NAMES.size + TIME_PARAM_COUNT)
        params.putAll(defaults)
        params.putAll(opts.parameters)
        CONSTANT_NAMES.forEach { (name, value) -> params[name] = value }
        return FrameState(params, evaluators.map(ExpressionEvaluator::fork))
    }

    private fun releaseState(state: FrameState) {
        if (states.size < MAX_POOLED_STATES) {
            states.offer(state)
        }
    }

    // overrides the configured sample count when the caller passes `samples` or `points` (backward compatibility)
    private fun samplingFor(opts: EffectOptions): List<Int> {
        val overridden = opts.parameters[SAMPLES_PARAM]?.toInt() ?: opts.parameters[POINTS_PARAM]?.toInt()
        return if (overridden != null && overridden >= MIN_SAMPLES) {
            if (variables.size > 1) {
                listOf(overridden, sampling.getOrElse(1) { overridden })
            } else {
                listOf(overridden)
            }
        } else {
            sampling
        }
    }

    // samples a single-variable curve (a closed loop over the first variable range)
    private fun calculateCurve(
        buffer: ParticleBuffer,
        center: Location,
        opts: EffectOptions,
        time: Double,
        phase: Double,
        params: MutableMap<String, Double>,
        samples: List<Int>,
        state: FrameState,
    ) {
        val axes = state.axes
        val variable = variables[0]
        val range = ranges[0]
        val count = samples[0].coerceAtLeast(MIN_SAMPLES)
        val span = range.last - range.first
        val oriented = prepareOrientation(state, opts)
        buffer.acquire(count)
        for (index in 0 until count) {
            // t = first + (i / count) * span + phase: advances the parameter across the open range
            val value = range.first + (index / count.toDouble()) * span + phase
            params[variable] = value
            val x = axes[0].evaluate(params)
            val y = axes[1].evaluate(params)
            val z = axes[2].evaluate(params)
            writeParticle(buffer, index, x, y, z, center, opts, time)
            writeAppearance(buffer, index, params, axes)
            if (oriented) writeExplicitOrientation(buffer, index, params, state)
        }
    }

    // samples a two-variable surface over the two variable ranges
    private fun calculateSurface(
        buffer: ParticleBuffer,
        center: Location,
        opts: EffectOptions,
        time: Double,
        phase: Double,
        params: MutableMap<String, Double>,
        samples: List<Int>,
        state: FrameState,
    ) {
        val axes = state.axes
        val variable0 = variables[0]
        val variable1 = variables[1]
        val range0 = ranges[0]
        val range1 = ranges[1]
        val count0 = samples[0].coerceAtLeast(MIN_SAMPLES)
        val count1 = samples[1].coerceAtLeast(MIN_SAMPLES)
        val span0 = range0.last - range0.first
        val span1 = range1.last - range1.first
        val oriented = prepareOrientation(state, opts)
        val surfaceNormals = oriented && orientation is DisplayOrientation.AlignToNormal
        if (surfaceNormals && state.grid.size < count0 * count1 * 3) {
            state.grid = DoubleArray(count0 * count1 * 3)
        }

        buffer.acquire(count0 * count1)
        var index = 0
        for (i in 0 until count0) {
            // u = first + (i / count0) * span + phase: longitude parameter across its range
            val value0 = range0.first + (i / count0.toDouble()) * span0 + phase
            params[variable0] = value0
            for (j in 0 until count1) {
                // v = first + (j / count1) * span: latitude parameter across its range
                val value1 = range1.first + (j / count1.toDouble()) * span1
                params[variable1] = value1
                val x = axes[0].evaluate(params)
                val y = axes[1].evaluate(params)
                val z = axes[2].evaluate(params)
                writeParticle(buffer, index, x, y, z, center, opts, time)
                writeAppearance(buffer, index, params, axes)
                if (surfaceNormals) {
                    state.grid[index * 3] = x
                    state.grid[index * 3 + 1] = y
                    state.grid[index * 3 + 2] = z
                } else if (oriented) {
                    writeExplicitOrientation(buffer, index, params, state)
                }
                index++
            }
        }
        if (surfaceNormals) {
            // signed parameter steps: a reversed range yields the same dP/du, so the side stays the analytic one
            writeSurfaceNormals(buffer, state, count0, count1, span0 / count0, span1 / count1)
        }
    }

    // orientation is computed only for fixed display models, Q_geometry is constant within one frame
    private fun prepareOrientation(state: FrameState, opts: EffectOptions): Boolean {
        if (orientation == null || !opts.display.acceptsPointOrientation) {
            return false
        }
        Orientation.geometry(opts.rotationYaw, opts.rotationPitch, opts.rotationRoll, opts.tiltAxis, opts.tiltAngle, state.geometry)
        return true
    }

    // Q_geometry * Q_point from normal or angle formulas, a degenerate value leaves Q_point identity
    private fun writeExplicitOrientation(buffer: ParticleBuffer, index: Int, params: Map<String, Double>, state: FrameState) {
        val axes = state.axes
        val a = axes[orientationAxis].evaluate(params)
        val b = axes[orientationAxis + 1].evaluate(params)
        val c = axes[orientationAxis + 2].evaluate(params)
        val point = state.point
        val valid = when (orientation) {
            is DisplayOrientation.Normal -> Orientation.facing(a, b, c, point)
            else -> Orientation.angles(a, b, c, point)
        }
        if (!valid) point.identity()
        buffer.orientation(index, point.premul(state.geometry))
    }

    // numerical dP/du x dP/dv on the local grid of this frame, sample (i, j) is at i * count1 + j
    private fun writeSurfaceNormals(buffer: ParticleBuffer, state: FrameState, count0: Int, count1: Int, step0: Double, step1: Double) {
        val grid = state.grid
        val point = state.point
        val sign = if ((orientation as DisplayOrientation.AlignToNormal).side == DisplayOrientation.Side.INWARD) -1.0 else 1.0
        for (i in 0 until count0) {
            for (j in 0 until count1) {
                val ux = derivative(grid, j, count1, i, count0, step0, 0)
                val uy = derivative(grid, j, count1, i, count0, step0, 1)
                val uz = derivative(grid, j, count1, i, count0, step0, 2)
                val vx = derivative(grid, i * count1, 1, j, count1, step1, 0)
                val vy = derivative(grid, i * count1, 1, j, count1, step1, 1)
                val vz = derivative(grid, i * count1, 1, j, count1, step1, 2)
                val nx = uy * vz - uz * vy
                val ny = uz * vx - ux * vz
                val nz = ux * vy - uy * vx
                val tu = sqrt(ux * ux + uy * uy + uz * uz)
                val tv = sqrt(vx * vx + vy * vy + vz * vz)
                val n = sqrt(nx * nx + ny * ny + nz * nz)
                // collinear or broken tangents have no normal, the relative test does not depend on the size
                val valid = tu.isFinite() && tv.isFinite() && n.isFinite() && tu > 0.0 && tv > 0.0 &&
                    n > Orientation.COLLINEAR_TOLERANCE * tu * tv &&
                    Orientation.facing(sign * nx, sign * ny, sign * nz, point)
                if (!valid) point.identity()
                buffer.orientation(i * count1 + j, point.premul(state.geometry))
            }
        }
    }

    // one component of the derivative along a grid line of n samples starting at base with the given stride.
    // Central difference inside, second-order one-sided (-3 P0 + 4 P1 - P2) / 2h at the ends
    private fun derivative(grid: DoubleArray, base: Int, stride: Int, at: Int, n: Int, step: Double, axis: Int): Double {
        fun p(k: Int) = grid[(base + k * stride) * 3 + axis]
        return when (at) {
            0 -> (-3.0 * p(0) + 4.0 * p(1) - p(2)) / (2.0 * step)
            n - 1 -> (3.0 * p(n - 1) - 4.0 * p(n - 2) + p(n - 3)) / (2.0 * step)
            else -> (p(at + 1) - p(at - 1)) / (2.0 * step)
        }
    }

    private fun writeAppearance(
        buffer: ParticleBuffer,
        index: Int,
        params: Map<String, Double>,
        axes: List<ExpressionEvaluator.Fork>,
    ) {
        if (valueFormula != null) buffer.value(index, axes[VALUE_AXIS].evaluate(params))
        if (ageFormula != null) buffer.age(index, axes[ageAxis].evaluate(params))
    }

    companion object {
        private const val MIN_SAMPLES = 4
        private const val VALUE_AXIS = 3
        private const val SAMPLES_PARAM = "samples"
        private const val POINTS_PARAM = "points"
        private const val ANGULAR_SPEED_PARAM = "angularSpeed"
        private const val TIME_VARIABLE = "time"
        private const val TIME_PARAM_COUNT = 1
        // scratch states kept for reuse, one per calculator thread is plenty
        private const val MAX_POOLED_STATES = 16

        // named mathematical constants exposed to formulas as variables (0.0 default, always overridden here)
        private val CONSTANT_NAMES = linkedMapOf(
            "tau" to MathUtils.TAU,
            "TAU" to MathUtils.TAU,
            "PI" to PI,
            "pi" to PI,
            "e" to kotlin.math.E,
        )
    }
}

// inclusive [first, last] parameter range for a sampling variable
data class ParamRange(val first: Double, val last: Double)
