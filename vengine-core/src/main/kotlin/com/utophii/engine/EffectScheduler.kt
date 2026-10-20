package com.utophii.engine

import com.utophii.api.EffectHandle
import com.utophii.api.EffectOptions
import com.utophii.api.SimpleEffectHandle
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// owns Bukkit task scheduling, active handles and frame synchronization for VEngine effects
class EffectScheduler(val plugin: JavaPlugin) {

    private val tasks: MutableSet<BukkitTask> = ConcurrentHashMap.newKeySet()
    private val activeHandles = ConcurrentHashMap<String, EffectHandle>()
    private val running = ConcurrentHashMap<String, RunningEffect>()
    private val anchors = ConcurrentHashMap<String, FollowAnchor>()
    private val idCounter = AtomicInteger(1)

    // view-distance culling radius in blocks; particles are only sent to players within this radius of the particle location
    var viewDistance: Double = DEFAULT_VIEW_DISTANCE_BLOCKS
        get() = field
        set(value) { field = value.coerceAtLeast(MIN_VIEW_DISTANCE_BLOCKS) }

    // squared view distance for cheap squared-distance comparisons
    private val viewDistanceSquared: Double
        get() = viewDistance * viewDistance

    // schedules frame calculation asynchronously while rendering strictly monotonically on the main thread
    // when a computed frame is dropped (stale, cancelled, or after rendering) [release] disposes it exactly once
    // onStop runs once on the server thread when the effect finishes or is cancelled
    // keepAlive is checked on the server thread every tick
    fun <T> scheduleFrames(
        effectName: String,
        initialDelayTicks: Long = INITIAL_DELAY_TICKS,
        durationTicks: Long,
        calculate: (Double) -> T,
        render: (Double, T) -> Unit,
        release: ((T) -> Unit)? = null,
        origin: Location? = null,
        onStop: (() -> Unit)? = null,
        keepAlive: (() -> Boolean)? = null,
        anchor: FollowAnchor? = null,
    ): EffectHandle {
        val handleId = "$effectName#${idCounter.getAndIncrement()}"
        var tick = 0L
        val lastDeliveredFrame = AtomicLong(UNINITIALIZED_FRAME_INDEX)
        lateinit var task: BukkitTask

        val handle = SimpleEffectHandle(handleId, effectName) {
            task.cancel()
            tasks.remove(task)
            forget(handleId)
            onStop?.let(::runOnMainThread)
        }

        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (tick >= durationTicks || handle.isCancelled || keepAlive?.invoke() == false) {
                handle.cancel()
                return@Runnable
            }

            val currentFrameIndex = tick
            val currentTime = currentFrameIndex.toDouble()
            tick++

            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                if (handle.isCancelled || task.isCancelled || task !in tasks) {
                    return@Runnable
                }

                val frameResult = calculate(currentTime)

                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (handle.isCancelled || task.isCancelled || task !in tasks) {
                        release?.invoke(frameResult)
                        return@Runnable
                    }

                    val previousDelivered = lastDeliveredFrame.get()
                    if (currentFrameIndex > previousDelivered) {
                        lastDeliveredFrame.set(currentFrameIndex)
                        try {
                            render(currentTime, frameResult)
                        } finally {
                            release?.invoke(frameResult)
                        }
                    } else {
                        release?.invoke(frameResult)
                    }
                })
            })
        }, initialDelayTicks, FRAME_PERIOD_TICKS)

        tasks.add(task)
        activeHandles[handleId] = handle
        track(handle, origin, anchor)
        return handle
    }

    // registers a composite or custom handle in the active handle registry
    fun registerHandle(handle: EffectHandle) {
        activeHandles[handle.id] = handle
    }

    // registers a composite handle and automatically unregisters it after ttlTicks,
    // so scripted effects that finished naturally do not linger in the active handle listing.
    // origin is the spawn point placeholders use
    fun registerComposite(handle: EffectHandle, ttlTicks: Long, origin: Location? = null, anchor: FollowAnchor? = null) {
        activeHandles[handle.id] = handle
        track(handle, origin, anchor)
        if (ttlTicks >= EffectOptions.INFINITE_DURATION) {
            return
        }
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            // any straggler children cancel themselves and leave the registry through their own callbacks
            handle.cancel()
            forget(handle.id)
        }, ttlTicks.coerceAtLeast(MIN_COMPOSITE_TTL_TICKS))
    }

    // removes a handle from the listing without stopping it
    fun detach(id: String) {
        forget(id)
    }

    // stops a running effect by handle ID
    fun stop(id: String): Boolean {
        val handle = activeHandles[id] ?: activeHandles.entries.firstOrNull { it.key.equals(id, ignoreCase = true) }?.value
        return if (handle != null) {
            handle.cancel()
            forget(handle.id)
            true
        } else {
            false
        }
    }

    // stops all active running effects
    fun stopAll(): Int {
        val count = activeHandles.size
        activeHandles.values.toList().forEach(EffectHandle::cancel)
        activeHandles.clear()
        running.clear()
        anchors.clear()
        cancelAllTasks()
        return count
    }

    // returns a snapshot of active effect handles
    fun activeHandles(): List<EffectHandle> = activeHandles.values.toList()

    // returns a snapshot of user-facing spawned effects, including their spawn point
    // followed effects report the last sampled position of their entity instead of the spawn point
    fun runningEffects(): List<RunningEffect> = running.values.map { effect ->
        val position = anchors[effect.id]?.position() ?: return@map effect
        val worldName = position.world?.name ?: return@map effect
        effect.copy(worldName = worldName, x = position.x, y = position.y, z = position.z)
    }

    // remembers a spawned effect's origin so placeholders can answer "what is playing, and where"
    private fun track(handle: EffectHandle, origin: Location?, anchor: FollowAnchor? = null) {
        if (origin == null) {
            return
        }
        anchor?.let { anchors[handle.id] = it }
        val worldName = origin.world?.name ?: return
        running[handle.id] = RunningEffect(
            id = handle.id,
            effectName = handle.effectName,
            worldName = worldName,
            x = origin.x,
            y = origin.y,
            z = origin.z,
        )
    }

    // drops a handle from both the task registry and the placeholder snapshot
    private fun forget(id: String) {
        activeHandles.remove(id)
        running.remove(id)
        anchors.remove(id)
    }

    // spawn point of one user-facing effect. Coordinates are copied so later Location mutation cannot move the snapshot
    data class RunningEffect(
        val id: String,
        val effectName: String,
        val worldName: String,
        val x: Double,
        val y: Double,
        val z: Double,
    )

    // spawns a particle only to players within the configured view distance of the particle location
    // explicit receivers are filtered by distance; when none are given the particle is broadcast only to nearby online players in the same world
    fun spawnParticle(
        location: Location,
        particle: Particle,
        data: Any?,
        receivers: Iterable<Player>,
        count: Int = DEFAULT_PARTICLE_COUNT,
        offsetX: Double = DEFAULT_PARTICLE_OFFSET,
        offsetY: Double = DEFAULT_PARTICLE_OFFSET,
        offsetZ: Double = DEFAULT_PARTICLE_OFFSET,
        speed: Double = DEFAULT_PARTICLE_SPEED,
    ) {
        spawnParticles(listOf(location), particle, data, receivers, count, offsetX, offsetY, offsetZ, speed)
    }

    // spawns one frame of particles with the same culling as spawnParticle.
    // Nearby players are resolved once per frame: candidates are pre-culled against the frame's bounding box,
    // then each particle is checked against cached player coordinates
    fun spawnParticles(
        locations: List<Location>,
        particle: Particle,
        data: Any?,
        receivers: Iterable<Player>,
        count: Int = DEFAULT_PARTICLE_COUNT,
        offsetX: Double = DEFAULT_PARTICLE_OFFSET,
        offsetY: Double = DEFAULT_PARTICLE_OFFSET,
        offsetZ: Double = DEFAULT_PARTICLE_OFFSET,
        speed: Double = DEFAULT_PARTICLE_SPEED,
    ) {
        if (locations.isEmpty()) {
            return
        }
        val explicitReceivers = receivers.toList()
        var viewers: FrameViewers? = null
        locations.forEach { location ->
            val world = location.world ?: return@forEach
            val current = viewers?.takeIf { it.world == world }
                ?: FrameViewers.collect(world, locations, explicitReceivers, viewDistance).also { viewers = it }
            current.forEachNear(location, viewDistanceSquared) { player ->
                player.spawnParticle(
                    particle,
                    location,
                    count,
                    offsetX,
                    offsetY,
                    offsetZ,
                    speed,
                    data,
                )
            }
        }
    }

    // online players of one world that may see any particle of the current frame, with their positions sampled once
    private class FrameViewers(
        val world: World,
        private val players: List<Player>,
        private val coordinates: DoubleArray,
    ) {
        inline fun forEachNear(location: Location, radiusSquared: Double, action: (Player) -> Unit) {
            val x = location.x
            val y = location.y
            val z = location.z
            for (index in players.indices) {
                val dx = coordinates[index * AXES] - x
                val dy = coordinates[index * AXES + 1] - y
                val dz = coordinates[index * AXES + 2] - z
                if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                    action(players[index])
                }
            }
        }

        companion object {
            private const val AXES = 3

            // keeps the candidates within radius of the bounding box of the frame's particles in this world
            fun collect(world: World, locations: List<Location>, explicitReceivers: List<Player>, radius: Double): FrameViewers {
                var minX = Double.POSITIVE_INFINITY
                var minY = Double.POSITIVE_INFINITY
                var minZ = Double.POSITIVE_INFINITY
                var maxX = Double.NEGATIVE_INFINITY
                var maxY = Double.NEGATIVE_INFINITY
                var maxZ = Double.NEGATIVE_INFINITY
                locations.forEach { location ->
                    if (location.world != world) return@forEach
                    minX = minOf(minX, location.x); maxX = maxOf(maxX, location.x)
                    minY = minOf(minY, location.y); maxY = maxOf(maxY, location.y)
                    minZ = minOf(minZ, location.z); maxZ = maxOf(maxZ, location.z)
                }

                val candidates = explicitReceivers.ifEmpty { world.players }
                val players = ArrayList<Player>(candidates.size)
                val coordinates = DoubleArray(candidates.size * AXES)
                val radiusSquared = radius * radius
                candidates.forEach { player ->
                    if (!player.isOnline || player.world != world) return@forEach
                    val position = player.location
                    val dx = axisGap(position.x, minX, maxX)
                    val dy = axisGap(position.y, minY, maxY)
                    val dz = axisGap(position.z, minZ, maxZ)
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) return@forEach
                    val offset = players.size * AXES
                    coordinates[offset] = position.x
                    coordinates[offset + 1] = position.y
                    coordinates[offset + 2] = position.z
                    players.add(player)
                }
                return FrameViewers(world, players, coordinates)
            }

            // distance from value to the [min, max] interval along one axis
            private fun axisGap(value: Double, min: Double, max: Double): Double = when {
                value < min -> min - value
                value > max -> value - max
                else -> 0.0
            }
        }
    }

    // runs action on the server thread
    fun runOnMainThread(action: () -> Unit) {
        if (plugin.server.isPrimaryThread) {
            action()
        } else if (plugin.isEnabled) {
            plugin.server.scheduler.runTask(plugin, Runnable(action))
        }
    }

    private fun cancelAllTasks() {
        tasks.forEach(BukkitTask::cancel)
        tasks.clear()
    }

    fun cancelAll() {
        stopAll()
    }

    companion object {
        private const val INITIAL_DELAY_TICKS = 0L
        private const val FRAME_PERIOD_TICKS = 1L
        private const val MIN_COMPOSITE_TTL_TICKS = 1L
        private const val UNINITIALIZED_FRAME_INDEX = -1L
        private const val DEFAULT_PARTICLE_COUNT = 1
        private const val DEFAULT_PARTICLE_OFFSET = 0.0
        private const val DEFAULT_PARTICLE_SPEED = 0.0

        // default view-distance culling radius in blocks
        const val DEFAULT_VIEW_DISTANCE_BLOCKS = 64.0
        private const val MIN_VIEW_DISTANCE_BLOCKS = 0.0
    }
}