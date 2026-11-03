package com.utophii.math

import net.objecthunter.exp4j.function.Function

// local, immutable formula functions
class ExpressionFunctions(definitions: Map<String, String>) {
    internal val compiled: Map<String, Function>

    init {
        require(definitions.size <= MAX_FUNCTIONS) { "At most $MAX_FUNCTIONS local functions are allowed" }
        val declarations = linkedMapOf<String, Declaration>()
        definitions.forEach { (signature, body) ->
            val match = SIGNATURE.matchEntire(signature.trim())
                ?: throw IllegalArgumentException("Invalid function signature '$signature', expected name(arg, ...)")
            val name = match.groupValues[1]
            require(!ExpressionEvaluator.isReserved(name)) { "Function name '$name' is reserved" }
            val parameterText = match.groupValues[2].trim()
            val parameters = if (parameterText.isEmpty()) emptyList() else parameterText.split(',').map(String::trim)
            require(parameters.all { IDENTIFIER.matches(it) && !ExpressionEvaluator.isReserved(it) }) {
                "Invalid or reserved parameter in '$signature'"
            }
            require(parameters.distinct().size == parameters.size) { "Duplicate parameter in '$signature'" }
            require(name !in declarations) { "Duplicate function '$name'. Overloads are not supported" }
            declarations[name] = Declaration(name, parameters, body)
        }
        require(declarations.values.none { declaration -> declaration.parameters.any { it in declarations } }) {
            "Function parameters must not reuse local function names"
        }

        val available = ExpressionEvaluator.FUNCTIONS + declarations
        val resolved = linkedMapOf<String, Function>()
        val visiting = linkedSetOf<String>()
        fun resolve(name: String): Function {
            resolved[name]?.let { return it }
            val declaration = declarations[name] ?: return ExpressionEvaluator.builtin(name)
            require(visiting.add(name)) { "Recursive functions: ${(visiting.toList() + name).joinToString(" -> ")}" }
            require(visiting.size <= FormulaParser.MAX_DEPTH) { "Function calls are nested too deeply" }
            val parsed = try {
                FormulaParser(declaration.body, available, ::resolve, declaration.parameters).parse()
            } catch (exception: RuntimeException) {
                throw IllegalArgumentException("In function '$name': ${exception.message}", exception)
            }
            visiting.remove(name)
            return FormulaFunction(name, declaration.parameters.size, parsed.root).also { resolved[name] = it }
        }
        declarations.keys.forEach(::resolve)
        compiled = resolved.toMap()
    }

    private class Declaration(name: String, val parameters: List<String>, val body: String) : Function(name, parameters.size) {
        override fun apply(vararg args: Double): Double = error("Uncompiled function '$name'")
    }

    companion object {
        val EMPTY = ExpressionFunctions(emptyMap())
        private const val MAX_FUNCTIONS = 128
        private val IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val SIGNATURE = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^()]*)\\)")
    }
}
