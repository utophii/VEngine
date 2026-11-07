package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.FollowDefaults
import com.utophii.math.ExpressionEvaluator
import com.utophii.math.ExpressionFunctions
import org.bukkit.Location
import kotlin.math.cos
import kotlin.math.sin

// immutable compiled expressions, with independent variable storage for each calculation
typealias FormulaProgram = ExpressionEvaluator.Batch
typealias FormulaFrame = ExpressionEvaluator.Batch.Frame

// common formula output uses the same transforms, appearance and rendering as every other atom
abstract class FormulaEffect(
    name: String,
    expressions: Map<String, String>,
    output: Map<String, String>,
    parameters: Map<String, Double>,
    functions: ExpressionFunctions,
    override val followDefaults: FollowDefaults?,
    private val dynamicVariables: Set<String> = emptySet(),
) : AbstractParticleEffect(name), ConfiguredPrimitive {
    protected val program = FormulaProgram(expressions + output.mapKeys { "out.${it.key}" }, functions)
    private val defaults = parameters.toMap()
    @Volatile private var cached: Snapshot? = null

    private data class Snapshot(
        val signature: Map<String, com.utophii.math.FormulaNode>,
        val parameters: Map<String, Double>,
        val points: DoubleArray,
    )

    // immutable cached local geometry is independent of the world, render options and play instance.
    // Frame specialization makes unchanged equations equal even when their output yaw changes
    final override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val frame = frame(opts, time)
        val yaw = frame.constant("out.yaw")
        val spin = frame.constant("out.spin")
        if (yaw != null && !yaw.isFinite() || spin != null && !spin.isFinite()) {
            buffer.acquire(0)
            return
        }
        val signature = frame.signature(buildSet {
            if (yaw != null) add("out.yaw")
            if (spin != null) add("out.spin")
        })
        val previous = cached
        val points = if (previous != null && previous.signature == signature && previous.parameters == opts.parameters) previous.points else {
            val local = LocalPoints()
            generate(frame, local, time)
            local.snapshot().also { cached = Snapshot(signature, opts.parameters.toMap(), it) }
        }
        buffer.acquire(points.size / STRIDE)
        val spinCos = spin?.let(::cos)
        val spinSin = spin?.let(::sin)
        for (index in 0 until points.size / STRIDE) {
            val offset = index * STRIDE
            val c = spinCos ?: cos(points[offset + 6])
            val s = spinSin ?: sin(points[offset + 6])
            val x = points[offset] * c - points[offset + 2] * s
            val z = points[offset] * s + points[offset + 2] * c
            writeParticle(buffer, index, x, points[offset + 1], z,
                center, opts, time, center, opts.rotationYaw + (yaw ?: points[offset + 5]))
            points[offset + 3].takeIf(Double::isFinite)?.let { buffer.value(index, it) }
            points[offset + 4].takeIf(Double::isFinite)?.let { buffer.age(index, it) }
        }
    }

    protected abstract fun generate(frame: FormulaFrame, buffer: LocalPoints, time: Double)

    protected class LocalPoints {
        private var data = DoubleArray(128 * STRIDE)
        private var size = 0
        fun clear() { size = 0 }
        fun add(x: Double, y: Double, z: Double, value: Double, age: Double, yaw: Double, spin: Double) {
            if (size + STRIDE > data.size) data = data.copyOf(data.size * 2)
            data[size++] = x
            data[size++] = y
            data[size++] = z
            data[size++] = value
            data[size++] = age
            data[size++] = yaw
            data[size++] = spin
        }
        fun snapshot(): DoubleArray = data.copyOf(size)
    }

    init {
        require(output.keys.containsAll(listOf("x", "y", "z"))) { "output requires x, y and z" }
        require(output.keys.all { it in setOf("x", "y", "z", "value", "age", "yaw", "spin") }) { "Unknown output field" }
        require(parameters.values.all(Double::isFinite)) { "Parameters must be finite" }
    }

    protected fun frame(opts: EffectOptions, time: Double): FormulaFrame =
        program.frame(defaults + opts.parameters + mapOf("time" to time, "duration" to opts.duration.toDouble()),
            dynamicVariables + setOf("index", "count"))

    protected fun count(frame: FormulaFrame, key: String, maximum: Int = MAX_POINTS): Int {
        val value = frame.eval(key)
        return if (value.isFinite()) value.toInt().coerceIn(1, maximum) else 1
    }

    protected fun emit(frame: FormulaFrame, buffer: LocalPoints) {
        val x = frame.eval("out.x")
        val y = frame.eval("out.y")
        val z = frame.eval("out.z")
        val yaw = if (frame.has("out.yaw")) frame.eval("out.yaw") else 0.0
        val spin = if (frame.has("out.spin")) frame.eval("out.spin") else 0.0
        if (!x.isFinite() || !y.isFinite() || !z.isFinite() || !yaw.isFinite() || !spin.isFinite()) return
        buffer.add(x, y, z, if (frame.has("out.value")) frame.eval("out.value") else Double.NaN,
            if (frame.has("out.age")) frame.eval("out.age") else Double.NaN, yaw, spin)
    }

    companion object {
        const val MAX_POINTS = 4096
        private const val STRIDE = 7
        val XYZ = mapOf("x" to "x", "y" to "y", "z" to "z")
    }
}