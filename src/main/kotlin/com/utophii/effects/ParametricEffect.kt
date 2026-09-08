package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.ExpressionEvaluator
import com.utophii.math.MathUtils
import org.bukkit.Location
import kotlin.math.PI

// a user-defined parametric effect expressed purely as math formulas.
// Supports one or more variables (curve: `t`; surface: `theta`, `phi`) and evaluates `x`, `y`, `z`
// with the exp4j engine, so arbitrary parametric shapes can be created without writing Kotlin code.
// The effect inherits the pooled render buffer and the standard modifier/particle pipeline from AbstractParticleEffect.
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
        // angularSpeed may be overridden at runtime (e.g. by a layered config parameter)
        val speed = opts.parameters[ANGULAR_SPEED_PARAM] ?: angularSpeed
        val phase = time * speed
        // params = defaults + runtime options: runtime values override the configured formula defaults
        val params = HashMap<String, Double>(defaults.size + opts.parameters.size + CONSTANT_NAMES.size)
        params.putAll(defaults)
        params.putAll(opts.parameters)
        CONSTANT_NAMES.forEach { (name, value) -> params[name] = value }

        when (variables.size) {
            1 -> calculateCurve(buffer, center, opts, time, phase, params, samplingFor(opts))
            else -> calculateSurface(buffer, center, opts, time, phase, params, samplingFor(opts))
        }
    }

    // overrides the configured sample count when the caller passes `samples` or `points`
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
    ) {
        val variable = variables[0]
        val range = ranges[0]
        val count = samples[0].coerceAtLeast(MIN_SAMPLES)
        val span = range.last - range.first
        buffer.acquire(count)
        for (index in 0 until count) {
            // t = first + (i / count) * span + phase: advances the parameter across the open range
            val value = range.first + (index / count.toDouble()) * span + phase
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
        samples: List<Int>,
    ) {
        val variable0 = variables[0]
        val variable1 = variables[1]
        val range0 = ranges[0]
        val range1 = ranges[1]
        val count0 = samples[0].coerceAtLeast(MIN_SAMPLES)
        val count1 = samples[1].coerceAtLeast(MIN_SAMPLES)
        val span0 = range0.last - range0.first
        val span1 = range1.last - range1.first

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
        private const val SAMPLES_PARAM = "samples"
        private const val POINTS_PARAM = "points"
        private const val ANGULAR_SPEED_PARAM = "angularSpeed"

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
