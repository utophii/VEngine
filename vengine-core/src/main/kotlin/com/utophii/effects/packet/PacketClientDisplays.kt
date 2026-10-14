package com.utophii.effects.packet

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes
import com.github.retrooper.packetevents.util.Quaternion4f
import com.github.retrooper.packetevents.util.Vector3d
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.PacketWrapper
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import com.utophii.api.DisplayOptions
import com.utophii.api.EffectOptions
import com.utophii.effects.DisplayEntityPool
import com.utophii.effects.PacketDisplays
import com.utophii.effects.ClientDisplays
import com.utophii.engine.EffectScheduler
import com.utophii.engine.FollowAnchor
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

// display models that exist only on the clients.
// Every model is spawned at one reference point and placed by the translation of its transformation
class PacketClientDisplays(
    private val scheduler: EffectScheduler,
    private val origin: Location,
) : ClientDisplays {
    private var vehicle: Entity? = null
    private var vehicleId = NO_VEHICLE
    private var look: DisplayOptions? = null

    @Volatile
    private var ids = IntArray(0)
    private val uuids = ArrayList<UUID>()
    private val sent = ArrayList<Transformation>()

    // client view range sent with the models, grows when the effect spreads further from its reference point
    private var sentViewRange = 0f

    // pixel color sent with the models
    private var sentPixelColor: Int? = null

    // viewer -> world the models were spawned in
    private val viewers = ConcurrentHashMap<UUID, UUID>()

    override fun idsFor(viewer: UUID): IntArray = if (viewers.containsKey(viewer)) ids else EMPTY

    override fun sync(positions: List<Location>, opts: EffectOptions, display: DisplayOptions, time: Double, anchor: FollowAnchor?) {
        val entity = anchor?.entity
        if (entity != null && !entity.isValid) {
            return
        }
        if (vehicle !== entity || look?.let { !DisplayEntityPool.sameLook(it, display) } == true) {
            clear()
        }
        if (entity != null && vehicle == null) {
            vehicle = entity
            vehicleId = entity.entityId
            PacketDisplays.register(vehicleId, this)
        }
        look = display

        val previousCount = ids.size
        val target = positions.size.coerceAtMost(DisplayEntityPool.MAX_DISPLAYS)
        val removed = if (target < previousCount) ids.copyOfRange(target, previousCount) else EMPTY
        resize(target)

        val reference = reference(entity) ?: return
        val frameRotation = anchor?.frameRotation()
        var extentSquared = 0.0
        val transforms = List(target) { index ->
            val position = positions[index]
            val dx = position.x - reference.x
            val dy = position.y - reference.y
            val dz = position.z - reference.z
            extentSquared = maxOf(extentSquared, dx * dx + dy * dy + dz * dz)
            val transform = DisplayEntityPool.modelTransformation(display, opts, index, time, frameRotation)
            transform.translation.add(dx.toFloat(), dy.toFloat(), dz.toFloat())
            transform
        }
        val extent = sqrt(extentSquared)
        val viewRange = viewRange(display, extent)
        val viewRangeGrew = previousCount > 0 && viewRange > sentViewRange
        if (previousCount == 0 || viewRangeGrew) {
            sentViewRange = viewRange
        }
        val pixelColor = if (display.type == DisplayOptions.Type.PIXEL) display.pixelColor(opts) else null
        val pixelColorChanged = previousCount > 0 && pixelColor != sentPixelColor
        sentPixelColor = pixelColor

        val current = if (entity != null) ridingViewers(entity, opts) else nearbyViewers(reference, extent, opts)
        viewers.keys.filter { it !in current }.forEach { uuid ->
            viewers.remove(uuid)
            Bukkit.getPlayer(uuid)?.let { player ->
                send(player, WrapperPlayServerDestroyEntities(*ids))
                if (removed.isNotEmpty()) send(player, WrapperPlayServerDestroyEntities(*removed))
            }
        }

        val interpolation = interpolationTicks(display)
        current.values.forEach { player ->
            val world = player.world.uid
            if (viewers[player.uniqueId] != world) {
                viewers[player.uniqueId] = world
                for (index in 0 until target) spawn(player, index, display, opts, transforms[index], reference)
                entity?.let { sendPassengers(player, it) }
                return@forEach
            }
            if (removed.isNotEmpty()) {
                send(player, WrapperPlayServerDestroyEntities(*removed))
            }
            for (index in previousCount until target) spawn(player, index, display, opts, transforms[index], reference)
            if (entity != null && target != previousCount) {
                sendPassengers(player, entity)
            }
            for (index in 0 until minOf(previousCount, target)) {
                val data = if (transforms[index] != sent[index]) transformData(transforms[index], interpolation) else null
                val range = if (viewRangeGrew) EntityData(INDEX_VIEW_RANGE, EntityDataTypes.FLOAT, sentViewRange) else null
                val color = if (pixelColorChanged && pixelColor != null) EntityData(INDEX_TEXT_BACKGROUND, EntityDataTypes.INT, pixelColor) else null
                if (data != null || range != null || color != null) {
                    send(player, WrapperPlayServerEntityMetadata(ids[index], listOfNotNull(range, color) + data.orEmpty()))
                }
            }
        }

        for (index in 0 until target) {
            if (index < sent.size) sent[index] = transforms[index] else sent.add(transforms[index])
        }
    }

    override fun clear() {
        val all = ids
        if (all.isNotEmpty()) {
            viewers.keys.forEach { uuid -> Bukkit.getPlayer(uuid)?.let { player -> send(player, WrapperPlayServerDestroyEntities(*all)) } }
        }
        viewers.clear()
        if (vehicleId != NO_VEHICLE) {
            PacketDisplays.unregister(vehicleId, this)
        }
        vehicle = null
        vehicleId = NO_VEHICLE
        look = null
        sentViewRange = 0f
        sentPixelColor = null
        ids = EMPTY
        uuids.clear()
        sent.clear()
    }

    // where the models are spawned
    private fun reference(entity: Entity?): Location? {
        if (entity == null) {
            return origin.takeIf { it.world != null }
        }
        val location = entity.location
        location.y += entity.height
        return location
    }

    // the client hides an entity farther than its view range from the camera, measured from the reference point,
    // so the range must also cover how far the models reach from it
    private fun viewRange(display: DisplayOptions, extent: Double): Float =
        display.viewRange ?: ((scheduler.viewDistance + extent) / VANILLA_VIEW_RANGE_BLOCKS).toFloat()

    private fun resize(target: Int) {
        if (target == ids.size) {
            return
        }
        val next = ids.copyOf(target)
        for (index in ids.size until target) {
            next[index] = Bukkit.getUnsafe().nextEntityId()
            uuids.add(UUID.randomUUID())
        }
        while (uuids.size > target) uuids.removeAt(uuids.lastIndex)
        while (sent.size > target) sent.removeAt(sent.lastIndex)
        ids = next
    }

    // players whose client knows the entity, plus the player itself when it is the one being followed
    private fun ridingViewers(entity: Entity, opts: EffectOptions): Map<UUID, Player> {
        val candidates = if (entity is Player) entity.trackedBy + entity else entity.trackedBy
        return allowedViewers(candidates, opts)
    }

    // players of the same world within the view distance of any model, matching the particle culling
    private fun nearbyViewers(reference: Location, extent: Double, opts: EffectOptions): Map<UUID, Player> {
        val world = reference.world ?: return emptyMap()
        val radius = scheduler.viewDistance + extent
        val radiusSquared = radius * radius
        val candidates = world.players.filter { player -> player.location.distanceSquared(reference) <= radiusSquared }
        return allowedViewers(candidates, opts)
    }

    private fun allowedViewers(candidates: Collection<Player>, opts: EffectOptions): Map<UUID, Player> {
        val allowed = opts.receivers.takeIf { it.isNotEmpty() }?.mapTo(HashSet()) { it.uniqueId }
        return candidates
            .filter { player -> player.isOnline && (allowed == null || player.uniqueId in allowed) }
            .associateBy { it.uniqueId }
    }

    private fun spawn(
        player: Player,
        index: Int,
        display: DisplayOptions,
        opts: EffectOptions,
        transform: Transformation,
        reference: Location,
    ) {
        val type = when (display.type) {
            DisplayOptions.Type.ITEM -> EntityTypes.ITEM_DISPLAY
            DisplayOptions.Type.BLOCK -> EntityTypes.BLOCK_DISPLAY
            DisplayOptions.Type.TEXT, DisplayOptions.Type.PIXEL -> EntityTypes.TEXT_DISPLAY
        }
        send(player, WrapperPlayServerSpawnEntity(
            ids[index], Optional.of(uuids[index]), type,
            Vector3d(reference.x, reference.y, reference.z), 0f, 0f, 0f, 0, Optional.empty(),
        ))
        send(player, WrapperPlayServerEntityMetadata(ids[index], lookData(display, index, opts) + transformData(transform, interpolationTicks(display))))
    }

    // passengers packets replace the whole list on the client
    private fun sendPassengers(player: Player, entity: Entity) {
        val real = entity.passengers.map(Entity::getEntityId)
        val virtual = PacketDisplays.virtualPassengers(entity.entityId, player.uniqueId).asList()
        send(player, WrapperPlayServerSetPassengers(entity.entityId, (real + virtual).distinct().toIntArray()))
    }

    private fun transformData(transform: Transformation, interpolation: Int): List<EntityData<*>> {
        val translation = transform.translation
        val scale = transform.scale
        val left = transform.leftRotation
        val right = transform.rightRotation
        return listOf(
            EntityData(INDEX_INTERPOLATION_DELAY, EntityDataTypes.INT, 0),
            EntityData(INDEX_TRANSFORMATION_DURATION, EntityDataTypes.INT, interpolation),
            EntityData(INDEX_TRANSLATION, EntityDataTypes.VECTOR3F, Vector3f(translation.x, translation.y, translation.z)),
            EntityData(INDEX_SCALE, EntityDataTypes.VECTOR3F, Vector3f(scale.x, scale.y, scale.z)),
            EntityData(INDEX_LEFT_ROTATION, EntityDataTypes.QUATERNION, Quaternion4f(left.x, left.y, left.z, left.w)),
            EntityData(INDEX_RIGHT_ROTATION, EntityDataTypes.QUATERNION, Quaternion4f(right.x, right.y, right.z, right.w)),
        )
    }

    // static appearance, mirroring what DisplayEntityPool sets through the Bukkit API
    private fun lookData(display: DisplayOptions, index: Int, opts: EffectOptions): List<EntityData<*>> {
        val brightness = if (display.blockLight != null || display.skyLight != null) {
            ((display.blockLight ?: MAX_LIGHT).coerceIn(0, MAX_LIGHT) shl 4) or ((display.skyLight ?: MAX_LIGHT).coerceIn(0, MAX_LIGHT) shl 20)
        } else {
            NO_BRIGHTNESS
        }
        val data = mutableListOf<EntityData<*>>(
            EntityData(INDEX_FLAGS, EntityDataTypes.BYTE, (if (display.glowColor != null) FLAG_GLOWING else 0).toByte()),
            EntityData(INDEX_BILLBOARD, EntityDataTypes.BYTE, display.resolvedBillboard.ordinal.toByte()),
            EntityData(INDEX_BRIGHTNESS, EntityDataTypes.INT, brightness),
            EntityData(INDEX_VIEW_RANGE, EntityDataTypes.FLOAT, sentViewRange),
            EntityData(INDEX_SHADOW_RADIUS, EntityDataTypes.FLOAT, display.shadowRadius),
            EntityData(INDEX_SHADOW_STRENGTH, EntityDataTypes.FLOAT, display.shadowStrength),
            EntityData(INDEX_GLOW_COLOR, EntityDataTypes.INT, display.glowColor?.asARGB() ?: NO_GLOW_COLOR),
        )
        when (display.type) {
            DisplayOptions.Type.ITEM -> {
                val material = if (display.material.isItem) display.material else Material.AMETHYST_SHARD
                data += EntityData(INDEX_CONTENT, EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(ItemStack(material)))
            }
            DisplayOptions.Type.BLOCK -> {
                val material = if (display.material.isBlock) display.material else Material.AMETHYST_BLOCK
                data += EntityData(INDEX_CONTENT, EntityDataTypes.BLOCK_STATE, SpigotConversionUtil.fromBukkitBlockData(material.createBlockData()).globalId)
            }
            DisplayOptions.Type.PIXEL -> {
                // no default-background flag
                data += EntityData(INDEX_CONTENT, EntityDataTypes.ADV_COMPONENT, Component.text(DisplayEntityPool.PIXEL_TEXT))
                data += EntityData(INDEX_TEXT_BACKGROUND, EntityDataTypes.INT, sentPixelColor ?: display.pixelColor(opts))
                data += EntityData(INDEX_TEXT_FLAGS, EntityDataTypes.BYTE, 0.toByte())
            }
            DisplayOptions.Type.TEXT -> {
                val line = display.text.takeIf { it.isNotEmpty() }?.let { it[index % it.size] } ?: ""
                data += EntityData(INDEX_CONTENT, EntityDataTypes.ADV_COMPONENT, MINI_MESSAGE.deserialize(line))
                display.textBackground?.let { color -> data += EntityData(INDEX_TEXT_BACKGROUND, EntityDataTypes.INT, color.asARGB()) }
                var flags = 0
                if (display.textShadow) flags = flags or TEXT_SHADOW
                if (display.seeThrough) flags = flags or TEXT_SEE_THROUGH
                if (display.textBackground == null) flags = flags or TEXT_DEFAULT_BACKGROUND
                data += EntityData(INDEX_TEXT_FLAGS, EntityDataTypes.BYTE, flags.toByte())
            }
        }
        return data
    }

    private fun interpolationTicks(display: DisplayOptions): Int =
        display.interpolationTicks.coerceIn(0, DisplayOptions.MAX_INTERPOLATION_TICKS)

    // our own packets skip the listeners
    private fun send(player: Player, packet: PacketWrapper<*>) {
        PacketEvents.getAPI().playerManager.sendPacketSilently(player, packet)
    }

    companion object {
        private val EMPTY = IntArray(0)
        private const val NO_VEHICLE = -1
        private val MINI_MESSAGE = MiniMessage.miniMessage()
        private const val VANILLA_VIEW_RANGE_BLOCKS = 64.0
        private const val MAX_LIGHT = 15

        // entity metadata indices of display entities
        private const val INDEX_FLAGS = 0
        private const val INDEX_INTERPOLATION_DELAY = 8
        private const val INDEX_TRANSFORMATION_DURATION = 9
        private const val INDEX_TRANSLATION = 11
        private const val INDEX_SCALE = 12
        private const val INDEX_LEFT_ROTATION = 13
        private const val INDEX_RIGHT_ROTATION = 14
        private const val INDEX_BILLBOARD = 15
        private const val INDEX_BRIGHTNESS = 16
        private const val INDEX_VIEW_RANGE = 17
        private const val INDEX_SHADOW_RADIUS = 18
        private const val INDEX_SHADOW_STRENGTH = 19
        private const val INDEX_GLOW_COLOR = 22
        private const val INDEX_CONTENT = 23
        private const val INDEX_TEXT_BACKGROUND = 25
        private const val INDEX_TEXT_FLAGS = 27

        private const val FLAG_GLOWING = 0x40
        private const val NO_BRIGHTNESS = -1
        private const val NO_GLOW_COLOR = -1
        private const val TEXT_SHADOW = 0x01
        private const val TEXT_SEE_THROUGH = 0x02
        private const val TEXT_DEFAULT_BACKGROUND = 0x04
    }
}
