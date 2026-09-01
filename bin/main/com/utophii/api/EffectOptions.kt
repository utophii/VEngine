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
* @property dustSize : Dust particle size (default 1.0)
* @property material : Material for block (BLOCK, FALLING_DUST) and item (ITEM) particles
* @property count : Number of particles spawned at each point (default 1)
* @property offsetX : Random spread along the X-axis
* @property offsetY : Random spread along the Y-axis
* @property offsetZ : Random spread along the Z-axis
* @property speed : Speed/extra particle parameter (extra)
* @property scale : Geometric scale
* @property rotationYaw : Rotation around the Y-axis in radians
* @property tiltAxis : Arbitrary Tilt axis
* @property tiltAngle tilt angle in radians
* @property duration effect duration in ticks
* @property receivers list of receivers (empty - visible to everyone in the world)
* @property modifiers position post-processing chain
* @property parameters effect-specific numeric parameters
*/
data class EffectOptions(
    val particle: Particle = Particle.FLAME,
    val color: Color? = null,
    val toColor: Color? = null,
    val dustSize: Float = DEFAULT_DUST_SIZE,
    val material: Material? = null,
    val count: Int = DEFAULT_PARTICLE_COUNT,
    val offsetX: Double = DEFAULT_PARTICLE_OFFSET,
    val offsetY: Double = DEFAULT_PARTICLE_OFFSET,
    val offsetZ: Double = DEFAULT_PARTICLE_OFFSET,
    val speed: Double = DEFAULT_PARTICLE_SPEED,
    val scale: Double = DEFAULT_SCALE,
    val rotationYaw: Double = DEFAULT_ROTATION_YAW,
    val tiltAxis: Vector? = null,
    val tiltAngle: Double = DEFAULT_TILT_ANGLE,
    val duration: Long = DEFAULT_DURATION_TICKS,
    val receivers: List<Player> = emptyList(),
    val modifiers: List<EffectModifier> = emptyList(),
    val parameters: Map<String, Double> = emptyMap(),
) {
    fun toBuilder(): Builder = Builder()
        .particle(particle)
        .dustSize(dustSize)
        .count(count)
        .offset(offsetX, offsetY, offsetZ)
        .speed(speed)
        .scale(scale)
        .rotationYaw(rotationYaw)
        .tiltAngle(tiltAngle)
        .duration(duration)
        .receivers(receivers)
        .modifiers(modifiers)
        .parameters(parameters)
        .also { builder ->
            color?.let(builder::color)
            toColor?.let(builder::toColor)
            material?.let(builder::material)
            tiltAxis?.let(builder::tiltAxis)
        }

    class Builder {
        private var particle: Particle = Particle.FLAME
        private var color: Color? = null
        private var toColor: Color? = null
        private var dustSize: Float = DEFAULT_DUST_SIZE
        private var material: Material? = null
        private var count: Int = DEFAULT_PARTICLE_COUNT
        private var offsetX: Double = DEFAULT_PARTICLE_OFFSET
        private var offsetY: Double = DEFAULT_PARTICLE_OFFSET
        private var offsetZ: Double = DEFAULT_PARTICLE_OFFSET
        private var speed: Double = DEFAULT_PARTICLE_SPEED
        private var scale: Double = DEFAULT_SCALE
        private var rotationYaw: Double = DEFAULT_ROTATION_YAW
        private var tiltAxis: Vector? = null
        private var tiltAngle: Double = DEFAULT_TILT_ANGLE
        private var duration: Long = DEFAULT_DURATION_TICKS
        private var receivers: List<Player> = emptyList()
        private var modifiers: List<EffectModifier> = emptyList()
        private var parameters: Map<String, Double> = emptyMap()

        fun particle(value: Particle) = apply { particle = value }
        fun color(value: Color) = apply { color = value }
        fun toColor(value: Color) = apply { toColor = value }
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
        fun tiltAxis(value: Vector) = apply { tiltAxis = value.clone() }
        fun tiltAngle(value: Double) = apply { tiltAngle = value }
        fun duration(value: Long) = apply { duration = value }
        fun receivers(value: List<Player>) = apply { receivers = value.toList() }
        fun modifiers(value: List<EffectModifier>) = apply { modifiers = value.toList() }
        fun modifier(value: EffectModifier) = apply { modifiers = modifiers + value }
        fun parameters(value: Map<String, Double>) = apply { parameters = value.toMap() }
        fun parameter(key: String, value: Double) = apply { parameters = parameters + (key to value) }

        fun build(): EffectOptions = EffectOptions(
            particle = particle,
            color = color,
            toColor = toColor,
            dustSize = dustSize,
            material = material,
            count = count,
            offsetX = offsetX,
            offsetY = offsetY,
            offsetZ = offsetZ,
            speed = speed,
            scale = scale,
            rotationYaw = rotationYaw,
            tiltAxis = tiltAxis?.clone(),
            tiltAngle = tiltAngle,
            duration = duration,
            receivers = receivers,
            modifiers = modifiers,
            parameters = parameters,
        )
    }

    companion object {
        const val DEFAULT_SCALE = 1.0
        const val DEFAULT_ROTATION_YAW = 0.0
        const val DEFAULT_TILT_ANGLE = 0.0
        const val DEFAULT_DURATION_TICKS = 1L
        const val DEFAULT_DUST_SIZE = 1.0f
        const val DEFAULT_PARTICLE_COUNT = 1
        const val DEFAULT_PARTICLE_OFFSET = 0.0
        const val DEFAULT_PARTICLE_SPEED = 0.0

        fun builder(): Builder = Builder()
    }
}