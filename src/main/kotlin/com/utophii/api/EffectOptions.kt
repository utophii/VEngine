package com.utophii.api

import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.util.Vector

/**
 * runtime options shared by every particle effect
 *
 * @property particle Bukkit particle type used by the renderer
 * @property color optional dust color for color-aware particles
 * @property scale uniform geometric scale applied by effect formulas
 * @property rotationYaw rotation around the Y axis in radians
 * @property tiltAxis optional normalized or non-normalized arbitrary tilt axis
 * @property tiltAngle arbitrary axis tilt angle in radians
 * @property duration duration in ticks
 * @property receivers explicit receiver list; empty means world broadcast
 * @property modifiers post-processing pipeline for calculated locations
 * @property parameters effect-specific numeric parameters loaded from code or YAML
 */
data class EffectOptions(
    val particle: Particle = Particle.FLAME,
    val color: Color? = null,
    val scale: Double = DEFAULT_SCALE,
    val rotationYaw: Double = DEFAULT_ROTATION_YAW,
    val tiltAxis: Vector? = null,
    val tiltAngle: Double = DEFAULT_TILT_ANGLE,
    val duration: Long = DEFAULT_DURATION_TICKS,
    val receivers: List<Player> = emptyList(),
    val modifiers: List<EffectModifier> = emptyList(),
    val parameters: Map<String, Double> = emptyMap(),
) {
    // creates a mutable builder initialized with this option set
    fun toBuilder(): Builder = Builder()
        .particle(particle)
        .scale(scale)
        .rotationYaw(rotationYaw)
        .tiltAngle(tiltAngle)
        .duration(duration)
        .receivers(receivers)
        .modifiers(modifiers)
        .parameters(parameters)
        .also { builder ->
            color?.let(builder::color)
            tiltAxis?.let(builder::tiltAxis)
        }

    class Builder {
        private var particle: Particle = Particle.FLAME
        private var color: Color? = null
        private var scale: Double = DEFAULT_SCALE
        private var rotationYaw: Double = DEFAULT_ROTATION_YAW
        private var tiltAxis: Vector? = null
        private var tiltAngle: Double = DEFAULT_TILT_ANGLE
        private var duration: Long = DEFAULT_DURATION_TICKS
        private var receivers: List<Player> = emptyList()
        private var modifiers: List<EffectModifier> = emptyList()
        private var parameters: Map<String, Double> = emptyMap()

        // sets the Bukkit particle type
        fun particle(value: Particle) = apply { particle = value }

        // sets an optional particle color
        fun color(value: Color) = apply { color = value }

        // sets uniform geometric scale
        fun scale(value: Double) = apply { scale = value }

        // sets rotation around the Y axis in radians
        fun rotationYaw(value: Double) = apply { rotationYaw = value }

        // sets arbitrary tilt axis
        fun tiltAxis(value: Vector) = apply { tiltAxis = value.clone() }

        // sets arbitrary tilt angle in radians
        fun tiltAngle(value: Double) = apply { tiltAngle = value }

        // sets duration in Bukkit ticks
        fun duration(value: Long) = apply { duration = value }

        // sets explicit particle receivers
        fun receivers(value: List<Player>) = apply { receivers = value.toList() }

        // sets post-processing modifiers
        fun modifiers(value: List<EffectModifier>) = apply { modifiers = value.toList() }

        // sdds one post-processing modifier
        fun modifier(value: EffectModifier) = apply { modifiers = modifiers + value }

        // sets all effect-specific parameters
        fun parameters(value: Map<String, Double>) = apply { parameters = value.toMap() }

        // adds one effect-specific parameter
        fun parameter(key: String, value: Double) = apply { parameters = parameters + (key to value) }

        // builds an immutable option set
        fun build(): EffectOptions = EffectOptions(
            particle = particle,
            color = color,
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

        // starts a chained builder for effect options
        fun builder(): Builder = Builder()
    }
}
