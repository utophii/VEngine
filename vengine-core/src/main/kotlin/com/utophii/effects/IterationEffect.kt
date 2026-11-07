package com.utophii.effects

import com.utophii.api.FollowDefaults
import com.utophii.math.ExpressionFunctions
import com.utophii.math.Hash
import kotlin.math.ln

// iterates configurable maps independently for each point, with stable branch addresses
class IterationEffect(
    name: String,
    initial: Map<String, String>,
    branches: List<Map<String, String>>,
    private val selection: Selection = Selection.HASH,
    private val reverse: Boolean = false,
    iterations: String = "24",
    samples: String = "512",
    copies: String = "1",
    output: Map<String, String> = XYZ,
    parameters: Map<String, Double> = emptyMap(),
    functions: ExpressionFunctions = ExpressionFunctions.EMPTY,
    followDefaults: FollowDefaults? = null,
) : FormulaEffect(
    name, initial.mapKeys { "initial.${it.key}" } + branches.flatMapIndexed { i, branch ->
        branch.map { (key, value) -> "branch.$i.$key" to value }
    }.toMap() + mapOf("iterations" to iterations, "samples" to samples, "copies" to copies),
    output, parameters, functions, followDefaults,
    initial.keys + setOf("iteration", "branch", "address", "copy"),
) {
    enum class Selection { HASH, BITS, STRATIFIED }
    private val variables = initial.keys.toList()
    private val branchCount = branches.size
    private val initialKeys = variables.map { "initial.$it" }
    private val branchKeys = branches.indices.map { i -> variables.map { "branch.$i.$it" } }

    init {
        require(branches.size in 1..64) { "Expected 1 to 64 iteration branches" }
        branches.forEach { validateState(initial, it) }
        require(selection != Selection.BITS || branches.size == 2) { "Bit selection requires two branches" }
    }

    override fun generate(frame: FormulaFrame, buffer: LocalPoints, time: Double) {
        val size = count(frame, "samples")
        val depth = count(frame, "iterations", if (selection == Selection.BITS) 64 else 128)
        val copies = count(frame, "copies", size)
        val evenLevels = if (branchCount == 1) depth else
            (ln(size.toDouble() / copies) / ln(branchCount.toDouble()) + 1e-9).toInt().coerceAtLeast(0)
        val digits = IntArray(depth)
        val state = DoubleArray(variables.size)
        frame["count"] = size.toDouble()
        buffer.clear()
        repeat(size) { index ->
            frame["index"] = index.toDouble()
            frame["copy"] = (index % copies).toDouble()
            val seed = Hash.mix(index.toLong())
            var hash = seed
            var rest = index / copies
            for (level in 0 until depth) {
                digits[level] = when {
                    selection == Selection.BITS -> ((seed ushr level) and 1L).toInt()
                    selection == Selection.STRATIFIED && level < evenLevels -> (rest % branchCount).also { rest /= branchCount }
                    else -> ((hash ushr 1) % branchCount).toInt().also { hash = Hash.mix(hash) }
                }
            }
            frame["iteration"] = 0.0
            frame["branch"] = 0.0
            frame["address"] = 0.0
            variables.forEach { frame[it] = 0.0 }
            variables.indices.forEach { i -> state[i] = frame.eval(initialKeys[i]) }
            if (state.any { !it.isFinite() }) return@repeat
            variables.forEachIndexed { i, key -> frame[key] = state[i] }
            var address = 0.0
            var weight = 1.0
            val addressDepth = if (reverse) depth else minOf(16, depth)
            repeat(addressDepth) { level ->
                weight /= branchCount
                address += digits[if (reverse) level else depth - 1 - level] * weight
            }
            frame["address"] = address
            for (step in 0 until depth) {
                val level = if (reverse) depth - 1 - step else step
                val branch = digits[level]
                frame["iteration"] = step.toDouble()
                frame["branch"] = branch.toDouble()
                variables.indices.forEach { i -> state[i] = frame.eval(branchKeys[branch][i]) }
                if (state.any { !it.isFinite() }) break
                variables.forEachIndexed { i, key -> frame[key] = state[i] }
            }
            if (state.all(Double::isFinite)) emit(frame, buffer)
        }
    }
}