package com.utophii.engine

import com.utophii.api.EffectOptions
import com.utophii.api.EffectHandle
import com.utophii.api.ParticleEffect
import com.utophii.effects.*
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.atomic.AtomicInteger

// Central VEngine effect registry and scheduling facade
object FXEngine {
    private val primitiveEffects = linkedMapOf<String, ParticleEffect>()
    private val scriptedEffects = linkedMapOf<String, ParticleEffect>()
    private var scheduler: EffectScheduler? = null
    private val scriptedIdCounter = AtomicInteger(1)

    // initializes the engine and registers bundled primitive effects
    fun initialize(plugin: JavaPlugin) {
        scheduler = EffectScheduler(plugin)
        registerPrimitive(HelixEffect())
        registerPrimitive(SphereEffect())
        registerPrimitive(TorusEffect())
        registerPrimitive(LissajousEffect())
        registerPrimitive(BeamEffect())
        registerPrimitive(RosetteEffect())
        registerPrimitive(RK4TrajectoryEffect())
        registerPrimitive(TorusKnotEffect())
        registerPrimitive(LorenzAttractorEffect())
        registerPrimitive(SupershapeEffect())
        registerPrimitive(SplineBeamEffect())
    }

    fun nextScriptedId(): Int = scriptedIdCounter.getAndIncrement()

    // registers or replaces a primitive effect used as an engine building block
    fun registerPrimitive(effect: ParticleEffect) {
        primitiveEffects[effect.name.lowercase()] = effect
    }

    // registers or replaces a scripted effect loaded from configuration
    fun registerScripted(effect: ParticleEffect) {
        scriptedEffects[effect.name.lowercase()] = effect
    }

    // clears all dynamically loaded scripted effects
    fun clearScripted() {
        scriptedEffects.clear()
    }

    // returns a primitive effect by registry name
    fun primitiveEffect(name: String): ParticleEffect? = primitiveEffects[name.lowercase()]

    // returns any effect by registry name; scripted effects override primitive names
    fun effect(name: String): ParticleEffect? {
        val key = name.lowercase()
        return scriptedEffects[key] ?: primitiveEffects[key]
    }

    // returns a sorted snapshot of all registered effect names
    fun effectNames(): List<String> = (primitiveEffects.keys + scriptedEffects.keys).distinct().sorted()

    // plays a registered effect and returns its EffectHandle
    fun play(name: String, center: Location, options: EffectOptions = EffectOptions()): EffectHandle? {
        return effect(name)?.play(center, options)
    }

    // stops an active running effect by ID
    fun stop(id: String): Boolean = scheduler?.stop(id) ?: false

    // stops all running effects
    fun stopAll(): Int = scheduler?.stopAll() ?: 0

    // returns all active running effect handles
    fun activeHandles(): List<EffectHandle> = scheduler?.activeHandles() ?: emptyList()

    // returns the active scheduler or fails if the plugin was not initialized
    fun scheduler(): EffectScheduler = requireNotNull(scheduler) { "FXEngine is not initialized" }

    // cancels all active effect tasks and clears runtime state
    fun shutdown() {
        scheduler?.cancelAll()
        scheduler = null
        clearScripted()
        primitiveEffects.clear()
    }
}