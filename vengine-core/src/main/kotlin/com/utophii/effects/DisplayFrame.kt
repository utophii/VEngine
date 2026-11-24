package com.utophii.effects

import com.utophii.api.DisplayOptions
import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Transformation
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

// both display backends consume the same positions, centered transforms and ARGB colors
internal class DisplayFrame(
    private val points: List<Location>,
    private val opts: EffectOptions,
    private val display: DisplayOptions,
    private val time: Double,
    private val frameRotation: Quaternionf?,
    private val colors: IntArray?,
    private val sizes: FloatArray?,
) {
    private val baseColor = if (display.usesPixelBackground) display.pixelColor(opts) else 0
    var maxSegmentRadius = 0.0
        private set
    private val segments = if (display.type == DisplayOptions.Type.RIBBON) ribbonSegments() else null
    val count = segments?.size ?: points.size.coerceAtMost(DisplayEntityPool.MAX_DISPLAYS)

    fun position(index: Int): Location = segments?.get(index)?.midpoint ?: points[index]
    fun color(index: Int): Int = segments?.get(index)?.color ?: colors?.get(index) ?: baseColor
    fun transform(index: Int): Transformation = segments?.get(index)?.transform
        ?: DisplayEntityPool.modelTransformation(display, opts, index, time, frameRotation, sizes?.get(index) ?: 1f)

    private data class Segment(val midpoint: Location, val transform: Transformation, val color: Int)

    private fun ribbonSegments(): List<Segment> {
        if (points.size < 2) return emptyList()
        val lengths = DoubleArray(points.lastIndex)
        var total = 0.0
        for (i in lengths.indices) {
            val a = points[i]
            val b = points[i + 1]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val dz = b.z - a.z
            val length = sqrt(dx * dx + dy * dy + dz * dz)
            if (length.isFinite() && length > 0.0 && a.world === b.world) {
                lengths[i] = length
                total += length
            }
        }
        if (total == 0.0) return emptyList()

        val normal = ribbonNormal()
        val result = ArrayList<Segment>(minOf(lengths.size, DisplayEntityPool.MAX_DISPLAYS))
        var distance = 0.0
        for (i in lengths.indices) {
            val length = lengths[i]
            if (length == 0.0) continue
            val t = (distance + length * 0.5) / total
            result.add(ribbonSegment(i, length, t, normal))
            distance += length
            if (result.size == DisplayEntityPool.MAX_DISPLAYS) break
        }
        return result
    }

    private fun ribbonNormal(): Vector3f {
        val local = display.normal.clone().normalize()
        val oriented = MathUtils.transform(local, ORIGIN, 1.0, opts.rotationYaw, opts.tiltAxis, opts.tiltAngle,
            opts.rotationPitch, opts.rotationRoll)
        val normal = Vector3f(oriented.x.toFloat(), oriented.y.toFloat(), oriented.z.toFloat())
        frameRotation?.transform(normal)
        return normal
    }

    private fun ribbonSegment(i: Int, length: Double, t: Double, normal: Vector3f): Segment {
        val a = points[i]
        val b = points[i + 1]
        val tangent = Vector3f(((b.x - a.x) / length).toFloat(), ((b.y - a.y) / length).toFloat(),
            ((b.z - a.z) / length).toFloat())
        val rotation = ribbonRotation(tangent, normal)
        val lifeSize = sizes?.let { (it[i] + it[i + 1]) * 0.5f } ?: 1f
        val width = GradientColors.sampleSize(display.width, t) * lifeSize * abs(display.size.y * opts.scale)
        val transform = DisplayEntityPool.pixelTransformation(length.toFloat(), width.toFloat(), rotation)
        maxSegmentRadius = maxOf(maxSegmentRadius, sqrt(length * length + width * width) * 0.5)
        val midpoint = Location(a.world, (a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5)
        val color = colors?.let { averageColor(it[i], it[i + 1]) } ?: baseColor
        val alpha = ((color ushr 24) * GradientColors.sampleSize(display.opacity, t)).roundToInt()
        return Segment(midpoint, transform, (alpha shl 24) or (color and 0xFFFFFF))
    }

    private fun ribbonRotation(tangent: Vector3f, normal: Vector3f): Quaternionf {
        val across = normal.cross(tangent, Vector3f())
        if (across.lengthSquared() < 1e-8f) {
            // a tangent parallel to the preferred normal needs a different reference axis
            if (abs(tangent.x) < 0.9f) across.set(1f, 0f, 0f) else across.set(0f, 1f, 0f)
            across.cross(tangent)
        }
        across.normalize()
        val facing = tangent.cross(across, Vector3f())
        val basis = Matrix3f().setColumn(0, tangent).setColumn(1, across).setColumn(2, facing)
        return Quaternionf().setFromNormalized(basis)
    }

    // per-channel floor of the average, without carries between ARGB channels
    private fun averageColor(a: Int, b: Int): Int = (a and b) + (((a xor b) ushr 1) and 0x7F7F7F7F)

    private companion object {
        val ORIGIN = Location(null, 0.0, 0.0, 0.0)
    }
}