package com.utophii.effects

import com.utophii.api.FollowDefaults
import com.utophii.math.ExpressionFunctions
import com.utophii.math.Hash
import com.utophii.math.ImplicitSurface
import com.utophii.math.ScalarField
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// projects deterministic planar samples onto f(u, v) = 0 and animates their settling
class FieldEffect(
    name: String,
    field: String,
    private val domain: Domain = Domain.SQUARE,
    samples: String = "256",
    peak: String = "1",
    settle: String = "60",
    period: String = "0",
    jitter: String = "0",
    output: Map<String, String> = mapOf("x" to "u", "y" to "0", "z" to "v", "value" to "strength"),
    parameters: Map<String, Double> = emptyMap(),
    functions: ExpressionFunctions = ExpressionFunctions.EMPTY,
    followDefaults: FollowDefaults? = null,
) : FormulaEffect(name, mapOf(
    "clock" to "time", "field" to field, "samples" to samples, "peak" to peak, "settle" to settle, "period" to period, "jitter" to jitter,
), output, parameters, functions, followDefaults, setOf("u", "v", "strength")) {
    enum class Domain { SQUARE, DISK }
    private data class ProjectionKey(val field: com.utophii.math.FormulaNode, val count: Int, val peak: Double)
    private data class Projection(val key: ProjectionKey, val points: DoubleArray)
    @Volatile private var projected: Projection? = null

    override fun generate(frame: FormulaFrame, buffer: LocalPoints, time: Double) {
        val size = count(frame, "samples")
        val peak = frame.eval("peak")
        val settle = frame.eval("settle").coerceAtLeast(1.0)
        val period = frame.eval("period")
        val jitter = frame.eval("jitter").coerceAtLeast(0.0)
        buffer.clear()
        if (!peak.isFinite() || peak <= 0.0 || !settle.isFinite() || !period.isFinite() || !jitter.isFinite()) return
        val local = if (period > 0.0) time % period else time
        val linear = (local / settle).coerceIn(0.0, 1.0)
        val progress = linear * linear * (3.0 - 2.0 * linear)
        val field = ScalarField { u, v, _ ->
            frame["u"] = u
            frame["v"] = v
            frame.eval("field")
        }
        frame["count"] = size.toDouble()
        val key = ProjectionKey(frame.expression("field"), size, peak)
        val previous = projected
        val targets = if (previous != null && previous.key == key) previous.points else {
            val point = DoubleArray(3)
            val data = DoubleArray(size * 5)
            repeat(size) { index ->
                frame["index"] = index.toDouble()
                frame["strength"] = 0.0
                val seed = Hash.mix(index.toLong())
                val a = Hash.signedUnit(seed)
                val b = Hash.signedUnit(Hash.mix(seed))
                val startU = if (domain == Domain.DISK) sqrt((a + 1.0) * 0.5) * cos(PI * b) else a
                val startV = if (domain == Domain.DISK) sqrt((a + 1.0) * 0.5) * sin(PI * b) else b
                point[0] = startU
                point[1] = startV
                point[2] = 0.0
                ImplicitSurface.project(field, point, 12, 0.08, planar = true)
                if (domain == Domain.SQUARE) {
                    point[0] = point[0].coerceIn(-1.0, 1.0)
                    point[1] = point[1].coerceIn(-1.0, 1.0)
                } else {
                    val r = sqrt(point[0] * point[0] + point[1] * point[1])
                    if (r > 1.0) {
                        point[0] /= r
                        point[1] /= r
                    }
                }
                val offset = index * 5
                data[offset] = startU
                data[offset + 1] = startV
                data[offset + 2] = point[0]
                data[offset + 3] = point[1]
                data[offset + 4] = abs(field.value(startU, startV, 0.0)) / peak
            }
            data.also { projected = Projection(key, it) }
        }
        repeat(size) { index ->
            frame["index"] = index.toDouble()
            frame["strength"] = 0.0
            val seed = Hash.mix(index.toLong())
            val offset = index * 5
            val startU = targets[offset]
            val startV = targets[offset + 1]
            var u = startU + (targets[offset + 2] - startU) * progress
            var v = startV + (targets[offset + 3] - startV) * progress
            if (jitter > 0.0) {
                val amplitude = jitter * abs(field.value(u, v, 0.0)) / peak
                val shake = Hash.mix(seed xor time.toLong() * 0x5DEECE66DL)
                u += amplitude * Hash.signedUnit(shake)
                v += amplitude * Hash.signedUnit(Hash.mix(shake))
            }
            frame["strength"] = targets[offset + 4]
            frame["u"] = u
            frame["v"] = v
            emit(frame, buffer)
        }
    }
}