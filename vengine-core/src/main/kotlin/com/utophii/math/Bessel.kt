package com.utophii.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Bessel functions of the first kind for integer orders, accurate to ~1e-12 for |x| <= 60.
// https://en.wikipedia.org/wiki/Bessel_function#Bessel's_integrals
object Bessel {
    // the integrand is periodic, so the trapezoid rule converges exponentially once the step resolves x
    private const val INTEGRATION_STEPS = 96
    private const val ROOT_SCAN_STEP = 0.1
    private const val ROOT_ITERATIONS = 60

    // 1 / φ
    private const val GOLDEN = 0.6180339887498949

    // Jₙ(x) = 1/π ∫₀^π cos(nτ − x sin τ) dτ
    fun j(n: Int, x: Double): Double {
        if (n < 0) {
            // J₋ₙ = (−1)ⁿ Jₙ
            return if (n % 2 == 0) j(-n, x) else -j(-n, x)
        }
        val step = PI / INTEGRATION_STEPS
        // trapezoid: the ends τ = 0 and τ = π get half weight
        var sum = (1.0 + cos(n * PI)) * 0.5
        for (k in 1 until INTEGRATION_STEPS) {
            val tau = k * step
            sum += cos(n * tau - x * sin(tau))
        }
        return sum / INTEGRATION_STEPS
    }

    // the k-th positive zero of Jₙ (k >= 1): scans for a sign change, then bisects
    fun zero(n: Int, k: Int): Double {
        require(k >= 1) { "k must be >= 1" }
        var found = 0
        // every zero of Jₙ lies beyond x = n, and below that Jₙ is so small that rounding could fake sign changes
        var a = maxOf(ROOT_SCAN_STEP, n.toDouble())
        var fa = j(n, a)
        while (true) {
            val b = a + ROOT_SCAN_STEP
            val fb = j(n, b)
            if (fa == 0.0 || fa * fb < 0.0) {
                found++
                if (found == k) return bisect(a, b) { j(n, it) }
            }
            a = b
            fa = fb
        }
    }

    // where |Jₙ| peaks between two neighbouring zeros a < b of Jₙ (or 0 and the first zero), i.e. the zero of Jₙ'
    // there (Rolle's theorem). |Jₙ| has a single hump on (a, b), so a golden-section search finds it without
    // reading the sign of Jₙ', which is lost in rounding near 0 for high orders
    fun extremum(n: Int, a: Double, b: Double): Double {
        var low = a
        var high = b
        var left = high - GOLDEN * (high - low)
        var right = low + GOLDEN * (high - low)
        var fLeft = abs(j(n, left))
        var fRight = abs(j(n, right))
        repeat(ROOT_ITERATIONS) {
            if (fLeft < fRight) {
                low = left
                left = right
                fLeft = fRight
                right = low + GOLDEN * (high - low)
                fRight = abs(j(n, right))
            } else {
                high = right
                right = left
                fRight = fLeft
                left = high - GOLDEN * (high - low)
                fLeft = abs(j(n, left))
            }
        }
        return (low + high) * 0.5
    }

    private inline fun bisect(start: Double, end: Double, f: (Double) -> Double): Double {
        var a = start
        var b = end
        var fa = f(a)
        repeat(ROOT_ITERATIONS) {
            val middle = (a + b) * 0.5
            val fm = f(middle)
            if (fm == 0.0) return middle
            if (fa * fm < 0.0) {
                b = middle
            } else {
                a = middle
                fa = fm
            }
            if (abs(b - a) < 1e-14) return (a + b) * 0.5
        }
        return (a + b) * 0.5
    }
}
