package com.utophii.math

import net.objecthunter.exp4j.Expression
import net.objecthunter.exp4j.ExpressionBuilder
import net.objecthunter.exp4j.function.Function
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Thread-safe wrapper around the exp4j expression engine.
// exp4j parses a formula into a reusable AST and evaluates it against a set of variables; it is a
// pure math evaluator and cannot execute arbitrary code, which keeps user-defined formulas safe.
// Formula variables are auto-detected so callers only have to supply the values for the variables
// they used (the scanner skips exp4j's built-in functions and mathematical constants).
class ExpressionEvaluator private constructor(
    // never mutated after compile
    private val template: Expression,
) {
    // the set of variable names that must be supplied before evaluating
    val variableNames: Set<String> = template.variableNames

    // evaluates the formula once on a fresh copy,
    // and extra keys in `values` are ignored
    fun evaluate(values: Map<String, Double>): Double = fork().evaluate(values)

    // a private copy for one thread
    fun fork(): Fork = Fork(Expression(template), variableNames)

    class Fork internal constructor(
        private val expression: Expression,
        private val variableNames: Set<String>,
    ) {
        // same semantics as ExpressionEvaluator.evaluate
        fun evaluate(values: Map<String, Double>): Double {
            bind(values)
            return expression.evaluate()
        }

        // sets every variable from values, for hot loops that then
        // change only a few variables with set and call evaluate
        fun bind(values: Map<String, Double>) {
            variableNames.forEach { name -> expression.setVariable(name, values[name] ?: CONSTANTS[name] ?: DEFAULT_MISSING_VARIABLE) }
        }

        // sets one variable if the formula uses it
        fun set(name: String, value: Double) {
            if (name in variableNames) expression.setVariable(name, value)
        }

        // evaluates with the variables as last bound/set
        fun evaluate(): Double = expression.evaluate()
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
            // signed distance helpers
            "sdSphere", "sdBox", "sdTorus", "smin", "smax",
            // built-in constants
            "pi", "e", "π", "ε",
        )

        // signed distance functions, for implicit (sdf) effects and any other formula
        // https://iquilezles.org/articles/distfunctions/
        private val SDF_FUNCTIONS = listOf(
            // sdSphere(x, y, z, r) = |p| - r
            object : Function("sdSphere", 4) {
                override fun apply(vararg args: Double): Double = sqrt(args[0] * args[0] + args[1] * args[1] + args[2] * args[2]) - args[3]
            },
            // sdBox(x, y, z, bx, by, bz): q = |p| - b, distance = |max(q, 0)| + min(max(qx, qy, qz), 0)
            object : Function("sdBox", 6) {
                override fun apply(vararg args: Double): Double {
                    val qx = abs(args[0]) - args[3]
                    val qy = abs(args[1]) - args[4]
                    val qz = abs(args[2]) - args[5]
                    val ox = max(qx, 0.0)
                    val oy = max(qy, 0.0)
                    val oz = max(qz, 0.0)
                    return sqrt(ox * ox + oy * oy + oz * oz) + min(max(qx, max(qy, qz)), 0.0)
                }
            },
            // sdTorus(x, y, z, R, r) = |(|p.xz| - R, p.y)| - r, ring in the XZ plane
            object : Function("sdTorus", 5) {
                override fun apply(vararg args: Double): Double {
                    val ring = sqrt(args[0] * args[0] + args[2] * args[2]) - args[3]
                    return sqrt(ring * ring + args[1] * args[1]) - args[4]
                }
            },
            // smin(a, b, k): polynomial smooth union, h = max(k - |a - b|, 0) / k, min(a, b) - h² k / 4
            object : Function("smin", 3) {
                override fun apply(vararg args: Double): Double = smoothMin(args[0], args[1], args[2])
            },
            // smax(a, b, k) = -smin(-a, -b, k): smooth intersection, or subtraction as smax(a, -b, k)
            object : Function("smax", 3) {
                override fun apply(vararg args: Double): Double = -smoothMin(-args[0], -args[1], args[2])
            },
        )

        private fun smoothMin(a: Double, b: Double, k: Double): Double {
            if (k <= 0.0) {
                return min(a, b)
            }
            val h = max(k - abs(a - b), 0.0) / k
            return min(a, b) - h * h * k * QUARTER
        }

        private const val QUARTER = 0.25

        private val IDENTIFIER_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")

        // named math constants exposed as default variable values (callers may still override them)
        private val CONSTANTS = linkedMapOf(
            "tau" to MathUtils.TAU,
            "TAU" to MathUtils.TAU,
            "PI" to kotlin.math.PI,
        )

        /**
         * compiles a formula string into a reusable [ExpressionEvaluator].
         *
         * @param formula math expression e.g. `"R * cos(t)"`
         * @return evaluator, or null when the formula cannot be parsed or is not a valid expression
         */
        fun compile(formula: String): ExpressionEvaluator? {
            if (formula.isBlank()) {
                return null
            }
            val variables = (extractIdentifiers(formula) - RESERVED_NAMES).toTypedArray()
            return try {
                val expression = ExpressionBuilder(formula)
                    .functions(SDF_FUNCTIONS)
                    .variables(*variables)
                    .build()
                val evaluator = ExpressionEvaluator(expression)
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
