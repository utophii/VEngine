package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.AdvancedMathUtils
import com.utophii.math.MathUtils
import org.bukkit.Location
import kotlin.math.sin

// energy beam effect based on the cubic centripetal Catmull-Rom spline
// https://en.wikipedia.org/wiki/Centripetal_Catmull%E2%80%93Rom_spline
// P(t) = 0.5 * ((2*P1) + (-P0 + P2)*t + (2*P0 - 5*P1 + 4*P2 - P3)*t^2 + (-P0 + 3*P1 - 3*P2 + P3)*t^3)
// y_wave = sin(progress * freq * 2pi - time * speed) * amplitude
class SplineBeamEffect : AbstractParticleEffect("spline_beam") {

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val length = param(opts, PARAM_LENGTH, DEFAULT_LENGTH)
        val arcHeight = param(opts, PARAM_ARC_HEIGHT, DEFAULT_ARC_HEIGHT)
        val points = param(opts, PARAM_POINTS, DEFAULT_POINTS).toInt().coerceAtLeast(MIN_POINTS)
        val waveAmplitude = param(opts, PARAM_WAVE_AMPLITUDE, DEFAULT_WAVE_AMPLITUDE)
        val waveFrequency = param(opts, PARAM_WAVE_FREQUENCY, DEFAULT_WAVE_FREQUENCY)
        val waveSpeed = param(opts, PARAM_WAVE_SPEED, DEFAULT_WAVE_SPEED)

        // plotting spline control points around the center (computed as scalars, no Location allocation)
        val halfLength = length * HALF
        val p0x = center.x - length * P0_RATIO
        val p1x = center.x - halfLength
        val p2x = center.x
        val p3x = center.x + halfLength
        val p4x = center.x + length * P0_RATIO
        val cx = center.x
        val cy = center.y
        val cz = center.z
        val p2y = center.y + arcHeight

        buffer.acquire(points)
        for (index in 0 until points) {
            // globalProgress = index / (points - 1): normalized progress along the entire beam length [0, 1]
            val globalProgress = index.toDouble() / (points - 1.0)

            // spline segment selection: first half [P0,P1,P2,P3], second half [P1,P2,P3,P4]
            val (sx, sy, sz) = if (globalProgress < HALF) {
                // segmentProgress = globalProgress * 2: local parameter t ∈ [0, 1] for the first arc
                val segmentProgress = globalProgress * DOUBLE_SCALE
                Triple(
                    AdvancedMathUtils.catmullRomComponent(p0x, p1x, p2x, p3x, segmentProgress),
                    AdvancedMathUtils.catmullRomComponent(cy, cy, p2y, cy, segmentProgress),
                    AdvancedMathUtils.catmullRomComponent(cz, cz, cz, cz, segmentProgress),
                )
            } else {
                // segmentProgress = (globalProgress - 0.5) * 2: local parameter t ∈ [0, 1] for the second arc
                val segmentProgress = (globalProgress - HALF) * DOUBLE_SCALE
                Triple(
                    AdvancedMathUtils.catmullRomComponent(p1x, p2x, p3x, p4x, segmentProgress),
                    AdvancedMathUtils.catmullRomComponent(cy, p2y, cy, cy, segmentProgress),
                    AdvancedMathUtils.catmullRomComponent(cz, cz, cz, cz, segmentProgress),
                )
            }

            // waveOffset = sin(progress * freq * 2pi - time * speed) * amplitude: running wave pulsation
            val waveOffset = sin(globalProgress * waveFrequency * MathUtils.TAU - time * waveSpeed) * waveAmplitude
            // local = splineWorld - center, with the wave applied to the vertical offset
            val localX = sx - cx
            val localY = sy - cy + waveOffset
            val localZ = sz - cz
            writeParticle(buffer, index, localX, localY, localZ, center, opts, time, center)
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