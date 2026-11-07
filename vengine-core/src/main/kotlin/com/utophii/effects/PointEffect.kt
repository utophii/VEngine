package com.utophii.effects

import com.utophii.api.FollowDefaults
import com.utophii.math.ExpressionFunctions

// samples arbitrary coordinates by index, independently of curve or surface parameter ranges
class PointEffect(
    name: String,
    samples: String = "128",
    output: Map<String, String>,
    parameters: Map<String, Double> = emptyMap(),
    functions: ExpressionFunctions = ExpressionFunctions.EMPTY,
    followDefaults: FollowDefaults? = null,
) : FormulaEffect(name, mapOf("samples" to samples), output, parameters, functions, followDefaults) {
    override fun generate(frame: FormulaFrame, buffer: LocalPoints, time: Double) {
        val size = count(frame, "samples")
        frame["count"] = size.toDouble()
        buffer.clear()
        repeat(size) { index ->
            frame["index"] = index.toDouble()
            emit(frame, buffer)
        }
    }
}