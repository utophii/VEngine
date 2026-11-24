package com.utophii.api

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.util.Vector

/**
 * Renders an effect with display entities instead of (or together with) particles.
 * Point models are placed at each sample and ribbons span consecutive samples. Models are moved and
 * re-transformed each frame with client-side interpolation, so the shape reads as solid geometry:
 * crystals (item), rune stones (block), holograms and glyphs (text), and flat colored squares (pixel) that
 * look like dust particles but move smoothly.
 *
 * A PIXEL is a text display showing a single space on a colored background. Its color is the layer's
 * [EffectOptions.color] (animatable with the `color.r/g/b` tracks), falling back to [textBackground],
 * then white; [size] is the edge of the square in blocks, so `display.size` tracks animate it.
 *
 * @property width ribbon width stops in blocks, multiplied by the Y component of [size]
 * @property opacity ribbon opacity stops from 0 to 1, multiplied by the background alpha
 * @property normal preferred local surface normal of the ribbon
 * @property type which display entity to spawn
 * @property material item model for ITEM, block state for BLOCK
 * @property text MiniMessage lines for TEXT; point `i` shows `text[i % text.size]`
 * @property size per-axis model scale in blocks, multiplied by [EffectOptions.scale]
 * @property rotation base local rotation in radians (applied X, then Y, then Z)
 * @property spin local angular velocity in radians per tick around X, Y and Z
 * @property phase spin offset in ticks between consecutive points, so pieces do not turn in lockstep
 * @property billboard how the model faces the viewer; null picks FIXED for item/block and CENTER for text
 * @property blockLight forced block light 0..15, or null to use the world light
 * @property skyLight forced sky light 0..15, or null to use the world light
 * @property glowColor outline glow color, or null for no glow
 * @property interpolationTicks client interpolation length for movement and transformation (0..59)
 * @property viewRange render distance multiplier (1.0 = 64 blocks), or null to follow the engine view distance
 * @property shadowRadius / shadowStrength ground shadow under each entity
 * @property textBackground ARGB background for TEXT (null = vanilla default), or the fallback color of a PIXEL
 * @property textShadow draws text with a drop shadow
 * @property seeThrough TEXT is visible through blocks
 * @property withParticles also spawn the layer's particles at the same points
 */
@OptIn(ExperimentalVersionOverloading::class)
data class DisplayOptions(
    val type: Type = Type.ITEM,
    val material: Material = Material.AMETHYST_SHARD,
    val text: List<String> = emptyList(),
    val size: Vector = if (type == Type.RIBBON) Vector(1, 1, 1) else Vector(DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE),
    val rotation: Vector = Vector(),
    val spin: Vector = Vector(),
    val phase: Double = 0.0,
    val billboard: Display.Billboard? = null,
    val blockLight: Int? = null,
    val skyLight: Int? = null,
    val glowColor: Color? = null,
    val interpolationTicks: Int = DEFAULT_INTERPOLATION_TICKS,
    val viewRange: Float? = null,
    val shadowRadius: Float = 0.0f,
    val shadowStrength: Float = DEFAULT_SHADOW_STRENGTH,
    val textBackground: Color? = null,
    val textShadow: Boolean = false,
    val seeThrough: Boolean = false,
    val withParticles: Boolean = false,
    @IntroducedAt("0.1.2") val width: List<Float> = listOf(DEFAULT_PIXEL_SIZE.toFloat()),
    @IntroducedAt("0.1.2") val opacity: List<Float> = listOf(1f),
    @IntroducedAt("0.1.2") val normal: Vector = Vector(0, 1, 0),
) {
    enum class Type { ITEM, BLOCK, TEXT, PIXEL, RIBBON }

    init {
        if (type == Type.RIBBON) {
            require(width.isNotEmpty() && width.all { it.isFinite() && it >= 0f }) { "Ribbon widths must be finite and nonnegative" }
            require(opacity.isNotEmpty() && opacity.all { it in 0f..1f }) { "Ribbon opacity must be between 0 and 1" }
            require(normal.lengthSquared().let { it.isFinite() && it > 0.0 }) { "Ribbon normal must be finite and nonzero" }
        }
    }

    val usesPixelBackground: Boolean
        get() = type == Type.PIXEL || type == Type.RIBBON

    // billboard actually applied to spawned entities
    val resolvedBillboard: Display.Billboard
        get() = when (type) {
            Type.RIBBON -> Display.Billboard.FIXED
            Type.TEXT, Type.PIXEL -> billboard ?: Display.Billboard.CENTER
            else -> billboard ?: Display.Billboard.FIXED
        }

    // ARGB color of a PIXEL for the given layer options
    fun pixelColor(opts: EffectOptions): Int =
        opts.color?.asARGB() ?: textBackground?.asARGB() ?: Color.WHITE.asARGB()

    companion object {
        const val DEFAULT_SIZE = 0.5

        // default edge of a PIXEL in blocks, close to a dust particle
        const val DEFAULT_PIXEL_SIZE = 0.15
        const val DEFAULT_INTERPOLATION_TICKS = 2
        const val MAX_INTERPOLATION_TICKS = 59
        const val DEFAULT_SHADOW_STRENGTH = 1.0f
    }
}
