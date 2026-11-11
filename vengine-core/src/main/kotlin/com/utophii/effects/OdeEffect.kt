package com.utophii.effects

import com.utophii.api.FollowDefaults
import com.utophii.api.EffectOptions
import com.utophii.engine.FollowAnchor
import com.utophii.math.ExpressionEvaluator
import com.utophii.math.ExpressionFunctions
import com.utophii.math.OdeIntegrator
import com.utophii.modifiers.ColorModifier
import com.utophii.modifiers.MotionModifier
import com.utophii.modifiers.RotationModifier
import org.bukkit.Location
import org.bukkit.Bukkit
import org.bukkit.util.Vector
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

// a trajectory defined by simultaneous derivatives of named state variables
class OdeEffect(
    name: String,
    initial: Map<String, String>,
    derivatives: Map<String, String>,
    private val method: OdeIntegrator.Method = OdeIntegrator.Method.RK4,
    step: String = "0.01",
    samples: String = "256",
    private val includeInitial: Boolean = false,
    reveal: String = "1",
    output: Map<String, String> = XYZ,
    parameters: Map<String, Double> = emptyMap(),
    functions: ExpressionFunctions = ExpressionFunctions.EMPTY,
    followDefaults: FollowDefaults? = null,
    val collision: OdeCollision = OdeCollision(),
) : FormulaEffect(
    name, initial.mapKeys { "initial.${it.key}" } + derivatives.mapKeys { "derivative.${it.key}" } +
        mapOf("step" to step, "samples" to samples, "reveal" to reveal), output, parameters, functions, followDefaults,
    initial.keys + "integrationTime",
) {
    private val variables = initial.keys.toList()
    private val initialKeys = variables.map { "initial.$it" }
    private val derivativeKeys = variables.map { "derivative.$it" }
    internal override val calculateOnRender: Boolean get() = collision.response != OdeCollision.Response.NONE

    init {
        validateState(initial, derivatives)
        if (calculateOnRender) {
            require(initial.keys.containsAll(PHYSICAL_STATE)) { "ODE collisions require x, y, z, vx, vy and vz state variables" }
            require(XYZ.all { (axis, variable) -> output[axis]?.trim() == variable }) {
                "ODE collisions require output coordinates {x: x, y: y, z: z}"
            }
            require(XYZ.keys.all { derivatives[it]?.trim() == "v$it" }) {
                "ODE collisions require position derivatives {x: vx, y: vy, z: vz}"
            }
        }
    }

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        if (!calculateOnRender) super.calculateInto(buffer, center, opts, time)
        else calculateForRender(buffer, center, opts, time, null)
    }

    internal override fun calculateForRender(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double, anchor: FollowAnchor?) {
        if (!calculateOnRender) {
            super.calculateForRender(buffer, center, opts, time, anchor)
            return
        }
        check(center.world == null || Bukkit.isPrimaryThread()) { "ODE block collisions must be calculated on the server thread" }
        require(opts.modifiers.all { it is MotionModifier || it is RotationModifier || it is ColorModifier }) {
            "ODE collisions support motion, rotation and color modifiers, use acceleration formulas for other forces"
        }
        val frame = frame(opts, time)
        fun uniform(key: String): Double = if (frame.has(key)) {
            requireNotNull(frame.constant(key)) { "ODE collision output yaw and spin must be uniform across the trajectory" }
        } else 0.0
        val yaw = uniform("out.yaw")
        val spin = uniform("out.spin")
        if (!yaw.isFinite() || !spin.isFinite() || !opts.scale.isFinite() || opts.scale == 0.0) {
            buffer.acquire(0)
            return
        }
        // probe the same transform used by other atoms
        buffer.acquire(4)
        val c = cos(spin)
        val s = sin(spin)
        listOf(Vector(), Vector(1, 0, 0), Vector(0, 1, 0), Vector(0, 0, 1)).forEachIndexed { i, p ->
            buffer.location(i).world = center.world
            writeParticle(buffer, i, p.x * c - p.z * s, p.y, p.x * s + p.z * c,
                center, opts, time, center, opts.rotationYaw + yaw)
        }
        anchor?.apply(buffer.list())
        val transform = OdeCollisionFrame(buffer.list().map(Location::clone))
        buffer.acquire(0)
        integrate(frame, transform) {
            val point = transform.position(Vector(frame.eval("out.x"), frame.eval("out.y"), frame.eval("out.z")))
            val index = buffer.list().size
            buffer.add(point.x, point.y, point.z)
            buffer.location(index).world = point.world
            if (frame.has("out.value")) frame.eval("out.value").takeIf(Double::isFinite)?.let { buffer.value(index, it) }
            if (frame.has("out.age")) frame.eval("out.age").takeIf(Double::isFinite)?.let { buffer.age(index, it) }
        }
    }

    override fun generate(frame: FormulaFrame, buffer: LocalPoints, time: Double) {
        buffer.clear()
        integrate(frame, null) { emit(frame, buffer) }
    }

    private fun integrate(frame: FormulaFrame, transform: OdeCollisionFrame?, emitSample: () -> Unit) {
        val dt = frame.eval("step")
        if (!dt.isFinite() || dt <= 0.0) return
        val total = count(frame, "samples")
        val reveal = frame.eval("reveal")
        if (!reveal.isFinite()) return
        val visible = ceil(total * reveal.coerceIn(0.0, 1.0)).toInt()
        frame["count"] = total.toDouble()
        frame["integrationTime"] = 0.0
        val state = DoubleArray(variables.size) { frame.eval(initialKeys[it]) }
        if (state.any { !it.isFinite() }) return
        val integrator = OdeIntegrator(state.size, method)
        var elapsed = 0.0
        val derivative: (DoubleArray, Double, DoubleArray) -> Unit = { values, t, out ->
            variables.forEachIndexed { i, key -> frame[key] = values[i] }
            frame["integrationTime"] = t
            variables.indices.forEach { i -> out[i] = frame.eval(derivativeKeys[i]) }
        }
        val collisions = transform?.let {
            OdeBlockStepper(collision, it, PHYSICAL_STATE.map(variables::indexOf), integrator, derivative)
        }
        for (index in 0 until visible) {
            frame["index"] = index.toDouble()
            if (index != 0 || !includeInitial) {
                if (collisions == null) {
                    integrator.step(state, elapsed, dt, derivative)
                    elapsed += dt
                } else elapsed += collisions.step(state, elapsed, dt)
            }
            if (state.any { !it.isFinite() }) break
            variables.forEachIndexed { i, key -> frame[key] = state[i] }
            frame["integrationTime"] = elapsed
            emitSample()
            if (collisions?.terminated == true) break
        }
    }

    private companion object {
        val PHYSICAL_STATE = listOf("x", "y", "z", "vx", "vy", "vz")
    }
}

internal fun validateState(initial: Map<String, String>, next: Map<String, String>) {
    require(initial.size in 1..16 && initial.keys == next.keys) { "Initial state and updates must have the same 1 to 16 variables" }
    val reserved = setOf("time", "duration", "integrationTime", "index", "count", "iteration", "branch", "address", "copy")
    require(initial.keys.all { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && it !in reserved && !ExpressionEvaluator.isReserved(it) }) { "Invalid or reserved state variable" }
}