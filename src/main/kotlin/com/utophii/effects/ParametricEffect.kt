package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.ExpressionEvaluator
import com.utophii.math.MathUtils
import org.bukkit.Location
import kotlin.math.PI

// a user-defined parametric effect expressed purely as math formulas.
// Supports one or more variables (curve: `t`; surface: `theta`, `phi`) and evaluates `x`, `y`, `z`
// with the exp4j engine, so arbitrary parametric shapes can be created without writing Kotlin code.
// The effect inherits the pooled render buffer and the standard modifier/particle pipeline from AbstractParticleEffect
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
) : AbstractParticleEffect(name) {

    // compiled once per axis; the evaluator is thread-safe and shared across concurrent plays
    private val evaluators: List<ExpressionEvaluator> = listOf(xFormula, yFormula, zFormula)
        .map { formula -> requireNotNull(ExpressionEvaluator.compile(formula)) { "Invalid formula: '$formula'" } }

    // writes the sampled parametric positions into the pooled buffer
    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val phase = time * angularSpeed
        // params = defaults + runtime options: runtime values override the configured formula defaults
        val params = HashMap<String, Double>(defaults.size + opts.parameters.size + CONSTANT_NAMES.size)
        params.putAll(defaults)
        params.putAll(opts.parameters)
        CONSTANT_NAMES.forEach { (name, value) -> params[name] = value }

        when (variables.size) {
            1 -> calculateCurve(buffer, center, opts, time, phase, params)
            else -> calculateSurface(buffer, center, opts, time, phase, params)
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
    ) {
        val variable = variables[0]
        val range = ranges[0]
        val samples = sampling[0].coerceAtLeast(MIN_SAMPLES)
        val span = range.last - range.first
        buffer.acquire(samples)
        for (index in 0 until samples) {
            // t = first + (i / samples) * span + phase: advances the parameter across the open range
            val value = range.first + (index / samples.toDouble()) * span + phase
            params[variable] = value
            val x = evaluators[0].evaluate(params)
            val y = evaluators[1].evaluate(params)
            val z = evaluators[2].evaluate(params)
            writeParticle(buffer, index, x, y, z, center, opts, time)
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
    ) {
        val variable0 = variables[0]
        val variable1 = variables[1]
        val range0 = ranges[0]
        val range1 = ranges[1]
        val samples0 = sampling[0].coerceAtLeast(MIN_SAMPLES)
        val samples1 = sampling[1].coerceAtLeast(MIN_SAMPLES)
        val span0 = range0.last - range0.first
        val span1 = range1.last - range1.first

        buffer.acquire(samples0 * samples1)
        var index = 0
        for (i in 0 until samples0) {
            // u = first + (i / samples0) * span + phase: longitude parameter across its range
            val value0 = range0.first + (i / samples0.toDouble()) * span0 + phase
            params[variable0] = value0
            for (j in 0 until samples1) {
                // v = first + (j / samples1) * span: latitude parameter across its range
                val value1 = range1.first + (j / samples1.toDouble()) * span1
                params[variable1] = value1
                val x = evaluators[0].evaluate(params)
                val y = evaluators[1].evaluate(params)
                val z = evaluators[2].evaluate(params)
                writeParticle(buffer, index, x, y, z, center, opts, time)
                index++
            }
        }
    }

    companion object {
        private const val MIN_SAMPLES = 4

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
