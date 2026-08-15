package com.utophii.engine

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

// owns Bukkit task scheduling for VEngine effects
class EffectScheduler(private val plugin: JavaPlugin) {
    private val tasks = mutableSetOf<BukkitTask>()

    fun <T> scheduleFrames(
        initialDelayTicks: Long = INITIAL_DELAY_TICKS,
        durationTicks: Long,
        calculate: (Double) -> T,
        render: (Double, T) -> Unit,
    ): BukkitTask {
        var tick = 0L
        lateinit var task: BukkitTask
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            if (tick >= durationTicks) {
                task.cancel()
                tasks.remove(task)
                return@Runnable
            }

            val currentTime = tick.toDouble()
            plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
                val frame = calculate(currentTime)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (!task.isCancelled && task in tasks) {
                        render(currentTime, frame)
                    }
                })
            })
            tick++
        }, initialDelayTicks, FRAME_PERIOD_TICKS)
        tasks.add(task)
        return task
    }

    // spawns particles with full support for count, offset (x/y/z), speed and special data
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
        val explicitReceivers = receivers.toList()
        if (explicitReceivers.isEmpty()) {
            location.world?.spawnParticle(
                particle,
                location,
                count,
                offsetX,
                offsetY,
                offsetZ,
                speed,
                data,
            )
            return
        }

        explicitReceivers.forEach { player ->
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

    fun cancelAll() {
        tasks.toList().forEach(BukkitTask::cancel)
        tasks.clear()
    }

    companion object {
        private const val INITIAL_DELAY_TICKS = 0L
        private const val FRAME_PERIOD_TICKS = 1L
        private const val DEFAULT_PARTICLE_COUNT = 1
        private const val DEFAULT_PARTICLE_OFFSET = 0.0
        private const val DEFAULT_PARTICLE_SPEED = 0.0
    }
}