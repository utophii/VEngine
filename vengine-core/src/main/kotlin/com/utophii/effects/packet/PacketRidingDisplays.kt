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
import com.utophii.effects.RidingDisplays
import com.utophii.engine.EffectScheduler
import com.utophii.engine.FollowAnchor
import io.github.retrooper.packetevents.util.SpigotConversionUtil
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

// display models that exist only on the clients and ride the followed entity as passengers.
// The server never sees them, so they cannot block teleports or collide with anything, and the client
// carries them along with the entity between ticks.
//
// Riding passengers are placed at the vehicle's passenger attachment point, which is the top of the
// bounding box for players and most mobs
class PacketRidingDisplays(private val scheduler: EffectScheduler) : RidingDisplays {
    private var vehicle: Entity? = null
    private var vehicleId = NO_VEHICLE
    private var look: DisplayOptions? = null

    @Volatile
    private var ids = IntArray(0)
    private val uuids = ArrayList<UUID>()
    private val sent = ArrayList<Transformation>()

    // viewer -> world the models were spawned in
    private val viewers = ConcurrentHashMap<UUID, UUID>()

    override fun idsFor(viewer: UUID): IntArray = if (viewers.containsKey(viewer)) ids else EMPTY

    override fun sync(positions: List<Location>, opts: EffectOptions, display: DisplayOptions, time: Double, anchor: FollowAnchor) {
        val entity = anchor.entity
        if (!entity.isValid) {
            return
        }
        if (vehicle !== entity || look?.let { !DisplayEntityPool.sameLook(it, display) } == true) {
            clear()
        }
        if (vehicle == null) {
            vehicle = entity
            vehicleId = entity.entityId
            PacketDisplays.register(vehicleId, this)
        }
        look = display

        val previousCount = ids.size
        val target = positions.size.coerceAtMost(DisplayEntityPool.MAX_DISPLAYS)
        val removed = if (target < previousCount) ids.copyOfRange(target, previousCount) else EMPTY
        resize(target)

        val base = entity.location
        val ridingY = base.y + entity.height
        val frameRotation = anchor.frameRotation()
        val transforms = List(target) { index ->
            val position = positions[index]
            val transform = DisplayEntityPool.modelTransformation(display, opts, index, time, frameRotation)
            transform.translation.add(
                (position.x - base.x).toFloat(),
                (position.y - ridingY).toFloat(),
                (position.z - base.z).toFloat(),
            )
            transform
        }

        val current = currentViewers(entity, opts)
        viewers.keys.filter { it !in current }.forEach { uuid ->
            viewers.remove(uuid)
            Bukkit.getPlayer(uuid)?.let { player -> send(player, WrapperPlayServerDestroyEntities(*ids)) }
            if (removed.isNotEmpty()) Bukkit.getPlayer(uuid)?.let { player -> send(player, WrapperPlayServerDestroyEntities(*removed)) }
        }

        current.values.forEach { player ->
            val world = player.world.uid
            if (viewers[player.uniqueId] != world) {
                viewers[player.uniqueId] = world
                for (index in 0 until target) spawn(player, index, display, opts, transforms[index], base, ridingY)
                sendPassengers(player, entity)
                return@forEach
            }
            if (removed.isNotEmpty()) {
                send(player, WrapperPlayServerDestroyEntities(*removed))
            }
            for (index in previousCount until target) spawn(player, index, display, opts, transforms[index], base, ridingY)
            if (target != previousCount) {
                sendPassengers(player, entity)
            }
            val interpolation = interpolationTicks(display)
            for (index in 0 until minOf(previousCount, target)) {
                if (transforms[index] != sent[index]) {
                    send(player, WrapperPlayServerEntityMetadata(ids[index], transformData(transforms[index], interpolation)))
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
        ids = EMPTY
        uuids.clear()
        sent.clear()
    }

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
    private fun currentViewers(entity: Entity, opts: EffectOptions): Map<UUID, Player> {
        val candidates = if (entity is Player) entity.trackedBy + entity else entity.trackedBy
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
        base: Location,
        ridingY: Double,
    ) {
        val type = when (display.type) {
            DisplayOptions.Type.ITEM -> EntityTypes.ITEM_DISPLAY
            DisplayOptions.Type.BLOCK -> EntityTypes.BLOCK_DISPLAY
            DisplayOptions.Type.TEXT -> EntityTypes.TEXT_DISPLAY
        }
        send(player, WrapperPlayServerSpawnEntity(
            ids[index], Optional.of(uuids[index]), type,
            Vector3d(base.x, ridingY, base.z), 0f, 0f, 0f, 0, Optional.empty(),
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
            EntityData(INDEX_VIEW_RANGE, EntityDataTypes.FLOAT, display.viewRange ?: (scheduler.viewDistance / VANILLA_VIEW_RANGE_BLOCKS).toFloat()),
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
