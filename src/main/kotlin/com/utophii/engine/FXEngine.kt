package com.utophii.engine

import com.utophii.api.EffectOptions
import com.utophii.api.ParticleEffect
import com.utophii.effects.BeamEffect
import com.utophii.effects.HelixEffect
import com.utophii.effects.LissajousEffect
import com.utophii.effects.RosetteEffect
import com.utophii.effects.SphereEffect
import com.utophii.effects.TorusEffect
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin

// Central VEngine effect registry and scheduling facade
object FXEngine {
    private val effects = linkedMapOf<String, ParticleEffect>()
    private var scheduler: EffectScheduler? = null

    // initializes the engine and registers bundled effects
    fun initialize(plugin: JavaPlugin) {
        scheduler = EffectScheduler(plugin)
        register(HelixEffect())
        register(SphereEffect())
        register(TorusEffect())
        register(LissajousEffect())
        register(BeamEffect())
        register(RosetteEffect())
    }

    // registers or replaces an effect by its stable name
    fun register(effect: ParticleEffect) {
        effects[effect.name.lowercase()] = effect
    }

    // returns an effect by registry name
    fun effect(name: String): ParticleEffect? = effects[name.lowercase()]

    // plays a registered effect by name
    fun play(name: String, center: Location, options: EffectOptions = EffectOptions()) {
        effect(name)?.play(center, options)
    }

    // returns the active scheduler or fails if the plugin was not initialized
    fun scheduler(): EffectScheduler = requireNotNull(scheduler) { "FXEngine is not initialized" }

    // cancels all active effect tasks and clears runtime state
    fun shutdown() {
        scheduler?.cancelAll()
        scheduler = null
        effects.clear()
    }
}
