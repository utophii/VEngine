package com.utophii.api

import com.utophii.engine.FXEngine
import com.utophii.modifiers.ColorModifier
import com.utophii.modifiers.RotationModifier
import com.utophii.modifiers.TurbulenceModifier
import com.utophii.modifiers.VortexModifier
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.util.Vector

// type-safe domain marker for VEngine DSL blocks
@DslMarker
annotation class VEngineDsl

/**
 * Type-safe Kotlin DSL entry point: spawns a registered effect at this location.
 *
 * ```
 * player.location.playEffect("torus") {
 *     scale(2.0)
 *     color(Color.PURPLE)
 *     duration(120L)
 *     modifiers { vortex(); rotation(angularVelocity = 0.08) }
 *     parameter("points", 240)
 * }
 * ```
 *
 * @param name registry name of the effect (primitive or scripted)
 * @param config configuration DSL applied to the effect options
 * @return handle to control the spawned effect, or null when the effect is not registered
 */
fun Location.playEffect(name: String, config: EffectConfig.() -> Unit = {}): EffectHandle? {
    val options = EffectConfig().apply(config).build()
    return FXEngine.play(name, this, options)
}

// typesafe builder for all [EffectOptions] rendering and transform fields
@VEngineDsl
class EffectConfig {
    private val builder = EffectOptions.builder()

    fun particle(particle: Particle) = builder.particle(particle)

    fun color(color: Color) = builder.color(color)

    fun toColor(color: Color) = builder.toColor(color)

    fun dustSize(value: Float) = builder.dustSize(value)

    fun material(material: Material) = builder.material(material)

    fun count(value: Int) = builder.count(value)

    fun speed(value: Double) = builder.speed(value)

    // anisotropic random spread
    fun offset(x: Double, y: Double, z: Double) = builder.offset(x, y, z)

    // isotropic random spread
    fun offset(spread: Double) = builder.offset(spread, spread, spread)

    fun scale(value: Double) = builder.scale(value)

    fun rotationYaw(radians: Double) = builder.rotationYaw(radians)

    fun tiltAxis(axis: Vector) = builder.tiltAxis(axis)

    fun tiltAngle(radians: Double) = builder.tiltAngle(radians)

    fun duration(ticks: Long) = builder.duration(ticks)

    // restricts the effect to an explicit audience; empty means everyone nearby sees it
    fun receivers(vararg players: Player) = builder.receivers(players.toList())

    fun receivers(players: Iterable<Player>) = builder.receivers(players.toList())

    fun modifiers(block: ModifierConfig.() -> Unit) {
        builder.modifiers(ModifierConfig().apply(block).build())
    }

    // adds a single effect-specific numeric parameter
    fun parameter(key: String, value: Double) = builder.parameter(key, value)

    fun parameter(key: String, value: Int) = builder.parameter(key, value.toDouble())

    // bulk effect-specific numeric parameters
    fun parameters(values: Map<String, Double>) = builder.parameters(values)

    // common geometric shortcuts that map to effect parameters
    fun radius(value: Double) = builder.parameter(PARAM_RADIUS, value)

    fun points(value: Int) = builder.parameter(PARAM_POINTS, value.toDouble())

    // materializes the configured EffectOptions
    fun build(): EffectOptions = builder.build()

    companion object {
        const val PARAM_RADIUS = "radius"
        const val PARAM_POINTS = "points"
    }
}

// typesafe builder for the position post-processing modifier chain
@VEngineDsl
class ModifierConfig {
    private val modifiers = mutableListOf<EffectModifier>()

    fun turbulence(
        strength: Double = TurbulenceModifier.DEFAULT_STRENGTH,
        frequency: Double = TurbulenceModifier.DEFAULT_FREQUENCY,
        octaves: Int = TurbulenceModifier.DEFAULT_OCTAVES,
    ) {
        modifiers += TurbulenceModifier(strength, frequency, octaves)
    }

    fun rotation(
        axis: Vector = RotationModifier.DEFAULT_AXIS,
        angularVelocity: Double = RotationModifier.DEFAULT_ANGULAR_VELOCITY,
        initialAngle: Double = RotationModifier.DEFAULT_INITIAL_ANGLE,
        pivotOffset: Vector = RotationModifier.DEFAULT_PIVOT_OFFSET,
    ) {
        modifiers += RotationModifier(axis, angularVelocity, initialAngle, pivotOffset)
    }

    fun vortex(
        axis: Vector = VortexModifier.DEFAULT_AXIS,
        coreRadius: Double = VortexModifier.DEFAULT_CORE_RADIUS,
        strength: Double = VortexModifier.DEFAULT_STRENGTH,
    ) {
        modifiers += VortexModifier(axis, coreRadius, strength)
    }

    fun color(
        from: Color,
        to: Color,
        periodTicks: Double = ColorModifier.DEFAULT_PERIOD_TICKS,
    ) {
        modifiers += ColorModifier(from, to, periodTicks)
    }

    // appends a custom modifier implementation
    fun add(modifier: EffectModifier) = modifiers.add(modifier)

    fun build(): List<EffectModifier> = modifiers.toList()
}
