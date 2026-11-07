package com.utophii.math

import net.objecthunter.exp4j.function.Function
import net.objecthunter.exp4j.function.Functions
import net.objecthunter.exp4j.operator.Operator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// compiled math with exp4j syntax, lazy conditionals and local formula functions.
// The immutable tree is shared. Variable values belong to each fork
class ExpressionEvaluator private constructor(
    private val parsed: ParsedFormula,
) {
    // multiple coordinate or state expressions share one variable array per calculation
    class Batch(expressions: Map<String, String>, functions: ExpressionFunctions = ExpressionFunctions.EMPTY) {
        private val compiled = expressions.mapValues { compileOrThrow(it.value, functions) }
        private val names = compiled.values.flatMap { it.parsed.variables }.distinct()
        private val slots = names.withIndex().associate { it.value to it.index }
        private val roots = compiled.mapValues { (_, expression) ->
            expression.parsed.root.sharedSlots(expression.parsed.variables.map { slots.getValue(it) }.toIntArray())
        }

        fun frame(parameters: Map<String, Double>, dynamic: Set<String>): Frame {
            val values = DoubleArray(names.size) { parameters[names[it]] ?: DEFAULT_CONSTANTS[names[it]] ?: 0.0 }
            val indices = dynamic.mapNotNullTo(mutableSetOf()) { slots[it] }
            return Frame(slots, values, roots.mapValues { it.value.frameConstants(values, indices) })
        }

        class Frame internal constructor(
            private val slots: Map<String, Int>,
            private val values: DoubleArray,
            private val expressions: Map<String, FormulaNode>,
        ) {
            operator fun set(name: String, value: Double) { slots[name]?.let { values[it] = value } }
            fun eval(name: String): Double = expressions.getValue(name).evaluate(values)
            fun has(name: String): Boolean = name in expressions
            fun constant(name: String): Double? = expressions[name]?.constantValue()
            internal fun signature(excluded: Set<String> = emptySet()): Map<String, FormulaNode> =
                expressions.filterKeys { it !in excluded }
            internal fun expression(name: String): FormulaNode = expressions.getValue(name)
        }
    }

    // the set of variable names that must be supplied before evaluating
    val variableNames: Set<String> = parsed.variables.toSet()

    // evaluates the formula once on a fresh copy,
    // and extra keys in `values` are ignored
    fun evaluate(values: Map<String, Double>): Double = fork().evaluate(values)

    // a private copy for one thread
    fun fork(): Fork = Fork(parsed)

    class Fork internal constructor(
        private val parsed: ParsedFormula,
    ) {
        private val indices = parsed.variables.withIndex().associate { it.value to it.index }
        private val values = DoubleArray(parsed.variables.size) { DEFAULT_CONSTANTS[parsed.variables[it]] ?: DEFAULT_MISSING_VARIABLE }

        // same semantics as ExpressionEvaluator.evaluate
        fun evaluate(values: Map<String, Double>): Double {
            bind(values)
            return evaluate()
        }

        // sets every variable from values, for hot loops that then
        // change only a few variables with set and call evaluate
        fun bind(values: Map<String, Double>) {
            parsed.variables.forEachIndexed { index, name ->
                this.values[index] = values[name] ?: DEFAULT_CONSTANTS[name] ?: DEFAULT_MISSING_VARIABLE
            }
        }

        // sets one variable if the formula uses it
        fun set(name: String, value: Double) {
            indices[name]?.let { values[it] = value }
        }

        // evaluates with the variables as last bound/set
        fun evaluate(): Double = parsed.root.evaluate(values)
    }

    companion object {
        private const val DEFAULT_MISSING_VARIABLE = 0.0

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

        // named math constants exposed as default variable values (callers may still override them)
        internal val DEFAULT_CONSTANTS = linkedMapOf(
            "tau" to MathUtils.TAU,
            "TAU" to MathUtils.TAU,
            "PI" to kotlin.math.PI,
        )

        internal val FIXED_CONSTANTS = mapOf("pi" to PI, "π" to PI, "e" to Math.E, "φ" to 1.61803398874)

        private fun function(name: String, arity: Int, body: (DoubleArray) -> Double) = object : Function(name, arity) {
            override fun apply(vararg args: Double): Double = body(args)
        }

        private class CurlSample {
            var arguments = DoubleArray(0)
            var value = org.bukkit.util.Vector()
        }
        private val curlSamples = ThreadLocal.withInitial { CurlSample() }

        private fun curl(args: DoubleArray): org.bukkit.util.Vector {
            val sample = curlSamples.get()
            if (!sample.arguments.contentEquals(args)) {
                sample.arguments = args.copyOf()
                sample.value = MathUtils.curlNoise(
                    args[0], args[1], args[2], args[3],
                    args[4].coerceAtLeast(MathUtils.MIN_CURL_FREQUENCY),
                    args[5].toInt().coerceIn(0, 16), args[6].coerceAtLeast(MathUtils.MIN_CURL_EPSILON),
                )
            }
            return sample.value
        }

        internal val FUNCTIONS: Map<String, Function> = (SDF_FUNCTIONS + listOf(
            function("atan2", 2) { kotlin.math.atan2(it[0], it[1]) },
            function("besselJ", 2) { Bessel.j(it[0].toInt().coerceIn(-12, 12), it[1]) },
            function("besselMode", 3) { RadialModes.value(it[0], it[1], it[2]) },
            function("curlX", 7) { curl(it).x },
            function("curlY", 7) { curl(it).y },
            function("curlZ", 7) { curl(it).z },
            function("noise", 3) { MathUtils.valueNoise(it[0], it[1], it[2]) },
            function("clamp", 3) { it[0].coerceIn(it[1], it[2]) },
            // mix extrapolates outside [0, 1]. Callers can explicitly clamp the factor when needed
            function("mix", 3) { it[0] * (1.0 - it[2]) + it[1] * it[2] },
            function("min", 2) { min(it[0], it[1]) },
            function("max", 2) { max(it[0], it[1]) },
            function("round", 1) { Math.round(it[0]).toDouble() },
            function("if", 3) { error("if must be evaluated lazily") },
        )).associateBy { it.name }

        private fun operator(symbol: String, precedence: Int, body: (Double, Double) -> Boolean) =
            object : Operator(symbol, 2, true, precedence) {
                override fun apply(vararg args: Double): Double = if (body(args[0], args[1])) 1.0 else 0.0
            }

        // all comparisons bind less tightly than arithmetic. Equality binds below ordering
        internal val OPERATORS = listOf(
            operator("<", 400) { a, b -> a < b }, operator("<=", 400) { a, b -> a <= b },
            operator(">", 400) { a, b -> a > b }, operator(">=", 400) { a, b -> a >= b },
            operator("==", 300) { a, b -> a == b }, operator("!=", 300) { a, b -> a != b },
            operator("&&", 200) { a, b -> a != 0.0 && b != 0.0 },
            operator("||", 100) { a, b -> a != 0.0 || b != 0.0 },
        ).associateBy { it.symbol }

        internal fun builtin(name: String): Function = FUNCTIONS[name] ?: requireNotNull(Functions.getBuiltinFunction(name)) {
            "Unknown function '$name'"
        }

        internal fun isFunction(name: String, functions: Map<String, Function>) =
            name in functions || Functions.getBuiltinFunction(name) != null

        internal fun isReserved(name: String) = isFunction(name, FUNCTIONS) || name in DEFAULT_CONSTANTS || name in FIXED_CONSTANTS

        /**
         * compiles a formula string into a reusable [ExpressionEvaluator].
         *
         * @param formula math expression e.g. `"R * cos(t)"`
         * @return evaluator, or null when the formula cannot be parsed or is not a valid expression
         */
        @JvmOverloads
        fun compile(formula: String, functions: ExpressionFunctions = ExpressionFunctions.EMPTY): ExpressionEvaluator? =
            try {
                compileOrThrow(formula, functions)
            } catch (_: RuntimeException) {
                null
            }

        internal fun compileOrThrow(formula: String, functions: ExpressionFunctions = ExpressionFunctions.EMPTY): ExpressionEvaluator {
            val available = FUNCTIONS + functions.compiled
            val parsed = FormulaParser(formula, available, { functions.compiled[it] ?: builtin(it) }).parse()
            // validate structure without probing arbitrary variable values
            return ExpressionEvaluator(parsed)
        }
    }
}
