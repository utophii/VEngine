package com.utophii.api

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.util.Vector

/**
 * Renders an effect with display entities instead of (or together with) particles.
 * One display entity is placed at every point the effect calculates, then teleported and
 * re-transformed each frame with client-side interpolation, so the shape reads as solid geometry:
 * crystals (item), rune stones (block), holograms and glyphs (text).
 *
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
 * @property textBackground ARGB background for TEXT, or null for the vanilla default
 * @property textShadow draws text with a drop shadow
 * @property seeThrough TEXT is visible through blocks
 * @property withParticles also spawn the layer's particles at the same points
 */
data class DisplayOptions(
    val type: Type = Type.ITEM,
    val material: Material = Material.AMETHYST_SHARD,
    val text: List<String> = emptyList(),
    val size: Vector = Vector(DEFAULT_SIZE, DEFAULT_SIZE, DEFAULT_SIZE),
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
) {
    enum class Type { ITEM, BLOCK, TEXT }

    // billboard actually applied to spawned entities
    val resolvedBillboard: Display.Billboard
        get() = billboard ?: if (type == Type.TEXT) Display.Billboard.CENTER else Display.Billboard.FIXED

    companion object {
        const val DEFAULT_SIZE = 0.5
        const val DEFAULT_INTERPOLATION_TICKS = 2
        const val MAX_INTERPOLATION_TICKS = 59
        const val DEFAULT_SHADOW_STRENGTH = 1.0f
    }
}
