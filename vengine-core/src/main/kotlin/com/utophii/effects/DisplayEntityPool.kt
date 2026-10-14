package com.utophii.effects

import com.utophii.api.DisplayOptions
import com.utophii.api.EffectOptions
import com.utophii.engine.EffectScheduler
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.joml.Quaternionf
import org.joml.Vector3f

// keeps one display entity per effect point for a single running effect.
// Entities are spawned lazily, moved with client-side interpolation every frame.
class DisplayEntityPool(private val scheduler: EffectScheduler) {
    private val entities = ArrayList<Display>()
    private val lastTransforms = ArrayList<Transformation?>()
    private var lastPositions = DoubleArray(0)
    private var look: DisplayOptions? = null

    // pixel color last applied to the entities
    private var pixelColor: Int? = null
    private val scratch = Location(null, 0.0, 0.0, 0.0)

    // matches the entity set to positions
    // frameRotation turns every model with a followed entity (see [com.utophii.engine.FollowAnchor.frameRotation])
    fun sync(positions: List<Location>, opts: EffectOptions, display: DisplayOptions, time: Double, frameRotation: Quaternionf? = null) {
        val previous = look
        if (previous != null && previous.type != display.type) {
            clear()
        }
        val lookChanged = look?.let { !sameLook(it, display) } ?: false
        look = display

        val target = positions.size.coerceAtMost(MAX_DISPLAYS)
        shrinkTo(target)
        ensurePositionCapacity(target)
        if (lookChanged) {
            entities.forEachIndexed { index, entity -> applyLook(entity, display, index) }
        }
        val color = if (display.type == DisplayOptions.Type.PIXEL) display.pixelColor(opts) else null
        if (color != pixelColor) {
            pixelColor = color
            if (color != null) entities.forEach { entity -> (entity as? TextDisplay)?.backgroundColor = Color.fromARGB(color) }
        }

        val interpolation = display.interpolationTicks.coerceIn(0, DisplayOptions.MAX_INTERPOLATION_TICKS)
        for (index in 0 until target) {
            val position = positions[index]
            val transform = modelTransformation(display, opts, index, time, frameRotation)
            if (index >= entities.size) {
                spawn(position, display, transform, index, opts) ?: return
                continue
            }
            val entity = entities[index]
            if (!entity.isValid) {
                // killed externally
                entity.remove()
                respawn(index, position, display, transform, opts)
                continue
            }
            moveIfChanged(entity, index, position)
            if (transform != lastTransforms[index]) {
                entity.interpolationDuration = interpolation
                entity.interpolationDelay = 0
                entity.transformation = transform
                lastTransforms[index] = transform
            }
        }
    }

    // removes every spawned entity
    fun clear() {
        entities.forEach { entity -> if (entity.isValid) entity.remove() }
        entities.clear()
        lastTransforms.clear()
        look = null
        pixelColor = null
    }

    private fun spawn(position: Location, display: DisplayOptions, transform: Transformation, index: Int, opts: EffectOptions): Display? {
        val entity = create(position, display, transform, index, opts) ?: return null
        entities.add(entity)
        lastTransforms.add(transform)
        rememberPosition(index, position)
        return entity
    }

    private fun respawn(index: Int, position: Location, display: DisplayOptions, transform: Transformation, opts: EffectOptions) {
        val entity = create(position, display, transform, index, opts) ?: return
        entities[index] = entity
        lastTransforms[index] = transform
        rememberPosition(index, position)
    }

    private fun create(position: Location, display: DisplayOptions, transform: Transformation, index: Int, opts: EffectOptions): Display? {
        val world = position.world ?: return null
        val type = when (display.type) {
            DisplayOptions.Type.ITEM -> ItemDisplay::class.java
            DisplayOptions.Type.BLOCK -> BlockDisplay::class.java
            DisplayOptions.Type.TEXT, DisplayOptions.Type.PIXEL -> TextDisplay::class.java
        }
        // everything is configured inside the spawn consumer
        return world.spawn(zeroRotation(position), type) { entity ->
            entity.isPersistent = false
            entity.teleportDuration = display.interpolationTicks.coerceIn(0, DisplayOptions.MAX_INTERPOLATION_TICKS)
            entity.transformation = transform
            applyLook(entity, display, index)
            if (entity is TextDisplay && display.type == DisplayOptions.Type.PIXEL) {
                entity.backgroundColor = Color.fromARGB(display.pixelColor(opts))
            }
            if (opts.receivers.isNotEmpty()) {
                entity.isVisibleByDefault = false
                opts.receivers.forEach { player -> if (player.isOnline) player.showEntity(scheduler.plugin, entity) }
            }
        }
    }

