package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.Hash
import org.bukkit.Location
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// boundary of the Julia set of f(z) = z² + c, traced by inverse iteration.
// https://en.wikipedia.org/wiki/Julia_set#Using_backwards_(inverse)_iteration_(IIM)
// The preimages z = ±√(w - c) are pulled towards the set, so a few dozen inverse steps from any start land on it.
// Particle i always takes the same branch sequence.
// The set lies in the vertical XY plane, its gradient value runs along the boundary (colorMode 0) or outwards (colorMode 1)
class JuliaEffect : AbstractParticleEffect("julia") {

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val points = param(opts, PARAM_POINTS, DEFAULT_POINTS).toInt().coerceIn(MIN_POINTS, MAX_POINTS)
        val depth = param(opts, PARAM_DEPTH, DEFAULT_DEPTH).toInt().coerceIn(MIN_DEPTH, MAX_DEPTH)
        val radius = param(opts, PARAM_RADIUS, DEFAULT_RADIUS)
        val radial = param(opts, PARAM_COLOR_MODE, COLOR_ALONG_BOUNDARY) >= COLOR_RADIAL
        // c(t) = c0 + orbit * (cos(ωt), sin(ωt)): moving c continuously deforms the set
        val orbit = param(opts, PARAM_C_ORBIT, 0.0)
        val cPhase = time * param(opts, PARAM_C_SPEED, DEFAULT_C_SPEED)
        val cRe = param(opts, PARAM_C_RE, DEFAULT_C_RE) + orbit * cos(cPhase)
        val cIm = param(opts, PARAM_C_IM, DEFAULT_C_IM) + orbit * sin(cPhase)
        // the whole set fits in |z| <= R = (1 + √(1 + 4|c|)) / 2
        val bound = (1.0 + sqrt(1.0 + 4.0 * sqrt(cRe * cRe + cIm * cIm))) / 2.0
        val spin = time * param(opts, PARAM_ANGULAR_SPEED, DEFAULT_ANGULAR_SPEED)
        val spinCos = cos(spin)
        val spinSin = sin(spin)
        val unit = radius / MODEL_EXTENT

        buffer.acquire(points)
        for (index in 0 until points) {
            val branches = Hash.mix(index.toLong())
            var re = START_RE
            var im = START_IM
            for (step in 0 until depth) {
                // w = z - c, z' = ±√w: one inverse step, the sign picked by bit step of the branch sequence
                val wRe = re - cRe
                val wIm = im - cIm
                val modulus = sqrt(wRe * wRe + wIm * wIm)
                // principal root: √w = (√((|w| + a) / 2), sign(b) √((|w| - a) / 2))
                var rootRe = sqrt(((modulus + wRe) * HALF).coerceAtLeast(0.0))
                var rootIm = sqrt(((modulus - wRe) * HALF).coerceAtLeast(0.0))
                if (wIm < 0.0) rootIm = -rootIm
                if ((branches ushr step) and 1L == 1L) {
                    rootRe = -rootRe
                    rootIm = -rootIm
                }
                re = rootRe
                im = rootIm
            }
            val value = if (radial) {
                sqrt(re * re + im * im) / bound
            } else {
                boundaryPosition(branches, depth)
            }
            // in-plane spin around the plane normal
            val x = (re * spinCos - im * spinSin) * unit
            val y = (re * spinSin + im * spinCos) * unit
            writeParticle(buffer, index, x, y, 0.0, center, opts, time)
            buffer.value(index, value)
        }
    }

    // binary fraction of the last branches taken, most recent first: the last inverse step picks the half
    // of the set, the one before it the half of that half
    private fun boundaryPosition(branches: Long, depth: Int): Double {
        var value = 0.0
        var weight = HALF
        val last = depth - 1
        for (offset in 0 until minOf(COLOR_BITS, depth)) {
            if ((branches ushr (last - offset)) and 1L == 1L) value += weight
            weight *= HALF
        }
        return value
    }


    companion object {
        private const val PARAM_POINTS = "points"
        private const val PARAM_DEPTH = "depth"
        private const val PARAM_RADIUS = "radius"
        private const val PARAM_C_RE = "cRe"
        private const val PARAM_C_IM = "cIm"
        private const val PARAM_C_ORBIT = "cOrbit"
        private const val PARAM_C_SPEED = "cSpeed"
        private const val PARAM_COLOR_MODE = "colorMode"
        private const val PARAM_ANGULAR_SPEED = "angularSpeed"

        private const val DEFAULT_POINTS = 600.0
        private const val DEFAULT_DEPTH = 24.0
        private const val DEFAULT_RADIUS = 1.5
        private const val DEFAULT_C_RE = -0.8
        private const val DEFAULT_C_IM = 0.156
        private const val DEFAULT_C_SPEED = 0.02
        private const val DEFAULT_ANGULAR_SPEED = 0.0
        private const val COLOR_ALONG_BOUNDARY = 0.0
        private const val COLOR_RADIAL = 1.0

        private const val MIN_POINTS = 1
        private const val MAX_POINTS = 4096
        private const val MIN_DEPTH = 1

        // one bit of the 64-bit branch sequence per step
        private const val MAX_DEPTH = 64
        private const val COLOR_BITS = 16

        // |z| = 2 maps to `radius` blocks
        private const val MODEL_EXTENT = 2.0
        private const val START_RE = 1.0
        private const val START_IM = 0.0
        private const val HALF = 0.5
    }
}
