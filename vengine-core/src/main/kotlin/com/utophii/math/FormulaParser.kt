package com.utophii.math

import net.objecthunter.exp4j.function.Function
import net.objecthunter.exp4j.operator.Operator
import net.objecthunter.exp4j.tokenizer.FunctionToken
import net.objecthunter.exp4j.tokenizer.NumberToken
import net.objecthunter.exp4j.tokenizer.OperatorToken
import net.objecthunter.exp4j.tokenizer.Token
import net.objecthunter.exp4j.tokenizer.Tokenizer
import net.objecthunter.exp4j.tokenizer.VariableToken
import kotlin.math.pow

// immutable expression trees share only code. Each fork supplies its own values
internal sealed interface FormulaNode {
    val depth: Int
    val cost: Int
    fun evaluate(values: DoubleArray): Double
}

private data class NumberNode(val value: Double) : FormulaNode {
    override val depth = 1
    override val cost = 1
    override fun evaluate(values: DoubleArray) = value
}

private data class VariableNode(val index: Int) : FormulaNode {
    override val depth = 1
    override val cost = 1
    override fun evaluate(values: DoubleArray) = values[index]
}

private data class OperatorNode(val operator: Operator, val arguments: List<FormulaNode>) : FormulaNode {
    override val depth = 1 + arguments.maxOf { it.depth }
    override val cost = 1 + arguments.sumOf { it.cost }
    override fun evaluate(values: DoubleArray): Double {
        val first = arguments[0].evaluate(values)
        // logical operators, like if(), never evaluate an unneeded operand
        if (operator.symbol == "&&" && first == 0.0) return 0.0
        if (operator.symbol == "||" && first != 0.0) return 1.0
        if (arguments.size == 1) return when (operator.symbol) {
            "+" -> first
            "-" -> -first
            else -> operator.apply(first)
        }
        val second = arguments[1].evaluate(values)
        return when (operator.symbol) {
            "+" -> first + second
            "-" -> first - second
            "*" -> first * second
            "/" -> {
                if (second == 0.0) throw ArithmeticException("Division by zero")
                first / second
            }
            "%" -> {
                if (second == 0.0) throw ArithmeticException("Division by zero")
                first % second
            }
            "^" -> first.pow(second)
            else -> operator.apply(first, second)
        }
    }
}

private data class CallNode(val function: Function, val arguments: List<FormulaNode>) : FormulaNode {
    override val depth = 1 + (arguments.maxOfOrNull { it.depth } ?: 0) +
        ((function as? FormulaFunction)?.body?.depth ?: 0)
    override val cost = 1 + arguments.sumOf { it.cost } + ((function as? FormulaFunction)?.body?.cost ?: 0)

    override fun evaluate(values: DoubleArray): Double {
        if (function.name == "if") {
            return arguments[if (arguments[0].evaluate(values) != 0.0) 1 else 2].evaluate(values)
        }
        // common scalar operations avoid vararg arrays in dense procedural sampling loops
        when (function.name) {
            "sin" -> return kotlin.math.sin(arguments[0].evaluate(values))
            "cos" -> return kotlin.math.cos(arguments[0].evaluate(values))
            "sqrt" -> return kotlin.math.sqrt(arguments[0].evaluate(values))
            "abs" -> return kotlin.math.abs(arguments[0].evaluate(values))
            "floor" -> return kotlin.math.floor(arguments[0].evaluate(values))
            "ceil" -> return kotlin.math.ceil(arguments[0].evaluate(values))
            "round" -> return Math.round(arguments[0].evaluate(values)).toDouble()
            "min" -> return minOf(arguments[0].evaluate(values), arguments[1].evaluate(values))
            "max" -> return maxOf(arguments[0].evaluate(values), arguments[1].evaluate(values))
            "atan2" -> return kotlin.math.atan2(arguments[0].evaluate(values), arguments[1].evaluate(values))
            "clamp" -> return arguments[0].evaluate(values).coerceIn(arguments[1].evaluate(values), arguments[2].evaluate(values))
        }
        return function.apply(*DoubleArray(arguments.size) { arguments[it].evaluate(values) })
    }
}

internal class FormulaFunction(name: String, arity: Int, val body: FormulaNode) : Function(name, arity) {
    override fun apply(vararg args: Double): Double = body.evaluate(args)
}