    // applies the static appearance
    private fun applyLook(entity: Display, display: DisplayOptions, index: Int) {
        entity.billboard = display.resolvedBillboard
        entity.viewRange = display.viewRange ?: (scheduler.viewDistance / VANILLA_VIEW_RANGE_BLOCKS).toFloat()
        entity.shadowRadius = display.shadowRadius
        entity.shadowStrength = display.shadowStrength
        entity.brightness = if (display.blockLight != null || display.skyLight != null) {
            Display.Brightness(
                (display.blockLight ?: MAX_LIGHT).coerceIn(0, MAX_LIGHT),
                (display.skyLight ?: MAX_LIGHT).coerceIn(0, MAX_LIGHT),
            )
        } else {
            null
        }
        entity.isGlowing = display.glowColor != null
        entity.glowColorOverride = display.glowColor

        when (entity) {
            is ItemDisplay -> entity.setItemStack(ItemStack(itemMaterial(display.material)))
            is BlockDisplay -> entity.block = blockMaterial(display.material).createBlockData()
            is TextDisplay -> if (display.type == DisplayOptions.Type.PIXEL) {
                entity.text(Component.text(PIXEL_TEXT))
                entity.isDefaultBackground = false
            } else {
                val line = display.text.takeIf { it.isNotEmpty() }?.let { it[index % it.size] } ?: ""
                entity.text(MINI_MESSAGE.deserialize(line))
                entity.isShadowed = display.textShadow
                entity.isSeeThrough = display.seeThrough
                entity.isDefaultBackground = display.textBackground == null
                entity.backgroundColor = display.textBackground
            }
        }
    }

    private fun moveIfChanged(entity: Display, index: Int, position: Location) {
        val base = index * 3
        val moved = lastPositions[base] != position.x || lastPositions[base + 1] != position.y || lastPositions[base + 2] != position.z
        if (moved) {
            entity.teleport(zeroRotation(position))
            rememberPosition(index, position)
        }
    }

    private fun rememberPosition(index: Int, position: Location) {
        val base = index * 3
        lastPositions[base] = position.x
        lastPositions[base + 1] = position.y
        lastPositions[base + 2] = position.z
    }

    // entity yaw/pitch would rotate FIXED models
    private fun zeroRotation(position: Location): Location {
        scratch.world = position.world
        scratch.x = position.x
        scratch.y = position.y
        scratch.z = position.z
        scratch.yaw = 0f
        scratch.pitch = 0f
        return scratch
    }

    private fun shrinkTo(size: Int) {
        while (entities.size > size) {
            val last = entities.removeAt(entities.lastIndex)
            lastTransforms.removeAt(lastTransforms.lastIndex)
            if (last.isValid) last.remove()
        }
    }

    private fun ensurePositionCapacity(size: Int) {
        if (lastPositions.size < size * 3) {
            lastPositions = lastPositions.copyOf(size * 3)
        }
    }

    private fun itemMaterial(material: Material): Material = if (material.isItem) material else Material.AMETHYST_SHARD

    private fun blockMaterial(material: Material): Material = if (material.isBlock) material else Material.AMETHYST_BLOCK

    companion object {
        // hard cap per effect/layer
        const val MAX_DISPLAYS = 256
        private const val VANILLA_VIEW_RANGE_BLOCKS = 64.0
        private const val MAX_LIGHT = 15
        private const val HALF = 0.5f
        private const val PIXEL_SCALE_X = 8f
        private const val PIXEL_SCALE_Y = 4f
        private const val PIXEL_CENTER_X = -0.1f
        private const val PIXEL_CENTER_Y = -0.5f

        // a pixel is a text display whose only glyph is a space, so only its background shows
        const val PIXEL_TEXT = " "
        private val MINI_MESSAGE = MiniMessage.miniMessage()

        // model transform for point index, shared by the server-side and the packet-based pools
        // frameRotation (when following an entity) is applied on top of the model's own rotation
        fun modelTransformation(
            display: DisplayOptions,
            opts: EffectOptions,
            index: Int,
            time: Double,
            frameRotation: Quaternionf? = null,
        ): Transformation {
            // angleTime = t + i * phase: offsets each piece along the spin so the set does not rotate in lockstep
            val angleTime = time + index * display.phase
            val rotation = Quaternionf().rotateXYZ(
                (display.rotation.x + display.spin.x * angleTime).toFloat(),
                (display.rotation.y + display.spin.y * angleTime).toFloat(),
                (display.rotation.z + display.spin.z * angleTime).toFloat(),
            )
            frameRotation?.let { frame -> rotation.premul(frame) }
            val size = Vector3f(
                (display.size.x * opts.scale).toFloat(),
                (display.size.y * opts.scale).toFloat(),
                (display.size.z * opts.scale).toFloat(),
            )
            return when (display.type) {
                // block models span 0..1 from their corner; T = R(S * -0.5) rotates and scales them around their center
                DisplayOptions.Type.BLOCK -> Transformation(rotation.transform(Vector3f(size).mul(-HALF)), rotation, size, Quaternionf())
                // the background of a single space is 1/8 x 1/4 block; S = (8, 4) * edge makes an edge-sized square
                // whose center T = R(-0.1, -0.5) * edge moves onto the entity position
                DisplayOptions.Type.PIXEL -> {
                    val scale = Vector3f(size.x * PIXEL_SCALE_X, size.y * PIXEL_SCALE_Y, 1f)
                    val center = Vector3f(size.x * PIXEL_CENTER_X, size.y * PIXEL_CENTER_Y, 0f)
                    Transformation(rotation.transform(center), rotation, scale, Quaternionf())
                }
                else -> Transformation(Vector3f(), rotation, size, Quaternionf())
            }
        }

        // the static appearance
        internal fun sameLook(a: DisplayOptions, b: DisplayOptions): Boolean =
            a.copy(size = b.size, rotation = b.rotation, spin = b.spin, phase = b.phase, withParticles = b.withParticles) == b
    }
}
