package com.utophii.engine

import com.utophii.api.EffectHandle
import com.utophii.api.SimpleEffectHandle
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// owns Bukkit task scheduling, active handles and frame synchronization for VEngine effects
class EffectScheduler(private val plugin: JavaPlugin) {

    private val tasks: MutableSet<BukkitTask> = ConcurrentHashMap.newKeySet()
    private val activeHandles = ConcurrentHashMap<String, EffectHandle>()
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
    fun <T> scheduleFrames(
        effectName: String,
        initialDelayTicks: Long = INITIAL_DELAY_TICKS,
        durationTicks: Long,
        calculate: (Double) -> T,
        render: (Double, T) -> Unit,
        release: ((T) -> Unit)? = null,
    ): EffectHandle {
        val handleId = "$effectName#${idCounter.getAndIncrement()}"
        var tick = 0L
        val lastDeliveredFrame = AtomicLong(UNINITIALIZED_FRAME_INDEX)
        lateinit var task: BukkitTask

        val handle = SimpleEffectHandle(handleId, effectName) {
            task.cancel()
            tasks.remove(task)
            activeHandles.remove(handleId)
        }

        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (tick >= durationTicks || handle.isCancelled) {
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
        return handle
    }

    // registers a composite or custom handle in the active handle registry
    fun registerHandle(handle: EffectHandle) {
        activeHandles[handle.id] = handle
    }

    // stops a running effect by handle ID
    fun stop(id: String): Boolean {
        val handle = activeHandles[id] ?: activeHandles.entries.firstOrNull { it.key.equals(id, ignoreCase = true) }?.value
        return if (handle != null) {
            handle.cancel()
            activeHandles.remove(handle.id)
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
        cancelAllTasks()
        return count
    }

    // returns a snapshot of active effect handles
    fun activeHandles(): List<EffectHandle> = activeHandles.values.toList()

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
        val world = location.world ?: return
        val explicitReceivers = receivers.toList()
        val candidates = if (explicitReceivers.isEmpty()) world.players.toList() else explicitReceivers

        // near = online players of the same world within the view-distance radius
        val near = candidates.filter { player ->
            player.isOnline && player.world == world && player.location.distanceSquared(location) <= viewDistanceSquared
        }
        if (near.isEmpty()) {
            return
        }

        near.forEach { player ->
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
        private const val UNINITIALIZED_FRAME_INDEX = -1L
        private const val DEFAULT_PARTICLE_COUNT = 1
        private const val DEFAULT_PARTICLE_OFFSET = 0.0
        private const val DEFAULT_PARTICLE_SPEED = 0.0

        // default view-distance culling radius in blocks
        const val DEFAULT_VIEW_DISTANCE_BLOCKS = 64.0
        private const val MIN_VIEW_DISTANCE_BLOCKS = 0.0
    }
}