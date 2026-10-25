package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.Bessel
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Chladni figure of a round plate of radius 1, in polar coordinates r, θ:
// f(r, θ) = Rₙₘ(r) cos(nθ) + mix · Rₙ₂ₘ₂(r) cos(n₂θ), with the mode Rₙₘ(r) = Jₙ(k r) / max|Jₙ(k r)|.
// https://en.wikipedia.org/wiki/Vibrations_of_a_circular_membrane
// Mode (n, m) has n nodal diameters and m nodal circles inside the plate: k is the peak of Jₙ between its m-th
// and (m+1)-th zero, so the rim swings freely like the edge of a real plate. n and m are whole numbers
// (a fractional n would tear the wave at θ = 0), morph between figures with mix
class CircularChladniEffect : PlateFigureEffect("chladni_circle") {

    override fun plate(opts: EffectOptions): Plate {
        val first = mode(param(opts, PARAM_N, DEFAULT_N), param(opts, PARAM_M, DEFAULT_M))
        val blend = param(opts, PARAM_MIX, 0.0)
        val second = if (blend != 0.0) mode(param(opts, PARAM_N2, DEFAULT_N2), param(opts, PARAM_M2, DEFAULT_M2)) else first
        return Disk(first, second, blend)
    }

    // the radial profile of mode (n, m), sampled once per mode
    private fun mode(n: Double, m: Double): Mode {
        val order = n.roundToInt().coerceIn(0, MAX_ORDER)
        // J₀ has no peak before its first zero: (0, 0) would be a flat plate
        val circles = m.roundToInt().coerceIn(if (order == 0) 1 else 0, MAX_CIRCLES)
        return modes.getOrPut(order * (MAX_CIRCLES + 1) + circles) { Mode.sample(order, circles) }
    }

    // at most (MAX_ORDER + 1) * (MAX_CIRCLES + 1) entries
    private val modes = ConcurrentHashMap<Int, Mode>()

    private class Mode(val order: Int, private val profile: DoubleArray) {
        // R(r) by linear interpolation of the sampled profile
        fun radial(r: Double): Double {
            val position = r.coerceIn(0.0, 1.0) * (profile.size - 1)
            val index = position.toInt().coerceAtMost(profile.size - 2)
            val t = position - index
            return profile[index] + (profile[index + 1] - profile[index]) * t
        }

        companion object {
            fun sample(order: Int, circles: Int): Mode {
                // k = the zero of Jₙ' between the m-th and (m+1)-th zero of Jₙ (0 stands in for the 0-th)
                val inner = if (circles == 0) 0.0 else Bessel.zero(order, circles)
                val k = Bessel.extremum(order, inner, Bessel.zero(order, circles + 1))
                val profile = DoubleArray(PROFILE_SAMPLES) { index -> Bessel.j(order, k * index / (PROFILE_SAMPLES - 1.0)) }
                val peak = profile.maxOf(::abs)
                for (index in profile.indices) profile[index] /= peak
                return Mode(order, profile)
            }
        }
    }

    private class Disk(private val first: Mode, private val second: Mode, private val blend: Double) : Plate {
        override fun wave(u: Double, v: Double): Double {
            val r = sqrt(u * u + v * v)
            val theta = atan2(v, u)
            val wave = first.radial(r) * cos(first.order * theta)
            return if (blend == 0.0) wave else wave + blend * second.radial(r) * cos(second.order * theta)
        }

        override val peak = 1.0 + abs(blend)

        // uniform over the disk: r = √a', θ = πb
        override fun start(a: Double, b: Double, out: DoubleArray) {
            val r = sqrt((a + 1.0) * 0.5)
            val theta = PI * b
            out[0] = r * cos(theta)
            out[1] = r * sin(theta)
        }

        override fun confine(point: DoubleArray) {
            val r = sqrt(point[0] * point[0] + point[1] * point[1])
            if (r > 1.0) {
                point[0] /= r
                point[1] /= r
            }
        }
    }

    companion object {
        private const val PARAM_N = "n"
        private const val PARAM_M = "m"
        private const val PARAM_N2 = "n2"
        private const val PARAM_M2 = "m2"
        private const val PARAM_MIX = "mix"

        private const val DEFAULT_N = 2.0
        private const val DEFAULT_M = 1.0
        private const val DEFAULT_N2 = 0.0
        private const val DEFAULT_M2 = 2.0
        private const val MAX_ORDER = 12
        private const val MAX_CIRCLES = 8
        private const val PROFILE_SAMPLES = 1024
    }
}
