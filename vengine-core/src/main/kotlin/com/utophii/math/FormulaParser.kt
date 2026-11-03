package com.utophii.math

import net.objecthunter.exp4j.function.Function
import net.objecthunter.exp4j.operator.Operator
import net.objecthunter.exp4j.tokenizer.FunctionToken
import net.objecthunter.exp4j.tokenizer.NumberToken
import net.objecthunter.exp4j.tokenizer.OperatorToken
import net.objecthunter.exp4j.tokenizer.Token
import net.objecthunter.exp4j.tokenizer.Tokenizer
import net.objecthunter.exp4j.tokenizer.VariableToken

// immutable expression trees share only code. Each fork supplies its own values
internal sealed interface FormulaNode {
    val depth: Int
    val cost: Int
    fun evaluate(values: DoubleArray): Double
}

private class NumberNode(private val value: Double) : FormulaNode {
    override val depth = 1
    override val cost = 1
    override fun evaluate(values: DoubleArray) = value
}

private class VariableNode(private val index: Int) : FormulaNode {
    override val depth = 1
    override val cost = 1
    override fun evaluate(values: DoubleArray) = values[index]
}

private class OperatorNode(private val operator: Operator, private val arguments: List<FormulaNode>) : FormulaNode {
    override val depth = 1 + arguments.maxOf { it.depth }
    override val cost = 1 + arguments.sumOf { it.cost }
    override fun evaluate(values: DoubleArray): Double {
        val first = arguments[0].evaluate(values)
        // logical operators, like if(), never evaluate an unneeded operand
        if (operator.symbol == "&&" && first == 0.0) return 0.0
        if (operator.symbol == "||" && first != 0.0) return 1.0
        return if (arguments.size == 1) operator.apply(first)
        else operator.apply(first, arguments[1].evaluate(values))
    }
}

private class CallNode(private val function: Function, private val arguments: List<FormulaNode>) : FormulaNode {
    override val depth = 1 + (arguments.maxOfOrNull { it.depth } ?: 0) +
        ((function as? FormulaFunction)?.body?.depth ?: 0)
    override val cost = 1 + arguments.sumOf { it.cost } + ((function as? FormulaFunction)?.body?.cost ?: 0)

    override fun evaluate(values: DoubleArray): Double {
        if (function.name == "if") {
            return arguments[if (arguments[0].evaluate(values) != 0.0) 1 else 2].evaluate(values)
        }
        return function.apply(*DoubleArray(arguments.size) { arguments[it].evaluate(values) })
    }
}

internal class FormulaFunction(name: String, arity: Int, val body: FormulaNode) : Function(name, arity) {
    override fun apply(vararg args: Double): Double = body.evaluate(args)
}

internal data class ParsedFormula(val root: FormulaNode, val variables: List<String>)

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
            is FunctionToken -> call(token.function, nesting)
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
