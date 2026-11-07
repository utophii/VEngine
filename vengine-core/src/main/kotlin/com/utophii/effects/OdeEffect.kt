package com.utophii.effects

import com.utophii.api.FollowDefaults
import com.utophii.math.ExpressionEvaluator
import com.utophii.math.ExpressionFunctions
import com.utophii.math.OdeIntegrator
import kotlin.math.ceil

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
) : FormulaEffect(
    name, initial.mapKeys { "initial.${it.key}" } + derivatives.mapKeys { "derivative.${it.key}" } +
        mapOf("step" to step, "samples" to samples, "reveal" to reveal), output, parameters, functions, followDefaults,
    initial.keys + "integrationTime",
) {
    private val variables = initial.keys.toList()
    private val initialKeys = variables.map { "initial.$it" }
    private val derivativeKeys = variables.map { "derivative.$it" }

    init {
        validateState(initial, derivatives)
    }

    override fun generate(frame: FormulaFrame, buffer: LocalPoints, time: Double) {
        buffer.clear()
        val dt = frame.eval("step")
        if (!dt.isFinite() || dt <= 0.0) return
        val total = count(frame, "samples")
        val reveal = frame.eval("reveal")
        if (!reveal.isFinite()) return
        val visible = ceil(total * reveal.coerceIn(0.0, 1.0)).toInt()
        frame["count"] = total.toDouble()
        frame["integrationTime"] = 0.0
        val state = DoubleArray(variables.size) { frame.eval(initialKeys[it]) }
        val integrator = OdeIntegrator(state.size, method)
        var elapsed = 0.0
        val derivative: (DoubleArray, Double, DoubleArray) -> Unit = { values, t, out ->
            variables.forEachIndexed { i, key -> frame[key] = values[i] }
            frame["integrationTime"] = t
            variables.indices.forEach { i -> out[i] = frame.eval(derivativeKeys[i]) }
        }
        for (index in 0 until visible) {
            frame["index"] = index.toDouble()
            if (index != 0 || !includeInitial) {
                integrator.step(state, elapsed, dt, derivative)
                elapsed += dt
            }
            if (state.any { !it.isFinite() }) break
            variables.forEachIndexed { i, key -> frame[key] = state[i] }
            frame["integrationTime"] = elapsed
            emit(frame, buffer)
        }
    }
}

internal fun validateState(initial: Map<String, String>, next: Map<String, String>) {
    require(initial.size in 1..16 && initial.keys == next.keys) { "Initial state and updates must have the same 1 to 16 variables" }
    val reserved = setOf("time", "duration", "integrationTime", "index", "count", "iteration", "branch", "address", "copy")
    require(initial.keys.all { it.matches(Regex("[A-Za-z_][A-Za-z0-9_]*")) && it !in reserved && !ExpressionEvaluator.isReserved(it) }) { "Invalid or reserved state variable" }
}