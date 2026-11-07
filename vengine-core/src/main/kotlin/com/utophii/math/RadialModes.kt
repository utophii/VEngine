package com.utophii.math

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

// bounded cache of normalized radial Bessel profiles
object RadialModes {
    private const val SAMPLES = 1024
    private val profiles = ConcurrentHashMap<Pair<Int, Int>, DoubleArray>()

    fun value(order: Double, band: Double, radius: Double): Double {
        if (!order.isFinite() || !band.isFinite() || !radius.isFinite()) return Double.NaN
        val n = order.roundToInt().coerceIn(0, 12)
        val m = band.roundToInt().coerceIn(if (n == 0) 1 else 0, 8)
        val profile = profiles.computeIfAbsent(n to m) {
            val inner = if (m == 0) 0.0 else Bessel.zero(n, m)
            val k = Bessel.extremum(n, inner, Bessel.zero(n, m + 1))
            val values = DoubleArray(SAMPLES) { index -> Bessel.j(n, k * index / (SAMPLES - 1.0)) }
            val peak = values.maxOf(::abs)
            for (i in values.indices) values[i] /= peak
            values
        }
        val position = radius.coerceIn(0.0, 1.0) * (SAMPLES - 1)
        val index = position.toInt().coerceAtMost(SAMPLES - 2)
        val t = position - index
        return profile[index] + (profile[index + 1] - profile[index]) * t
    }
}
