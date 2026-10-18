package com.utophii.mythic

import com.utophii.api.EffectHandle
import com.utophii.engine.FXEngine
import java.util.UUID

// effects started by the vengine mechanic, remembered under their caster and the entity they play on
class MythicEffectTracker {
    private val byEntity = HashMap<UUID, MutableList<EffectHandle>>()
    private var tracked = 0

    fun track(handle: EffectHandle, entities: Collection<UUID>) {
        // finished effects cancel their handles, sweeping now and then keeps dead entries from piling up
        if (++tracked % SWEEP_INTERVAL == 0) {
            sweep()
        }
        entities.forEach { byEntity.getOrPut(it, ::ArrayList).add(handle) }
    }

    // stops the effects of one entity, all of them when effect is null
    fun stop(entity: UUID, effect: String?): Int {
        val handles = byEntity[entity] ?: return 0
        var stopped = 0
        handles.removeIf { handle ->
            when {
                handle.isCancelled -> true
                effect != null && !handle.effectName.equals(effect, ignoreCase = true) -> false
                else -> {
                    if (!FXEngine.stop(handle.id)) {
                        handle.cancel()
                    }
                    stopped++
                    true
                }
            }
        }
        if (handles.isEmpty()) {
            byEntity.remove(entity)
        }
        return stopped
    }

    fun clear() {
        byEntity.clear()
    }

    private fun sweep() {
        byEntity.values.removeIf { handles ->
            handles.removeIf(EffectHandle::isCancelled)
            handles.isEmpty()
        }
    }

    private companion object {
        const val SWEEP_INTERVAL = 256
    }
}
