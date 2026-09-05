package com.utophii.math

import net.objecthunter.exp4j.Expression
import net.objecthunter.exp4j.ExpressionBuilder
import kotlin.math.PI

// thread-safe wrapper around the exp4j expression engine.
// exp4j parses a formula into a reusable AST and evaluates it against a set of variables; it is a
// pure math evaluator and cannot execute arbitrary code, which keeps user-defined formulas safe.
// Formula variables are auto-detected so callers only have to supply the values for the variables
// they used (the scanner skips exp4j's built-in functions and mathematical constants)
class ExpressionEvaluator private constructor(
    private val expression: Expression,
    private val lock: Any,
) {
    // the set of variable names that must be supplied before evaluating
    val variableNames: Set<String> = expression.variableNames

    // evaluates the formula once; missing variables fall back to the named math constants then to 0.0,
    // and extra keys in `values` are ignored
    fun evaluate(values: Map<String, Double>): Double {
        synchronized(lock) {
            variableNames.forEach { name -> expression.setVariable(name, values[name] ?: CONSTANTS[name] ?: DEFAULT_MISSING_VARIABLE) }
            return expression.evaluate()
        }
    }

    companion object {
        private const val DEFAULT_MISSING_VARIABLE = 0.0
        private const val PROBE_VALUE = 1.0

        // identifiers that exp4j treats as built-in functions or constants and must never be declared as variables
        private val RESERVED_NAMES = setOf(
            // built-in functions
            "sin", "cos", "tan", "asin", "acos", "atan",
            "sinh", "cosh", "tanh", "log", "log10", "log2",
            "exp", "sqrt", "cbrt", "abs", "ceil", "floor", "round", "signum",
            "pow", "min", "max",
            // built-in constants
            "pi", "e", "π", "ε",
        )

        private val IDENTIFIER_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")

        // named math constants exposed as default variable values (callers may still override them)
        private val CONSTANTS = linkedMapOf(
            "tau" to MathUtils.TAU,
            "TAU" to MathUtils.TAU,
            "PI" to kotlin.math.PI,
        )

        // compiles a formula string into a reusable ExpressionEvaluator
        fun compile(formula: String): ExpressionEvaluator? {
            if (formula.isBlank()) {
                return null
            }
            val variables = (extractIdentifiers(formula) - RESERVED_NAMES).toTypedArray()
            return try {
                val expression = ExpressionBuilder(formula)
                    .variables(*variables)
                    .build()
                val evaluator = ExpressionEvaluator(expression, Any())
                // exp4j parses leniently (e.g. `R * `) and only fails at evaluation; probe once here
                evaluator.evaluate(evaluator.variableNames.associateWith { PROBE_VALUE })
                evaluator
            } catch (t: RuntimeException) {
                null
            }
        }

        // scans a formula for all identifier tokens (variables and function/constant names)
        private fun extractIdentifiers(formula: String): Set<String> =
            IDENTIFIER_PATTERN.findAll(formula).map { it.value }.toSet()
    }
}
