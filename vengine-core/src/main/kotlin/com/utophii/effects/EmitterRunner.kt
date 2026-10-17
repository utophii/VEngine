package com.utophii.effects

import com.utophii.api.EffectHandle
import com.utophii.api.EffectOptions
import com.utophii.api.FollowOptions
import com.utophii.engine.FXEngine
import org.bukkit.Location
import org.bukkit.scheduler.BukkitTask
import org.bukkit.util.Vector
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

// fires a scripted effect's emitters on the server thread and owns the effects they spawn.
// It is one of the parent's composite children, so stopping the parent stops everything it spawned.
// Spawned effects are detached from the scheduler listing: the parent stands for the whole group,
// and the runner ends the parent once its layers, emissions and spawned effects are all done
internal class EmitterRunner(
    private val parentId: String,
    private val emitters: List<EffectEmitter>,
    private val totalDuration: Long,
    private val origin: Location,
    // the parent's follow with its defaults applied
    private val follow: FollowOptions?,
    private val runtime: EffectOptions,
) : EffectHandle {
    override val id: String = "$parentId/emitters"
    override val effectName: String = parentId

    private val cancelled = AtomicBoolean(false)
    private val spawned = mutableListOf<EffectHandle>()
    private val lastEmissionTick = emitters.maxOf { it.lastTick(totalDuration) }
    private var tick = 0L
    private var task: BukkitTask? = null

    override val isCancelled: Boolean
        get() = cancelled.get()

    fun start() {
        val plugin = FXEngine.scheduler().plugin
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable(::step), 0L, 1L)
    }

    override fun cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return
        }
        task?.cancel()
        FXEngine.scheduler().runOnMainThread {
            spawned.forEach(EffectHandle::cancel)
            release(spawned.size)
            spawned.clear()
        }
    }

    private fun step() {
        if (isCancelled) {
            return
        }
        val before = spawned.size
        spawned.removeIf(EffectHandle::isCancelled)
        release(before - spawned.size)

        emitters.forEach { emitter ->
            if (emitter.firesAt(tick, totalDuration)) {
                emit(emitter)
            }
        }
        tick++
        if (tick > lastEmissionTick && tick >= totalDuration && spawned.isEmpty()) {
            FXEngine.stop(parentId)
        }
    }

    private fun emit(emitter: EffectEmitter) {
        val effect = FXEngine.effect(emitter.effect) ?: return
        if (live.incrementAndGet() > FXEngine.maxEmittedEffects) {
            live.decrementAndGet()
            return
        }
        val scale = runtime.scale * emitter.scale
        val offset = emitter.offset?.clone()?.multiply(runtime.scale) ?: Vector()
        val builder = EffectOptions.builder()
            .scale(scale)
            .receivers(runtime.receivers)
        emitter.duration?.let(builder::duration)

        val handle = if (follow != null) {
            // offsets live in the follow frame, so they turn together with the entity
            val childFollow = follow.copy(offset = (follow.offset?.clone() ?: Vector()).add(offset))
            effect.play(follow.entity.location, builder.follow(childFollow).build())
        } else {
            effect.play(origin.clone().add(offset), builder.build())
        }
        FXEngine.scheduler().detach(handle.id)
        spawned += handle
    }

    private fun release(count: Int) {
        if (count > 0) {
            live.addAndGet(-count)
        }
    }

    companion object {
        // effects spawned by emitters that are still playing, across all parents
        private val live = AtomicInteger()
    }
}