internal data class ParsedFormula(val root: FormulaNode, val variables: List<String>)

// rebinds expressions to shared slots and expands local function calls once
internal fun FormulaNode.sharedSlots(mapping: IntArray): FormulaNode = substitute { VariableNode(mapping[it]) }

private fun FormulaNode.substitute(variable: (Int) -> FormulaNode): FormulaNode = when (this) {
    is NumberNode -> this
    is VariableNode -> variable(index)
    is OperatorNode -> OperatorNode(operator, arguments.map { it.substitute(variable) })
    is CallNode -> {
        val args = arguments.map { it.substitute(variable) }
        val call = CallNode(function, args)
        if (function is FormulaFunction && args.all { it.safeToDuplicate() }) {
            val expanded = function.body.substitute { args[it] }
            if (expanded.cost <= 16384) expanded else call
        } else call
    }
}

// inlining must preserve eager argument evaluation and cannot multiply unsafe or unbounded work
private fun FormulaNode.safeToDuplicate(): Boolean = when (this) {
    is NumberNode, is VariableNode -> true
    is OperatorNode -> operator.symbol !in setOf("/", "%") && arguments.all { it.safeToDuplicate() }
    is CallNode -> function.name in setOf("sin", "cos", "sqrt", "abs", "min", "max", "floor", "ceil", "round", "atan2") &&
        arguments.all { it.safeToDuplicate() }
}

// freeze frame constants while leaving sample and state variables mutable. Invalid arithmetic in an
// unselected lazy branch must stay lazy
internal fun FormulaNode.frameConstants(values: DoubleArray, dynamic: Set<Int>): FormulaNode {
    val node = when (this) {
        is NumberNode -> this
        is VariableNode -> if (index in dynamic) this else NumberNode(values[index])
        is OperatorNode -> {
            val first = arguments[0].frameConstants(values, dynamic)
            if (first is NumberNode) {
                if (operator.symbol == "&&" && first.value == 0.0) return NumberNode(0.0)
                if (operator.symbol == "||" && first.value != 0.0) return NumberNode(1.0)
            }
            OperatorNode(operator, listOf(first) + arguments.drop(1).map { it.frameConstants(values, dynamic) })
        }
        is CallNode -> {
            val first = arguments.firstOrNull()?.frameConstants(values, dynamic)
            if (function.name == "if" && first is NumberNode) {
                return arguments[if (first.value != 0.0) 1 else 2].frameConstants(values, dynamic)
            }
            CallNode(function, listOfNotNull(first) + arguments.drop(1).map { it.frameConstants(values, dynamic) })
        }
    }
    val constant = when (node) {
        is NumberNode -> return node
        is VariableNode -> false
        is OperatorNode -> node.arguments.all { it is NumberNode }
        is CallNode -> node.arguments.all { it is NumberNode }
    }
    return if (constant) runCatching { NumberNode(node.evaluate(values)) }.getOrDefault(node) else node
}

