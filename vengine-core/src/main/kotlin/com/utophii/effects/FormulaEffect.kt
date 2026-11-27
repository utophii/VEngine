package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.FollowDefaults
import com.utophii.math.ExpressionEvaluator
import com.utophii.math.ExpressionFunctions
import com.utophii.math.FormulaNode
import com.utophii.math.Orientation
import org.bukkit.Location
import org.joml.Quaternionf
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
    // normal and rot output fields are accepted only by generators that document them
    orientationOutput: Boolean = false,
) : AbstractParticleEffect(name), ConfiguredPrimitive {
    init {
        require(output.keys.containsAll(listOf("x", "y", "z"))) { "output requires x, y and z" }
        val allowed = BASE_OUTPUT + if (orientationOutput) DisplayOrientation.FORMULA_KEYS else emptyList()
        output.keys.firstOrNull { it !in allowed }?.let { key ->
            require(key !in DisplayOrientation.FORMULA_KEYS) { "output.$key is only supported by points" }
            throw IllegalArgumentException("Unknown output field '$key'")
        }
        require(parameters.values.all(Double::isFinite)) { "Parameters must be finite" }
    }

    protected val program = FormulaProgram(expressions + output.mapKeys { "out.${it.key}" }, functions)
    private val defaults = parameters.toMap()
    private val orientation = DisplayOrientation.fromFields(output, "output")
    private val orientationOutputs = DisplayOrientation.FORMULA_KEYS.filter(output::containsKey).map { "out.$it" }.toSet()
    @Volatile private var cached: Snapshot? = null

    private class Snapshot(
        val signature: Map<String, FormulaNode>,
        val parameters: Map<String, Double>,
        val points: DoubleArray,
        // x y z w of Q_point per point, present when the snapshot was generated for fixed display models
        val orientations: FloatArray?,
    )

    // immutable cached local geometry is independent of the world, render options and play instance.
    // Frame specialization makes unchanged equations equal even when their output yaw changes
    override fun calculateGeometry(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val frame = frame(opts, time)
        val yaw = frame.constant("out.yaw")
        val spin = frame.constant("out.spin")
        if (yaw != null && !yaw.isFinite() || spin != null && !spin.isFinite()) {
            buffer.acquire(0)
            return
        }
        // orientation formulas join the cache key only when they are evaluated,
        // so a time-dependent angle regenerates oriented frames but leaves particle layers cached
        val oriented = orientation != null && opts.display.acceptsPointOrientation
        val signature = frame.signature(buildSet {
            if (yaw != null) add("out.yaw")
            if (spin != null) add("out.spin")
            if (!oriented) addAll(orientationOutputs)
        })
        val previous = cached
        val snapshot = if (previous != null && previous.signature == signature && previous.parameters == opts.parameters &&
            (previous.orientations != null) == oriented
        ) previous else {
            val local = LocalPoints(oriented)
            generate(frame, local, time)
            Snapshot(signature, opts.parameters.toMap(), local.snapshot(), local.orientationSnapshot()).also { cached = it }
        }
        val points = snapshot.points
        val orientations = snapshot.orientations
        buffer.acquire(points.size / STRIDE)
        val spinCos = spin?.let(::cos)
        val spinSin = spin?.let(::sin)
        val geometry = if (orientations != null) Quaternionf() else null
        val model = if (orientations != null) Quaternionf() else null
        for (index in 0 until points.size / STRIDE) {
            val offset = index * STRIDE
            val pointSpin = spin ?: points[offset + 6]
            val c = spinCos ?: cos(pointSpin)
            val s = spinSin ?: sin(pointSpin)
            val x = points[offset] * c - points[offset + 2] * s
            val z = points[offset] * s + points[offset + 2] * c
            val pointYaw = opts.rotationYaw + (yaw ?: points[offset + 5])
            writeParticle(buffer, index, x, points[offset + 1], z,
                center, opts, time, center, pointYaw)
            points[offset + 3].takeIf(Double::isFinite)?.let { buffer.value(index, it) }
            points[offset + 4].takeIf(Double::isFinite)?.let { buffer.age(index, it) }
            if (orientations != null) {
                // Q_geometry turns with the same yaw and spin as this point's coordinates
                Orientation.geometry(pointYaw, opts.rotationPitch, opts.rotationRoll, opts.tiltAxis, opts.tiltAngle,
                    geometry!!, pointSpin)
                val base = index * ParticleBuffer.QUATERNION
                model!!.set(orientations[base], orientations[base + 1], orientations[base + 2], orientations[base + 3])
                buffer.orientation(index, model.premul(geometry))
            }
        }
    }

    protected abstract fun generate(frame: FormulaFrame, buffer: LocalPoints, time: Double)

    // oriented points also keep Q_point of each emitted point, so skipped coordinates never shift it
    protected class LocalPoints(internal val oriented: Boolean = false) {
        private var data = DoubleArray(128 * STRIDE)
        private var size = 0
        private var rotations = if (oriented) FloatArray(128 * ParticleBuffer.QUATERNION) else FloatArray(0)
        internal val scratch = Quaternionf()
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
        // Q_point of the point added last
        internal fun orient(rotation: Quaternionf) {
            val base = (size / STRIDE - 1) * ParticleBuffer.QUATERNION
            if (base + ParticleBuffer.QUATERNION > rotations.size) rotations = rotations.copyOf(rotations.size * 2)
            rotations[base] = rotation.x
            rotations[base + 1] = rotation.y
            rotations[base + 2] = rotation.z
            rotations[base + 3] = rotation.w
        }
        fun snapshot(): DoubleArray = data.copyOf(size)
        internal fun orientationSnapshot(): FloatArray? =
            if (oriented) rotations.copyOf(size / STRIDE * ParticleBuffer.QUATERNION) else null
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
        if (buffer.oriented) buffer.orient(pointOrientation(frame, buffer.scratch))
    }

    // Q_point from the normal or angle outputs, identity when the value of this point is degenerate
    private fun pointOrientation(frame: FormulaFrame, out: Quaternionf): Quaternionf {
        val keys = if (orientation is DisplayOrientation.Normal) NORMAL_OUTPUTS else ANGLE_OUTPUTS
        fun field(key: String) = if (frame.has(key)) frame.eval(key) else 0.0
        val a = field(keys[0])
        val b = field(keys[1])
        val c = field(keys[2])
        val valid = if (orientation is DisplayOrientation.Normal) Orientation.facing(a, b, c, out) else Orientation.angles(a, b, c, out)
        return if (valid) out else out.identity()
    }

    companion object {
        const val MAX_POINTS = 4096
        private const val STRIDE = 7
        private val BASE_OUTPUT = listOf("x", "y", "z", "value", "age", "yaw", "spin")
        private val NORMAL_OUTPUTS = DisplayOrientation.NORMAL_KEYS.map { "out.$it" }
        private val ANGLE_OUTPUTS = DisplayOrientation.ANGLE_KEYS.map { "out.$it" }
        val XYZ = mapOf("x" to "x", "y" to "y", "z" to "z")
    }
}
