package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.FollowDefaults
import com.utophii.shapes.PointCloud
import com.utophii.shapes.ShapeVariants
import org.bukkit.Location
import org.bukkit.entity.Player

// An imported shape (picture, text, OBJ model, SVG outline) as a fixed point cloud.
// The cloud is built once, when the effect loads (or on first use for each resolved placeholder text).
// The shape appears point by point over growth ticks, in the order of its points: along the outline,
// line by line for fills, scattered for pictures. With sourceColors the particles take the colors of the source
// (picture pixels), otherwise the gradient picks them by the point values
class ShapeEffect(
    name: String,
    private val shape: ShapeVariants<PointCloud>,
    private val growthTicks: Double,
    private val angularSpeed: Double,
    private val sourceColors: Boolean,
    override val followDefaults: FollowDefaults? = null,
) : AbstractParticleEffect(name), ConfiguredPrimitive {

    // particles of the shape as loaded
    val pointCount: Int
        get() = shape.base.count

    override fun playParameters(configured: Map<String, Double>, subject: Player?): Map<String, Double> =
        shape.playParameters(configured, subject)

    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val cloud = shape.resolve(opts)
        // progress = t / growth, or set directly
        val growth = opts.parameters[GROWTH_PARAM] ?: growthTicks
        val progress = (opts.parameters[PROGRESS_PARAM] ?: if (growth > 0.0) time / growth else 1.0).coerceIn(0.0, 1.0)
        val phase = time * (opts.parameters[ANGULAR_SPEED_PARAM] ?: angularSpeed)
        val colors = cloud.colors.takeIf { sourceColors }

        val visible = (progress * cloud.count).toInt()
        buffer.acquire(visible)
        for (index in 0 until visible) {
            val offset = index * 3
            writeParticle(
                buffer, index, cloud.points[offset], cloud.points[offset + 1], cloud.points[offset + 2],
                center, opts, time, center, opts.rotationYaw + phase,
            )
            buffer.value(index, cloud.values[index])
            if (colors != null) buffer.color(index, colors[index])
        }
    }

    companion object {
        private const val GROWTH_PARAM = "growth"
        private const val PROGRESS_PARAM = "progress"
        private const val ANGULAR_SPEED_PARAM = "angularSpeed"
    }
}
