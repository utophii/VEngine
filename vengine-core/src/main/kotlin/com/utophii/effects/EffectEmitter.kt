package com.utophii.effects

import org.bukkit.util.Vector

// one emitters: entry of a scripted effect: plays another registered effect
// on the parent's timeline, at the parent's origin or on its followed entity
data class EffectEmitter(
    val effect: String,
    val startTick: Long = 0L,
    val every: Long? = null,
    val count: Int? = null,
    val offset: Vector? = null,
    val scale: Double = 1.0,
    val duration: Long? = null,
    val fromEnd: Boolean = false,
) {
    // tick of the first emission for a parent whose layers run totalDuration ticks saturates
    fun firstTick(totalDuration: Long): Long = when {
        !fromEnd -> startTick
        totalDuration > Long.MAX_VALUE - startTick -> Long.MAX_VALUE
        else -> totalDuration + startTick
    }

    // whether the emitter fires at this parent tick
    fun firesAt(tick: Long, totalDuration: Long): Boolean {
        val first = firstTick(totalDuration)
        if (tick < first) {
            return false
        }
        val elapsed = tick - first
        val period = every ?: return elapsed == 0L
        if (elapsed % period != 0L) {
            return false
        }
        val limit = count ?: return tick < totalDuration
        return elapsed / period < limit
    }

    // the last tick this emitter may fire at
    fun lastTick(totalDuration: Long): Long {
        val first = firstTick(totalDuration)
        val period = every ?: return first
        val limit = count ?: return (totalDuration - 1).coerceAtLeast(first)
        return first + period * (limit - 1).coerceAtLeast(0)
    }
}
