package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.ImplicitSurface
import com.utophii.math.ScalarField
import com.utophii.math.Hash
import org.bukkit.Location
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

// Chladni figure: sand on a vibrating square plate gathers on the nodal lines, where the plate stays still.
// https://en.wikipedia.org/wiki/Ernst_Chladni#Chladni_figures
// f(u, v) = cos(nπu') cos(mπv') + mix · cos(mπu') cos(nπv'), with u' = (u + 1) / 2 on the plate u, v ∈ [-1, 1].
// mix = -1 and +1 are the two classic free-plate families
class ChladniEffect : AbstractParticleEffect("chladni") {

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val points = param(opts, PARAM_POINTS, DEFAULT_POINTS).toInt().coerceIn(MIN_POINTS, MAX_POINTS)
        val size = param(opts, PARAM_SIZE, DEFAULT_SIZE)
        val n = param(opts, PARAM_N, DEFAULT_N)
        val m = param(opts, PARAM_M, DEFAULT_M)
        val blend = param(opts, PARAM_MIX, DEFAULT_MIX)
        val settle = param(opts, PARAM_SETTLE, DEFAULT_SETTLE).coerceAtLeast(MIN_SETTLE)
        val period = param(opts, PARAM_PERIOD, 0.0)
        val jitter = param(opts, PARAM_JITTER, DEFAULT_JITTER)
        val phase = time * param(opts, PARAM_ANGULAR_SPEED, DEFAULT_ANGULAR_SPEED)

        val field = ScalarField { u, v, _ -> plate(u, v, n, m, blend) }
        // progress = smoothstep(t / settle), restarting every period ticks when one is set
        val local = if (period > 0.0) time % period else time
        val linear = (local / settle).coerceIn(0.0, 1.0)
        val progress = linear * linear * (3.0 - 2.0 * linear)
        val shakeTick = time.toLong()
        val point = DoubleArray(3)

        buffer.acquire(points)
        for (index in 0 until points) {
            val seed = Hash.mix(index.toLong())
            val startU = Hash.signedUnit(seed)
            val startV = Hash.signedUnit(Hash.mix(seed))
            point[0] = startU
            point[1] = startV
            point[2] = 0.0
            ImplicitSurface.project(field, point, PROJECTION_STEPS, MAX_STEP, planar = true)
            val targetU = point[0].coerceIn(-1.0, 1.0)
            val targetV = point[1].coerceIn(-1.0, 1.0)

            var u = startU + (targetU - startU) * progress
            var v = startV + (targetV - startV) * progress
            if (jitter > 0.0) {
                // grains bounce where the plate moves: offset ∝ |f| / 2, a new random direction every tick
                val amplitude = jitter * abs(plate(u, v, n, m, blend)) * HALF
                val shake = Hash.mix(seed xor shakeTick * SHAKE_SALT)
                u += amplitude * Hash.signedUnit(shake)
                v += amplitude * Hash.signedUnit(Hash.mix(shake))
            }
            writeParticle(buffer, index, u * size, 0.0, v * size, center, opts, time, center, opts.rotationYaw + phase)
            buffer.value(index, abs(plate(startU, startV, n, m, blend)) * HALF)
        }
    }

    // standing wave of the plate, in -2..2
    private fun plate(u: Double, v: Double, n: Double, m: Double, blend: Double): Double {
        val x = (u + 1.0) * HALF * PI
        val y = (v + 1.0) * HALF * PI
        return cos(n * x) * cos(m * y) + blend * cos(m * x) * cos(n * y)
    }


    companion object {
        private const val PARAM_POINTS = "points"
        private const val PARAM_SIZE = "size"
        private const val PARAM_N = "n"
        private const val PARAM_M = "m"
        private const val PARAM_MIX = "mix"
        private const val PARAM_SETTLE = "settle"
        private const val PARAM_PERIOD = "period"
        private const val PARAM_JITTER = "jitter"
        private const val PARAM_ANGULAR_SPEED = "angularSpeed"

        private const val DEFAULT_POINTS = 800.0
        private const val DEFAULT_SIZE = 1.5
        private const val DEFAULT_N = 3.0
        private const val DEFAULT_M = 5.0
        private const val DEFAULT_MIX = -1.0
        private const val DEFAULT_SETTLE = 60.0
        private const val DEFAULT_JITTER = 0.02
        private const val DEFAULT_ANGULAR_SPEED = 0.0
        private const val MIN_POINTS = 1
        private const val MAX_POINTS = 4096
        private const val MIN_SETTLE = 1.0

        private const val PROJECTION_STEPS = 12

        // in plate units
        private const val MAX_STEP = 0.08
        private const val HALF = 0.5
        private const val SHAKE_SALT = 0x5DEECE66DL
    }
}