// keep exp4j's lexical rules and operator precedence, but retain branches.
// Function argument counts are checked at their call sites
internal class FormulaParser(
    formula: String,
    functions: Map<String, Function>,
    private val resolveFunction: (String) -> Function,
    private val parameters: List<String>? = null,
) {
    private val variables = linkedMapOf<String, Int>().apply {
        parameters?.forEachIndexed { index, name -> put(name, index) }
    }
    private val tokens: List<Token>
    private var position = 0

    init {
        require(formula.isNotBlank()) { "Empty expression" }
        require(formula.length <= MAX_LENGTH) { "Expression is too long (maximum $MAX_LENGTH characters)" }
        CALLS.findAll(formula).forEach { match ->
            val name = match.groupValues[1]
            require(ExpressionEvaluator.isFunction(name, functions)) { "Unknown function '$name'" }
        }
        val identifiers = IDENTIFIERS.findAll(formula).map { it.value }.toSet()
        val variableNames = identifiers.filterTo(mutableSetOf()) { !ExpressionEvaluator.isFunction(it, functions) }
        val tokenizer = Tokenizer(formula, functions, ExpressionEvaluator.OPERATORS, variableNames, true)
        tokens = buildList { while (tokenizer.hasNext()) add(tokenizer.nextToken()) }
    }

    fun parse(): ParsedFormula {
        val root = expression(0, 0)
        require(position == tokens.size) { "Unexpected token at position $position" }
        return ParsedFormula(root, variables.keys.toList())
    }

    private fun expression(minPrecedence: Int, nesting: Int): FormulaNode {
        require(nesting < MAX_DEPTH) { "Expression nesting is too deep" }
        val token = tokens.getOrNull(position++) ?: error("Expected an operand")
        var node = when (token) {
            is NumberToken -> NumberNode(token.value)
            is VariableToken -> variable(token.name)
            is FunctionToken -> if (tokens.getOrNull(position)?.type == Token.TOKEN_PARENTHESES_OPEN.toInt()) {
                call(token.function, nesting)
            } else variable(token.function.name)
            is OperatorToken -> {
                require(token.operator.numOperands == 1) { "Expected an operand before '${token.operator.symbol}'" }
                OperatorNode(token.operator, listOf(expression(token.operator.precedence, nesting + 1)))
            }
            else -> {
                require(token.type == Token.TOKEN_PARENTHESES_OPEN.toInt()) { "Expected an operand" }
                val grouped = expression(0, nesting + 1)
                consume(Token.TOKEN_PARENTHESES_CLOSE.toInt(), "Expected ')' after expression")
                grouped
            }
        }
        checkDepth(node)
        while (true) {
            val operator = (tokens.getOrNull(position) as? OperatorToken)?.operator ?: break
            if (operator.numOperands != 2 || operator.precedence < minPrecedence) break
            position++
            val right = expression(operator.precedence + if (operator.isLeftAssociative) 1 else 0, nesting + 1)
            node = OperatorNode(operator, listOf(node, right))
            checkDepth(node)
        }
        return node
    }

    private fun variable(name: String): FormulaNode {
        if (name in ExpressionEvaluator.FIXED_CONSTANTS ||
            (parameters != null && name in ExpressionEvaluator.DEFAULT_CONSTANTS)) {
            return NumberNode(ExpressionEvaluator.FIXED_CONSTANTS[name] ?: ExpressionEvaluator.DEFAULT_CONSTANTS.getValue(name))
        }
        require(parameters == null || name in variables) { "Unknown function parameter '$name'. Pass it explicitly" }
        return VariableNode(variables.getOrPut(name) { variables.size })
    }

    private fun call(tokenFunction: Function, nesting: Int): FormulaNode {
        consume(Token.TOKEN_PARENTHESES_OPEN.toInt(), "Expected '(' after '${tokenFunction.name}'")
        val arguments = mutableListOf<FormulaNode>()
        if (tokens.getOrNull(position)?.type != Token.TOKEN_PARENTHESES_CLOSE.toInt()) {
            do {
                arguments += expression(0, nesting + 1)
                if (tokens.getOrNull(position)?.type != Token.TOKEN_SEPARATOR.toInt()) break
                position++
            } while (true)
        }
        consume(Token.TOKEN_PARENTHESES_CLOSE.toInt(), "Expected ')' after arguments of '${tokenFunction.name}'")
        require(arguments.size == tokenFunction.numArguments) {
            "Function '${tokenFunction.name}' expects ${tokenFunction.numArguments} arguments, got ${arguments.size}"
        }
        return CallNode(resolveFunction(tokenFunction.name), arguments).also(::checkDepth)
    }

    private fun consume(type: Int, message: String) {
        require(tokens.getOrNull(position)?.type == type) { message }
        position++
    }

    private fun checkDepth(node: FormulaNode) {
        require(node.depth <= MAX_DEPTH) { "Expression or function calls are nested too deeply" }
        // acyclic functions can still multiply work exponentially by calling a previous function twice.
        // Bound the expanded work at compilation.
        require(node.cost <= MAX_COST) { "Expression exceeds $MAX_COST evaluation steps after function calls" }
    }

    companion object {
        internal const val MAX_DEPTH = 128
        private const val MAX_COST = 16384
        private const val MAX_LENGTH = 16384
        // a preceding digit keeps scientific-notation exponents out of the identifier scan
        private val IDENTIFIERS = Regex("(?<![A-Za-z0-9_])[A-Za-z_πφ][A-Za-z0-9_πφ]*")
        private val CALLS = Regex("([A-Za-z_πφ][A-Za-z0-9_πφ]*)\\s*\\(")
    }
}

internal fun FormulaNode.constantValue(): Double? = (this as? NumberNode)?.value