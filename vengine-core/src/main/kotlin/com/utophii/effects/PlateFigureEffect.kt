package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.Hash
import com.utophii.math.ImplicitSurface
import com.utophii.math.ScalarField
import org.bukkit.Location
import kotlin.math.abs

// Sand on a vibrating plate: grains gather on the nodal lines, where the standing wave is zero.
// https://en.wikipedia.org/wiki/Ernst_Chladni#Chladni_figures
// Subclasses give the wave of the plate in plate units u, v; this class lets every grain start at a random spot
// and slide to the nearest nodal line over settle ticks, shaking in proportion to how strongly its current spot
// vibrates. The plate lies in the horizontal XZ plane, the gradient value is the vibration strength where the grain started
abstract class PlateFigureEffect(name: String) : AbstractParticleEffect(name) {

    // the standing wave of one frame
    protected interface Plate {
        // the wave f(u, v); grains settle where it is 0
        fun wave(u: Double, v: Double): Double

        // the largest |f|, so |f| / peak is a 0..1 strength
        val peak: Double

        // a random start on the plate from two numbers in [-1, 1), written as u, v into out
        fun start(a: Double, b: Double, out: DoubleArray)

        // pulls a point that left the plate back onto it
        fun confine(point: DoubleArray)
    }

    // the plate for the current parameters
    protected abstract fun plate(opts: EffectOptions): Plate

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val points = param(opts, PARAM_POINTS, DEFAULT_POINTS).toInt().coerceIn(MIN_POINTS, MAX_POINTS)
        val size = param(opts, PARAM_SIZE, DEFAULT_SIZE)
        val settle = param(opts, PARAM_SETTLE, DEFAULT_SETTLE).coerceAtLeast(MIN_SETTLE)
        val period = param(opts, PARAM_PERIOD, 0.0)
        val jitter = param(opts, PARAM_JITTER, DEFAULT_JITTER)
        val phase = time * param(opts, PARAM_ANGULAR_SPEED, DEFAULT_ANGULAR_SPEED)

        val plate = plate(opts)
        val field = ScalarField { u, v, _ -> plate.wave(u, v) }
        // progress = smoothstep(t / settle), restarting every period ticks when one is set
        val local = if (period > 0.0) time % period else time
        val linear = (local / settle).coerceIn(0.0, 1.0)
        val progress = linear * linear * (3.0 - 2.0 * linear)
        val shakeTick = time.toLong()
        val start = DoubleArray(2)
        val point = DoubleArray(3)

        buffer.acquire(points)
        for (index in 0 until points) {
            val seed = Hash.mix(index.toLong())
            plate.start(Hash.signedUnit(seed), Hash.signedUnit(Hash.mix(seed)), start)
            val startU = start[0]
            val startV = start[1]
            point[0] = startU
            point[1] = startV
            point[2] = 0.0
            ImplicitSurface.project(field, point, PROJECTION_STEPS, MAX_STEP, planar = true)
            plate.confine(point)

            var u = startU + (point[0] - startU) * progress
            var v = startV + (point[1] - startV) * progress
            if (jitter > 0.0) {
                // grains bounce where the plate moves: offset ∝ |f| / peak, a new random direction every tick
                val amplitude = jitter * abs(plate.wave(u, v)) / plate.peak
                val shake = Hash.mix(seed xor shakeTick * SHAKE_SALT)
                u += amplitude * Hash.signedUnit(shake)
                v += amplitude * Hash.signedUnit(Hash.mix(shake))
            }
            writeParticle(buffer, index, u * size, 0.0, v * size, center, opts, time, center, opts.rotationYaw + phase)
            buffer.value(index, abs(plate.wave(startU, startV)) / plate.peak)
        }
    }

    companion object {
        private const val PARAM_POINTS = "points"
        private const val PARAM_SIZE = "size"
        private const val PARAM_SETTLE = "settle"
        private const val PARAM_PERIOD = "period"
        private const val PARAM_JITTER = "jitter"
        private const val PARAM_ANGULAR_SPEED = "angularSpeed"

        private const val DEFAULT_POINTS = 800.0
        private const val DEFAULT_SIZE = 1.5
        private const val DEFAULT_SETTLE = 60.0
        private const val DEFAULT_JITTER = 0.02
        private const val DEFAULT_ANGULAR_SPEED = 0.0
        private const val MIN_POINTS = 1
        private const val MAX_POINTS = 4096
        private const val MIN_SETTLE = 1.0

        private const val PROJECTION_STEPS = 12

        // in plate units (the plate is 2 wide): short enough not to skip over a nodal line
        private const val MAX_STEP = 0.08
        private const val SHAKE_SALT = 0x5DEECE66DL
    }
}
