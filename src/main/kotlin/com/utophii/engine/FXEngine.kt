package com.utophii.engine

import com.utophii.api.EffectOptions
import com.utophii.api.EffectHandle
import com.utophii.api.EffectProvider
import com.utophii.api.ParticleEffect
import com.utophii.effects.LorenzAttractorEffect
import com.utophii.effects.ParametricEffect
import com.utophii.effects.RK4TrajectoryEffect
import com.utophii.effects.SphereEffect
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.atomic.AtomicInteger

// Central VEngine effect registry and scheduling facade
object FXEngine {
    private val primitiveEffects = linkedMapOf<String, ParticleEffect>()
    private val scriptedEffects = linkedMapOf<String, ParticleEffect>()
    private val providers = mutableListOf<EffectProvider>()
    private var scheduler: EffectScheduler? = null
    private val scriptedIdCounter = AtomicInteger(1)

    // initializes the engine and registers the irreducible Kotlin primitive effects.
    // All other shapes are formula primitives loaded from the effects/ YAML directory
    fun initialize(plugin: JavaPlugin) {
        scheduler = EffectScheduler(plugin)
        registerPrimitive(SphereEffect())
        registerPrimitive(RK4TrajectoryEffect())
        registerPrimitive(LorenzAttractorEffect())
    }

    fun nextScriptedId(): Int = scriptedIdCounter.getAndIncrement()

    // registers or replaces a primitive effect used as an engine building block
    fun registerPrimitive(effect: ParticleEffect) {
        primitiveEffects[effect.name.lowercase()] = effect
    }

    // registers a custom effect from an external plugin; equivalent to registerPrimitive but named for the SPI
    fun registerEffect(effect: ParticleEffect) = registerPrimitive(effect)

    // registers an EffectProvider: every effect it contributes is made available to the registry and the DSL
    fun registerProvider(provider: EffectProvider) {
        providers.add(provider)
        provider.provide().forEach(::registerPrimitive)
    }

    // registers or replaces a scripted effect loaded from configuration
    fun registerScripted(effect: ParticleEffect) {
        scriptedEffects[effect.name.lowercase()] = effect
    }

    // clears all dynamically loaded scripted effects
    fun clearScripted() {
        scriptedEffects.clear()
    }

    // removes formula-based primitives that were refreshed from YAML, keeping bundled Kotlin primitives intact
    fun clearParametricPrimitives() {
        primitiveEffects.filterValues { it is ParametricEffect }.keys.forEach(primitiveEffects::remove)
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