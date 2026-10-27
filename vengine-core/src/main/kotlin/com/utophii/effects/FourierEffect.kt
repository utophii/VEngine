package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.FollowDefaults
import com.utophii.shapes.FourierSeries
import com.utophii.shapes.ShapeVariants
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

// An outline drawn by a chain of epicycles, see FourierSeries.
// The chain turns once every period ticks: θ = 2π t / period. The pen at the end of the chain leaves a trace.
// Trail is the share of the loop that stays visible behind the pen.
// Three parts can be shown separately, so layers can color them differently: trace, arms
// and circles. Trace values run along the loop, arm and circle values run from the
// largest circle (0) to the smallest (1)
class FourierEffect(
    name: String,
    private val series: ShapeVariants<FourierSeries>,
    private val defaultEpicycles: Int,
    private val periodTicks: Double,
    private val trail: Double,
    private val spacing: Double,
    private val angularSpeed: Double,
    override val followDefaults: FollowDefaults? = null,
) : AbstractParticleEffect(name), ConfiguredPrimitive {

    // reconstructed curves per series and epicycle count
    private val curves = ConcurrentHashMap<CurveKey, DoubleArray>()

    override fun playParameters(configured: Map<String, Double>, subject: Player?): Map<String, Double> =
        series.playParameters(configured, subject)

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val fourier = series.resolve(opts)
        val terms = param(opts, PARAM_EPICYCLES, defaultEpicycles.toDouble()).toInt().coerceIn(1, fourier.maxTerms)
        val period = param(opts, PARAM_PERIOD, periodTicks).coerceAtLeast(1.0)
        val trailShare = param(opts, PARAM_TRAIL, trail).coerceIn(0.0, 1.0)
        val gap = param(opts, PARAM_SPACING, spacing).coerceAtLeast(MIN_SPACING)
        val showTrace = param(opts, PARAM_TRACE, 1.0) > 0.0
        val showArms = param(opts, PARAM_ARMS, 1.0) > 0.0
        val showCircles = param(opts, PARAM_CIRCLES, 1.0) > 0.0
        val phase = time * param(opts, PARAM_ANGULAR_SPEED, angularSpeed)
        val yaw = opts.rotationYaw + phase

        val samples = fourier.samples
        val turns = time / period
        // samples passed by the pen so far, and the window of them still on screen
        val head = (turns * samples).toLong()
        val window = if (trailShare >= 1.0) samples.toLong() else (trailShare * samples).toLong()
        val firstSample = maxOf(0L, head - window + 1)
        val curve = if (showTrace) curve(fourier, terms) else null

        // pass 1: count the particles so the buffer is sized once
        var traceCount = 0
        if (curve != null) {
            for (sample in firstSample..head) {
                if (fourier.penDown[(sample % samples).toInt()]) traceCount++
            }
        }
        val theta = 2 * PI * turns
        var machineryCount = 0
        for (term in 1..terms) {
            val radius = fourier.radius(term)
            if (showArms) machineryCount += armPoints(radius, gap)
            if (showCircles) machineryCount += circlePoints(radius, gap)
            if (machineryCount >= MAX_MACHINERY_POINTS) break
        }
        machineryCount = machineryCount.coerceAtMost(MAX_MACHINERY_POINTS)

        buffer.acquire(traceCount + machineryCount)
        var index = 0
        if (curve != null) {
            for (sample in firstSample..head) {
                val n = (sample % samples).toInt()
                if (!fourier.penDown[n]) continue
                writeParticle(buffer, index, curve[n * 2], curve[n * 2 + 1], 0.0, center, opts, time, center, yaw)
                buffer.value(index, n.toDouble() / samples)
                index++
            }
        }

        // walk the chain: every circle is centered where the previous arm ends
        var centerX = fourier.re[0]
        var centerY = fourier.im[0]
        val limit = traceCount + machineryCount
        for (term in 1..terms) {
            if (index >= limit) break
            val radius = fourier.radius(term)
            // c e^(ikθ)
            val angle = fourier.frequencies[term] * theta
            val cos = cos(angle)
            val sin = sin(angle)
            val armX = fourier.re[term] * cos - fourier.im[term] * sin
            val armY = fourier.re[term] * sin + fourier.im[term] * cos
            val value = (term - 1).toDouble() / (terms - 1).coerceAtLeast(1)
            if (showCircles) {
                val points = circlePoints(radius, gap)
                for (point in 0 until points) {
                    if (index >= limit) break
                    val around = 2 * PI * point / points
                    writeParticle(
                        buffer, index, centerX + radius * cos(around), centerY + radius * sin(around), 0.0,
                        center, opts, time, center, yaw,
                    )
                    buffer.value(index++, value)
                }
            }
            if (showArms) {
                val points = armPoints(radius, gap)
                for (point in 0 until points) {
                    if (index >= limit) break
                    val t = point.toDouble() / points
                    writeParticle(buffer, index, centerX + armX * t, centerY + armY * t, 0.0, center, opts, time, center, yaw)
                    buffer.value(index++, value)
                }
            }
            centerX += armX
            centerY += armY
        }
    }

    private fun curve(fourier: FourierSeries, terms: Int): DoubleArray {
        val key = CurveKey(fourier, terms)
        return curves[key] ?: fourier.curve(terms).also { curve ->
            // an animated epicycle count or many player texts could otherwise grow the cache without bound
            if (curves.size >= MAX_CACHED_CURVES) curves.clear()
            curves[key] = curve
        }
    }

    // the arm's start is its point 0, its end is the next arm's start
    private fun armPoints(radius: Double, gap: Double): Int = ceil(radius / gap).toInt().coerceAtLeast(1)

    // circles smaller than the particle spacing are left out
    private fun circlePoints(radius: Double, gap: Double): Int {
        val points = ceil(2 * PI * radius / gap).toInt()
        return if (points < MIN_CIRCLE_POINTS) 0 else points
    }

    // identity of the series: the same object for the same text
    private data class CurveKey(val series: FourierSeries, val terms: Int)

    companion object {
        private const val PARAM_EPICYCLES = "epicycles"
        private const val PARAM_PERIOD = "period"
        private const val PARAM_TRAIL = "trail"
        private const val PARAM_SPACING = "spacing"
        private const val PARAM_TRACE = "trace"
        private const val PARAM_ARMS = "arms"
        private const val PARAM_CIRCLES = "circles"
        private const val PARAM_ANGULAR_SPEED = "angularSpeed"

        private const val MIN_SPACING = 0.01
        private const val MIN_CIRCLE_POINTS = 6
        private const val MAX_MACHINERY_POINTS = 2048
        private const val MAX_CACHED_CURVES = 16
    }
}
