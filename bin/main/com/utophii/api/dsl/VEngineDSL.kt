package com.utophii.api.dsl

import com.utophii.api.EffectModifier
import com.utophii.api.EffectOptions
import com.utophii.engine.FXEngine
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.util.Vector

// DSL Builder for constructing EffectOptions
@DslMarker
annotation class VEngineDsl

@VEngineDsl
class EffectOptionsBuilder {
    var particle: Particle = Particle.DUST
    var color: Color? = null
    var scale: Double = 1.0
    var rotationYaw: Double = 0.0
    var tiltAxis: Vector? = null
    var tiltAngle: Double = 0.0
    var duration: Long = 60L
    private val receivers = mutableListOf<Player>()
    private val modifiers = mutableListOf<EffectModifier>()
    private val parameters = mutableMapOf<String, Double>()

    fun receivers(vararg players: Player) {
        receivers.addAll(players)
    }

    fun modifier(modifier: EffectModifier) {
        modifiers.add(modifier)
    }

    fun param(key: String, value: Double) {
        parameters[key] = value
    }

    fun build(): EffectOptions = EffectOptions(
        particle = particle,
        color = color,
        scale = scale,
        rotationYaw = rotationYaw,
        tiltAxis = tiltAxis,
        tiltAngle = tiltAngle,
        duration = duration,
        receivers = receivers,
        modifiers = modifiers,
        parameters = parameters,
    )
}

/**
 * A Kotlin extension for concisely reproducing an effect in the world
 *
 * Example:
 * ```kotlin
 * player.location.playEffect("torus_knot") {
 *     particle = Particle.DUST
 *     color = Color.fromRGB(180, 50, 255)
 *     scale = 1.5
 *     duration = 100
 *     param("majorRadius", 2.0)
 *     param("p", 2.0)
 *     param("q", 3.0)
 * }
 * ```
 */
inline fun Location.playEffect(effectName: String, block: EffectOptionsBuilder.() -> Unit) {
    val builder = EffectOptionsBuilder().apply(block)
    FXEngine.play(effectName, this, builder.build())
}