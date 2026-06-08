package com.utophii.engine

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

// owns Bukkit task scheduling for VEngine effects
class EffectScheduler(private val plugin: JavaPlugin) {
    private val tasks = mutableSetOf<BukkitTask>()

    // calculates frames asynchronously and renders them synchronously once per tick
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

    // spawns one particle to either explicit receivers or the world
    fun spawnParticle(location: Location, particle: Particle, data: Any?, receivers: Iterable<Player>) {
        val explicitReceivers = receivers.toList()
        if (explicitReceivers.isEmpty()) {
            location.world?.spawnParticle(
                particle,
                location,
                PARTICLE_COUNT,
                PARTICLE_OFFSET,
                PARTICLE_OFFSET,
                PARTICLE_OFFSET,
                PARTICLE_SPEED,
                data,
            )
            return
        }

        explicitReceivers.forEach { player ->
            player.spawnParticle(
                particle,
                location,
                PARTICLE_COUNT,
                PARTICLE_OFFSET,
                PARTICLE_OFFSET,
                PARTICLE_OFFSET,
                PARTICLE_SPEED,
                data,
            )
        }
    }

    // cancels all active effect tasks owned by this scheduler
    fun cancelAll() {
        tasks.toList().forEach(BukkitTask::cancel)
        tasks.clear()
    }

    companion object {
        private const val INITIAL_DELAY_TICKS = 0L
        private const val FRAME_PERIOD_TICKS = 1L
        private const val PARTICLE_COUNT = 1
        private const val PARTICLE_OFFSET = 0.0
        private const val PARTICLE_SPEED = 0.0
    }
}
