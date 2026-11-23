package com.utophii.effects

import com.utophii.api.ContextualEffectModifier
import com.utophii.api.EffectOptions
import com.utophii.api.ParticleEffect
import com.utophii.modifiers.FlockingModifier
import org.bukkit.Location

// splits the modifier chain where the first modifier needs the entire frame
internal class FlockingChain private constructor(options: EffectOptions, split: Int) {
    val geometryOptions = options.toBuilder().modifiers(options.modifiers.take(split)).build()
    private val stages = options.modifiers.drop(split).map { modifier ->
        modifier to (modifier as? FlockingModifier)?.Simulation()
    }

    fun apply(points: List<Location>, center: Location, time: Double) {
        for ((modifier, flock) in stages) {
            when {
                flock != null -> flock.apply(points, time)
                modifier is ContextualEffectModifier -> points.forEach { modifier.modify(it, center, time) }
                else -> points.forEach { modifier.modify(it, time) }
            }
        }
    }

    // a random-access calculation has no previous frame
    inline fun preview(time: Double, frame: (Double) -> Unit) {
        require(time.isFinite() && time <= Int.MAX_VALUE) { "Invalid flocking preview time" }
        var tick = 0
        while (tick < time) frame((tick++).toDouble())
        frame(time)
    }

    companion object {
        fun from(options: EffectOptions, source: ParticleEffect): FlockingChain? {
            // leave collision trajectories their full modifier list for transform validation before tracing
            if (source is OdeEffect && source.calculateOnRender) return null
            val split = options.modifiers.indexOfFirst { it is FlockingModifier }
            return if (split < 0) null else FlockingChain(options, split)
        }
    }
}