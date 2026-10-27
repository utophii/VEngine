package com.utophii.shapes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// A closed planar curve as a sum of rotating circles.
// https://en.wikipedia.org/wiki/Discrete_Fourier_transform
// All contours are joined into one loop: the end of each contour jumps to the start of the next one and the last
// jumps back to the first. The loop is resampled at N points z_n = x_n + i y_n, evenly spaced by arc length, and
//   c_k = 1/N Σ z_n e^(-2πi k n / N),   k = -N/2 .. N/2 - 1
// so z(θ) = Σ c_k e^(i k θ) passes through every z_n at θ = 2π n / N. Circle k has radius |c_k| and turns k times
// per loop. The jumps are part of the loop, but their samples are pen-up and never drawn
class FourierSeries private constructor(
    // sample count N
    val samples: Int,
    // frequency k of every term: the constant term first, then by radius, largest first
    val frequencies: IntArray,
    val re: DoubleArray,
    val im: DoubleArray,
    // false for samples on a jump between contours
    val penDown: BooleanArray,
) {
    // rotating circles available
    val maxTerms: Int
        get() = frequencies.size - 1

    // radius |c_j| of term j
    fun radius(term: Int): Double = hypot(re[term], im[term])

    // the curve drawn by the constant term and the first terms circles at every sample angle θ_n = 2π n / N,
    // as x, y per sample
    fun curve(terms: Int): DoubleArray {
        val result = DoubleArray(samples * 2)
        val count = terms.coerceIn(0, maxTerms) + 1
        for (n in 0 until samples) {
            var x = 0.0
            var y = 0.0
            for (term in 0 until count) {
                // c e^(ikθ) = (re + i im)(cos kθ + i sin kθ); k n is reduced mod N, so the twiddle table covers it
                val index = Math.floorMod(frequencies[term].toLong() * n, samples.toLong()).toInt()
                val cos = cosTable[index]
                val sin = sinTable[index]
                x += re[term] * cos - im[term] * sin
                y += re[term] * sin + im[term] * cos
            }
            result[n * 2] = x
            result[n * 2 + 1] = y
        }
        return result
    }

    // cos / sin of 2π m / N
    private val cosTable = DoubleArray(samples) { m -> cos(2 * PI * m / samples) }
    private val sinTable = DoubleArray(samples) { m -> sin(2 * PI * m / samples) }

    companion object {
        // contours: (x, y) polylines
        fun of(contours: List<DoubleArray>, samples: Int): FourierSeries {
            val loop = Loop.of(contours)
            if (loop.length <= 0.0) {
                throw ShapeException("the outline is empty")
            }
            val x = DoubleArray(samples)
            val y = DoubleArray(samples)
            val penDown = BooleanArray(samples)
            loop.resample(samples, x, y, penDown)

            // direct DFT with a twiddle table: O(N²), done once per shape
            val cosTable = DoubleArray(samples) { m -> cos(2 * PI * m / samples) }
            val sinTable = DoubleArray(samples) { m -> sin(2 * PI * m / samples) }
            val half = samples / 2
            val frequencies = IntArray(samples) { index -> index - half }
            val re = DoubleArray(samples)
            val im = DoubleArray(samples)
            frequencies.forEachIndexed { index, k ->
                var sumRe = 0.0
                var sumIm = 0.0
                for (n in 0 until samples) {
                    // z_n e^(-iφ) = (x + iy)(cos φ - i sin φ), φ = 2π k n / N
                    val m = Math.floorMod(k.toLong() * n, samples.toLong()).toInt()
                    sumRe += x[n] * cosTable[m] + y[n] * sinTable[m]
                    sumIm += y[n] * cosTable[m] - x[n] * sinTable[m]
                }
                re[index] = sumRe / samples
                im[index] = sumIm / samples
            }

            val order = frequencies.indices.sortedWith(
                compareBy<Int> { index -> if (frequencies[index] == 0) 0 else 1 }
                    .thenByDescending { index -> hypot(re[index], im[index]) },
            )
            return FourierSeries(
                samples,
                IntArray(samples) { frequencies[order[it]] },
                DoubleArray(samples) { re[order[it]] },
                DoubleArray(samples) { im[order[it]] },
                penDown,
            )
        }
    }

    // the joined loop as segments with a pen flag
    private class Loop(private val segments: DoubleArray, private val drawn: BooleanArray, val length: Double) {

        // samples evenly spaced by arc length; sample n sits at distance n L / N from the start
        fun resample(count: Int, x: DoubleArray, y: DoubleArray, penDown: BooleanArray) {
            var segment = 0
            var segmentStart = 0.0
            for (n in 0 until count) {
                val distance = n * length / count
                while (segment < drawn.size - 1 && segmentStart + segmentLength(segment) < distance) {
                    segmentStart += segmentLength(segment)
                    segment++
                }
                val base = segment * 4
                val segmentLength = segmentLength(segment)
                val t = if (segmentLength > 0.0) ((distance - segmentStart) / segmentLength).coerceIn(0.0, 1.0) else 0.0
                x[n] = segments[base] + (segments[base + 2] - segments[base]) * t
                y[n] = segments[base + 1] + (segments[base + 3] - segments[base + 1]) * t
                penDown[n] = drawn[segment]
            }
        }

        private fun segmentLength(segment: Int): Double {
            val base = segment * 4
            return hypot(segments[base + 2] - segments[base], segments[base + 3] - segments[base + 1])
        }

        companion object {
            fun of(contours: List<DoubleArray>): Loop {
                val segments = ArrayList<Double>()
                val drawn = ArrayList<Boolean>()
                var length = 0.0
                fun add(ax: Double, ay: Double, bx: Double, by: Double, pen: Boolean) {
                    val segmentLength = hypot(bx - ax, by - ay)
                    if (segmentLength <= 0.0) return
                    segments += ax; segments += ay; segments += bx; segments += by
                    drawn += pen
                    length += segmentLength
                }
                contours.forEachIndexed { index, contour ->
                    if (index > 0) {
                        // pen up from the previous contour's end to this contour's start
                        val previous = contours[index - 1]
                        add(previous[previous.size - 2], previous[previous.size - 1], contour[0], contour[1], pen = false)
                    }
                    for (offset in 0 until contour.size - 2 step 2) {
                        add(contour[offset], contour[offset + 1], contour[offset + 2], contour[offset + 3], pen = true)
                    }
                }
                if (contours.isNotEmpty()) {
                    // back to the start, which closes the loop
                    val last = contours.last()
                    add(last[last.size - 2], last[last.size - 1], contours[0][0], contours[0][1], pen = false)
                }
                return Loop(segments.toDoubleArray(), drawn.toBooleanArray(), length)
            }
        }
    }
}
