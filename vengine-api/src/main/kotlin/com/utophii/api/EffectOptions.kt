package com.utophii.api

import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
* Rendering and transformation options used by VEngine particle effects.
*
* @property particle : Bukkit particle type
* @property color : Primary color (for DUST and starting color for DUST_COLOR_TRANSITION)
* @property toColor : End color for DUST_COLOR_TRANSITION
* @property gradient : Color stops for per-particle coloring (empty - every particle uses [color]).
*   Each particle picks its color by the value its effect assigns (e.g. escape time of a fractal),
*   or by its index along the shape when the effect assigns none. Applies to DUST, DUST_COLOR_TRANSITION,
*   ENTITY_EFFECT and PIXEL displays
* @property dustSize : Dust particle size (default 1.0)
* @property material : Material for block (BLOCK, FALLING_DUST) and item (ITEM) particles
* @property count : Number of particles spawned at each point (default 1)
* @property offsetX : Random spread along the X-axis
* @property offsetY : Random spread along the Y-axis
* @property offsetZ : Random spread along the Z-axis
* @property speed : Speed/extra particle parameter (extra)
* @property scale : Geometric scale
* @property rotationYaw : Rotation around the Y-axis in radians
* @property rotationPitch : Rotation around the X-axis in radians (tilts the effect forward/backward)
* @property rotationRoll : Rotation around the Z-axis in radians (tilts the effect sideways)
* @property tiltAxis : Arbitrary Tilt axis
* @property tiltAngle tilt angle in radians
* @property duration effect duration in ticks
* @property receivers list of receivers (empty - visible to everyone in the world)
* @property modifiers position post-processing chain
* @property parameters effect-specific numeric parameters
* @property display renders display entities at each point instead of particles (null = particles only)
* @property follow binds the effect to an entity instead of its fixed spawn point (null = stays in place)
* @property subject player whose PlaceholderAPI values fill `%placeholders%` in the effect (null = the followed player, if any)
* @property budgetExempt ignores the particle limits and the lag-driven quality drop (important effects such as boss telegraphs)
* @property lod thins the particles out with distance from each player (false = full density up to the view distance)
*/
data class EffectOptions(
    val particle: Particle = Particle.FLAME,
    val color: Color? = null,
    val toColor: Color? = null,
    val gradient: List<Color> = emptyList(),
    val dustSize: Float = DEFAULT_DUST_SIZE,
    val material: Material? = null,
    val count: Int = DEFAULT_PARTICLE_COUNT,
    val offsetX: Double = DEFAULT_PARTICLE_OFFSET,
    val offsetY: Double = DEFAULT_PARTICLE_OFFSET,
    val offsetZ: Double = DEFAULT_PARTICLE_OFFSET,
    val speed: Double = DEFAULT_PARTICLE_SPEED,
    val scale: Double = DEFAULT_SCALE,
    val rotationYaw: Double = DEFAULT_ROTATION_YAW,
    val rotationPitch: Double = DEFAULT_ROTATION_PITCH,
    val rotationRoll: Double = DEFAULT_ROTATION_ROLL,
    val tiltAxis: Vector? = null,
    val tiltAngle: Double = DEFAULT_TILT_ANGLE,
    val duration: Long = DEFAULT_DURATION_TICKS,
    val receivers: List<Player> = emptyList(),
    val modifiers: List<EffectModifier> = emptyList(),
    val parameters: Map<String, Double> = emptyMap(),
    val display: DisplayOptions? = null,
    val follow: FollowOptions? = null,
    val subject: Player? = null,
    val budgetExempt: Boolean = false,
    val lod: Boolean = true,
) {
    fun toBuilder(): Builder = Builder()
        .particle(particle)
        .dustSize(dustSize)
        .count(count)
        .offset(offsetX, offsetY, offsetZ)
        .speed(speed)
        .gradient(gradient)
        .scale(scale)
        .rotationYaw(rotationYaw)
        .rotationPitch(rotationPitch)
        .rotationRoll(rotationRoll)
        .tiltAngle(tiltAngle)
        .duration(duration)
        .receivers(receivers)
        .modifiers(modifiers)
        .parameters(parameters)
        .budgetExempt(budgetExempt)
        .lod(lod)
        .also { builder ->
            color?.let(builder::color)
            toColor?.let(builder::toColor)
            material?.let(builder::material)
            tiltAxis?.let(builder::tiltAxis)
            display?.let(builder::display)
            follow?.let(builder::follow)
            subject?.let(builder::subject)
        }

    class Builder {
        private var particle: Particle = Particle.FLAME
        private var color: Color? = null
        private var toColor: Color? = null
        private var gradient: List<Color> = emptyList()
        private var dustSize: Float = DEFAULT_DUST_SIZE
        private var material: Material? = null
        private var count: Int = DEFAULT_PARTICLE_COUNT
        private var offsetX: Double = DEFAULT_PARTICLE_OFFSET
        private var offsetY: Double = DEFAULT_PARTICLE_OFFSET
        private var offsetZ: Double = DEFAULT_PARTICLE_OFFSET
        private var speed: Double = DEFAULT_PARTICLE_SPEED
        private var scale: Double = DEFAULT_SCALE
        private var rotationYaw: Double = DEFAULT_ROTATION_YAW
        private var rotationPitch: Double = DEFAULT_ROTATION_PITCH
        private var rotationRoll: Double = DEFAULT_ROTATION_ROLL
        private var tiltAxis: Vector? = null
        private var tiltAngle: Double = DEFAULT_TILT_ANGLE
        private var duration: Long = DEFAULT_DURATION_TICKS
        private var receivers: List<Player> = emptyList()
        private var modifiers: List<EffectModifier> = emptyList()
        private var parameters: Map<String, Double> = emptyMap()
        private var display: DisplayOptions? = null
        private var follow: FollowOptions? = null
        private var subject: Player? = null
        private var budgetExempt: Boolean = false
        private var lod: Boolean = true

        fun particle(value: Particle) = apply { particle = value }
        fun color(value: Color) = apply { color = value }
        fun toColor(value: Color) = apply { toColor = value }
        fun gradient(value: List<Color>) = apply { gradient = value.toList() }
        fun dustSize(value: Float) = apply { dustSize = value }
        fun material(value: Material) = apply { material = value }
        fun count(value: Int) = apply { count = value.coerceAtLeast(1) }
        fun offset(x: Double, y: Double, z: Double) = apply {
            offsetX = x
            offsetY = y
            offsetZ = z
        }
        fun speed(value: Double) = apply { speed = value }
        fun scale(value: Double) = apply { scale = value }
        fun rotationYaw(value: Double) = apply { rotationYaw = value }
        fun rotationPitch(value: Double) = apply { rotationPitch = value }
        fun rotationRoll(value: Double) = apply { rotationRoll = value }
        fun tiltAxis(value: Vector) = apply { tiltAxis = value.clone() }
        fun tiltAngle(value: Double) = apply { tiltAngle = value }
        fun duration(value: Long) = apply { duration = value }
        fun receivers(value: List<Player>) = apply { receivers = value.toList() }
        fun modifiers(value: List<EffectModifier>) = apply { modifiers = value.toList() }
        fun modifier(value: EffectModifier) = apply { modifiers = modifiers + value }
        fun parameters(value: Map<String, Double>) = apply { parameters = value.toMap() }
        fun parameter(key: String, value: Double) = apply { parameters = parameters + (key to value) }
        fun display(value: DisplayOptions?) = apply { display = value }
        fun follow(value: FollowOptions?) = apply { follow = value }
        fun subject(value: Player?) = apply { subject = value }
        fun budgetExempt(value: Boolean) = apply { budgetExempt = value }
        fun lod(value: Boolean) = apply { lod = value }

        fun build(): EffectOptions = EffectOptions(
            particle = particle,
            color = color,
            toColor = toColor,
            gradient = gradient,
            dustSize = dustSize,
            material = material,
            count = count,
            offsetX = offsetX,
            offsetY = offsetY,
            offsetZ = offsetZ,
            speed = speed,
            scale = scale,
            rotationYaw = rotationYaw,
            rotationPitch = rotationPitch,
            rotationRoll = rotationRoll,
            tiltAxis = tiltAxis?.clone(),
            tiltAngle = tiltAngle,
            duration = duration,
            receivers = receivers,
            modifiers = modifiers,
            parameters = parameters,
            display = display,
            follow = follow,
            subject = subject,
            budgetExempt = budgetExempt,
            lod = lod,
        )
    }

    companion object {
        const val DEFAULT_SCALE = 1.0
        const val DEFAULT_ROTATION_YAW = 0.0
        const val DEFAULT_ROTATION_PITCH = 0.0
        const val DEFAULT_ROTATION_ROLL = 0.0
        const val DEFAULT_TILT_ANGLE = 0.0
        const val DEFAULT_DURATION_TICKS = 1L

        const val INFINITE_DURATION = Long.MAX_VALUE
        const val DEFAULT_DUST_SIZE = 1.0f
        const val DEFAULT_PARTICLE_COUNT = 1
        const val DEFAULT_PARTICLE_OFFSET = 0.0
        const val DEFAULT_PARTICLE_SPEED = 0.0

        fun builder(): Builder = Builder()
    }
}