package com.utophii.effects

import com.utophii.api.EffectOptions
import kotlin.math.PI
import kotlin.math.cos

// Chladni figure of a square plate u, v ∈ [-1, 1]:
// f(u, v) = cos(nπu') cos(mπv') + mix · cos(mπu') cos(nπv'), with u' = (u + 1) / 2.
// mix = -1 and +1 are the two classic free-plate families; values in between (and fractional n, m) morph between figures
// https://en.wikipedia.org/wiki/Ernst_Chladni#Chladni_figures
class ChladniEffect : PlateFigureEffect("chladni") {

    override fun plate(opts: EffectOptions): Plate = Square(
        n = param(opts, PARAM_N, DEFAULT_N),
        m = param(opts, PARAM_M, DEFAULT_M),
        blend = param(opts, PARAM_MIX, DEFAULT_MIX),
    )

    private class Square(private val n: Double, private val m: Double, private val blend: Double) : Plate {
        // standing wave of the plate, in -2..2
        override fun wave(u: Double, v: Double): Double {
            val x = (u + 1.0) * HALF * PI
            val y = (v + 1.0) * HALF * PI
            return cos(n * x) * cos(m * y) + blend * cos(m * x) * cos(n * y)
        }

        override val peak = PEAK

        override fun start(a: Double, b: Double, out: DoubleArray) {
            out[0] = a
            out[1] = b
        }

        override fun confine(point: DoubleArray) {
            point[0] = point[0].coerceIn(-1.0, 1.0)
            point[1] = point[1].coerceIn(-1.0, 1.0)
        }
    }

    companion object {
        private const val PARAM_N = "n"
        private const val PARAM_M = "m"
        private const val PARAM_MIX = "mix"

        private const val DEFAULT_N = 3.0
        private const val DEFAULT_M = 5.0
        private const val DEFAULT_MIX = -1.0
        private const val HALF = 0.5
        private const val PEAK = 2.0
    }
}
