package com.utophii.engine

import com.utophii.math.ExpressionEvaluator
import org.bukkit.entity.Player

// a formula that may contain PlaceholderAPI placeholders.
// Each placeholder becomes a hidden variable, values are looked up on every evaluate()
class PlaceholderExpression private constructor(
    private val evaluator: ExpressionEvaluator,
    // hidden variable name -> placeholder as written
    private val placeholders: Map<String, String>,
) {
    val hasPlaceholders: Boolean
        get() = placeholders.isNotEmpty()

    // call on the server thread when the formula has placeholders
    fun evaluate(subject: Player?, variables: Map<String, Double> = emptyMap()): Double {
        if (placeholders.isEmpty()) {
            return evaluator.evaluate(variables)
        }
        val values = HashMap<String, Double>(variables.size + placeholders.size)
        values.putAll(variables)
        placeholders.forEach { (variable, placeholder) -> values[variable] = Placeholders.value(subject, placeholder) }
        return evaluator.evaluate(values)
    }

    companion object {
        // starts with a letter so % stays usable as modulo between numbers
        private val PLACEHOLDER_PATTERN = Regex("%[A-Za-z][A-Za-z0-9_:.]*%")
        private const val VARIABLE_PREFIX = "__placeholder"

        // compiles a formula with optional %placeholders%
        fun compile(formula: String): PlaceholderExpression? {
            val variables = linkedMapOf<String, String>()
            val rewritten = PLACEHOLDER_PATTERN.replace(formula) { match ->
                variables.getOrPut(match.value) { "$VARIABLE_PREFIX${variables.size}" }
            }
            val evaluator = ExpressionEvaluator.compile(rewritten) ?: return null
            return PlaceholderExpression(evaluator, variables.entries.associate { (placeholder, variable) -> variable to placeholder })
        }
    }
}
