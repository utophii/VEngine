package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.AdvancedMathUtils
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.sin

// energy beam effect based on the cubic centripetal Catmull-Rom spline
// https://en.wikipedia.org/wiki/Centripetal_Catmull%E2%80%93Rom_spline
// P(t) = 0.5 * ((2*P1) + (-P0 + P2)*t + (2*P0 - 5*P1 + 4*P2 - P3)*t^2 + (-P0 + 3*P1 - 3*P2 + P3)*t^3)
// y_wave = sin(progress * freq * 2pi - time * speed) * amplitude
class SplineBeamEffect : AbstractParticleEffect("spline_beam") {

    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> {
        val length = param(opts, PARAM_LENGTH, DEFAULT_LENGTH)
        val arcHeight = param(opts, PARAM_ARC_HEIGHT, DEFAULT_ARC_HEIGHT)
        val points = param(opts, PARAM_POINTS, DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val waveAmplitude = param(opts, PARAM_WAVE_AMPLITUDE, DEFAULT_WAVE_AMPLITUDE)
        val waveFrequency = param(opts, PARAM_WAVE_FREQUENCY, DEFAULT_WAVE_FREQUENCY)
        val waveSpeed = param(opts, PARAM_WAVE_SPEED, DEFAULT_WAVE_SPEED)

        // plotting spline control points around the center
        val world = center.world
        val halfLength = length * HALF
        val p0 = Location(world, center.x - length * P0_RATIO, center.y, center.z)
        val p1 = Location(world, center.x - halfLength, center.y, center.z)
        val p2 = Location(world, center.x, center.y + arcHeight, center.z)
        val p3 = Location(world, center.x + halfLength, center.y, center.z)
        val p4 = Location(world, center.x + length * P0_RATIO, center.y, center.z)

        return List(points) { index ->
            // globalProgress = index / (points - 1): normalized progress along the entire beam length [0, 1]
            val globalProgress = index.toDouble() / (points - 1.0)

            // spline segment selection: first half [P0,P1,P2,P3], second half [P1,P2,P3,P4]
            val splinePoint = if (globalProgress < HALF) {
                // segmentProgress = globalProgress * 2: local parameter t ∈ [0, 1] for the first arc
                val segmentProgress = globalProgress * DOUBLE_SCALE
                AdvancedMathUtils.catmullRom(p0, p1, p2, p3, segmentProgress)
            } else {
                // segmentProgress = (globalProgress - 0.5) * 2: local parameter t ∈ [0, 1] for the second arc
                val segmentProgress = (globalProgress - HALF) * DOUBLE_SCALE
                AdvancedMathUtils.catmullRom(p1, p2, p3, p4, segmentProgress)
            }

            // waveOffset = sin(progress * freq * 2pi - time * speed) * amplitude: running wave pulsation
            val waveOffset = sin(globalProgress * waveFrequency * MathUtils.TAU - time * waveSpeed) * waveAmplitude
            splinePoint.y += waveOffset

            val local = splinePoint.toVector().subtract(center.toVector())
            applyModifiers(
                MathUtils.transform(local, center, opts.scale, opts.rotationYaw, opts.tiltAxis, opts.tiltAngle),
                opts,
                time,
                center
            )
        }
    }

    companion object {
        private const val PARAM_LENGTH = "length"
        private const val PARAM_ARC_HEIGHT = "arcHeight"
        private const val PARAM_POINTS = "points"
        private const val PARAM_WAVE_AMPLITUDE = "waveAmplitude"
        private const val PARAM_WAVE_FREQUENCY = "waveFrequency"
        private const val PARAM_WAVE_SPEED = "waveSpeed"

        private const val DEFAULT_LENGTH = 6.0
        private const val DEFAULT_ARC_HEIGHT = 1.4
        private const val DEFAULT_POINTS = 100.0
        private const val DEFAULT_WAVE_AMPLITUDE = 0.12
        private const val DEFAULT_WAVE_FREQUENCY = 3.0
        private const val DEFAULT_WAVE_SPEED = 0.25
        private const val P0_RATIO = 0.8
        private const val HALF = 0.5
        private const val DOUBLE_SCALE = 2.0
        private const val MIN_POINTS = 6
    }
}