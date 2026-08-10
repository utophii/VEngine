package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.ParticleEffect
import com.utophii.engine.FXEngine
import com.utophii.math.EasingType
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle

// YAML-driven composite effect assembled from primitive particle effects
// a scripted effect is composed from layers. Each layer references a primitive effect, has its own timing window, static options, and animated numeric tracks
class ScriptedEffect(
    override val name: String,
    private val defaultDurationTicks: Long,
    private val layers: List<ScriptedLayer>,
) : ParticleEffect {

    // plays every active layer with async calculation and sync rendering
    // each layer uses its own particle/material options, which is why this class owns rendering instead of delegating to [AbstractParticleEffect]
    override fun play(center: Location, opts: EffectOptions) {
        val stableCenter = center.clone()
        val totalDuration = totalDuration(opts)
        layers.forEach { layer ->
            val primitive = FXEngine.primitiveEffect(layer.effect) ?: return@forEach
            val startTick = layer.startTick.coerceAtLeast(MIN_START_TICKS)
            if (startTick >= totalDuration) {
                return@forEach
            }

            // remainingDuration = totalDuration - startTick: clamps the layer inside the parent effect duration
            val remainingDuration = (totalDuration - startTick).coerceAtLeast(MIN_DURATION_TICKS)
            val layerDuration = layer.durationTicks?.coerceAtLeast(MIN_DURATION_TICKS) ?: remainingDuration
            val clampedDuration = layerDuration.coerceAtMost(remainingDuration)

            FXEngine.scheduler().scheduleFrames(
                initialDelayTicks = startTick,
                durationTicks = clampedDuration,
                calculate = { time ->
                    val resolvedOptions = layer.resolveOptions(opts, time)
                    LayerFrame(
                        options = resolvedOptions,
                        positions = primitive.calculate(stableCenter, resolvedOptions, time),
                    )
                },
                render = { _, frame -> render(frame.positions, frame.options) },
            )
        }
    }

    // calculates all active layer positions for the supplied frame time
    // this method merges all active layer positions into a single list, but per-layer rendering options such as particle type are only preserved in [play]
    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> {
        return layers.asSequence()
            .filter { layer -> layer.isActiveAt(time, totalDuration(opts)) }
            .flatMap { layer ->
                val primitive = FXEngine.primitiveEffect(layer.effect) ?: return@flatMap emptySequence<Location>()
                val localTime = time - layer.startTick
                val resolvedOptions = layer.resolveOptions(opts, localTime)
                primitive.calculate(center, resolvedOptions, localTime).asSequence()
            }
            .toList()
    }

    private fun totalDuration(runtime: EffectOptions): Long {
        return if (runtime.duration != EffectOptions.DEFAULT_DURATION_TICKS) {
            runtime.duration.coerceAtLeast(MIN_DURATION_TICKS)
        } else {
            defaultDurationTicks.coerceAtLeast(MIN_DURATION_TICKS)
        }
    }

    private fun render(positions: List<Location>, opts: EffectOptions) {
        val particleData = particleData(opts)
        positions.forEach { location ->
            FXEngine.scheduler().spawnParticle(location, opts.particle, particleData, opts.receivers)
        }
    }

    private fun particleData(opts: EffectOptions): Any? {
        val color = opts.color ?: return null
        return when (opts.particle) {
            Particle.DUST -> Particle.DustOptions(color, DUST_SIZE)
            else -> null
        }
    }

    private data class LayerFrame(
        val options: EffectOptions,
        val positions: List<Location>,
    )

    companion object {
        private const val MIN_START_TICKS = 0L
        private const val MIN_DURATION_TICKS = 1L
        private const val DUST_SIZE = 1.0F
    }
}

// one runtime layer inside a [ScriptedEffect]
data class ScriptedLayer(
    val effect: String,
    val startTick: Long,
    val durationTicks: Long?,
    val options: EffectOptions,
    val animation: NumericAnimation = NumericAnimation.EMPTY,
) {

    // resolves the final layer options for one frame
    // global runtime values from the caller are merged with layer-local YAML values, and then animated tracks override the numeric targets they address
    fun resolveOptions(runtime: EffectOptions, time: Double): EffectOptions {
        val mergedParameters = options.parameters + runtime.parameters
        val builder = options.toBuilder()

        // scale = layerScale * runtimeScale: caller scale acts as a global multiplier for the whole scripted effect
        builder.scale(options.scale * runtime.scale)

        // rotationYaw = layerYaw + runtimeYaw: caller yaw adds a global planar rotation on top of the layer rotation
        builder.rotationYaw(options.rotationYaw + runtime.rotationYaw)

        // tiltAxis = runtimeAxis ?: layerAxis: caller tilt axis overrides the layer axis when explicitly supplied
        (runtime.tiltAxis ?: options.tiltAxis)?.let(builder::tiltAxis)

        // tiltAngle = runtimeTilt if runtime axis is provided else layerTilt + runtimeTilt: caller tilt can globally augment the layer tilt
        val tiltAngle = if (runtime.tiltAxis != null) {
            runtime.tiltAngle
        } else {
            options.tiltAngle + runtime.tiltAngle
        }
        builder.tiltAngle(tiltAngle)

        if (runtime.receivers.isNotEmpty()) {
            builder.receivers(runtime.receivers)
        }

        if (runtime.modifiers.isNotEmpty()) {
            builder.modifiers(options.modifiers + runtime.modifiers)
        }

        builder.parameters(mergedParameters)

        // runtimeColor ?: layerColor: caller color overrides the YAML layer color when explicitly supplied
        runtime.color?.let(builder::color)

        // runtimeParticle when non-default else layerParticle: preserves YAML particles while still allowing explicit API overrides
        if (runtime.particle != DEFAULT_PARTICLE) {
            builder.particle(runtime.particle)
        }

        val animated = animation.apply(builder.build(), time)
        return animated.toBuilder()
            .duration(durationTicks ?: runtime.duration)
            .build()
    }

    // checks whether the layer should contribute at the supplied absolute effect time
    fun isActiveAt(time: Double, parentDuration: Long): Boolean {
        if (time < startTick) {
            return false
        }
        // remaining = parentDuration - startTick: limits the layer to the remaining time budget of the parent effect
        val remaining = (parentDuration - startTick).coerceAtLeast(0L)
        val effectiveDuration = (durationTicks ?: remaining).coerceAtMost(remaining)
        return (time - startTick) < effectiveDuration
    }

    companion object {
        private val DEFAULT_PARTICLE = EffectOptions().particle
    }
}

// numeric keyframe animation resolved with linear interpolation
data class NumericAnimation(
    val tracks: List<NumericTrack>,
) {
    fun apply(options: EffectOptions, time: Double): EffectOptions {
        if (tracks.isEmpty()) {
            return options
        }

        val builder = options.toBuilder()
        val parameters = options.parameters.toMutableMap()
        val colorChannels = mutableMapOf(
            COLOR_RED_TARGET to (options.color?.red?.toDouble() ?: 255.0),
            COLOR_GREEN_TARGET to (options.color?.green?.toDouble() ?: 255.0),
            COLOR_BLUE_TARGET to (options.color?.blue?.toDouble() ?: 255.0),
        )
        var colorTouched = options.color != null

        tracks.forEach { track ->
            val value = track.valueAt(time)
            when {
                track.target.equals(SCALE_TARGET, ignoreCase = true) -> builder.scale(value)
                track.target.equals(ROTATION_YAW_TARGET, ignoreCase = true) -> builder.rotationYaw(value)
                track.target.equals(TILT_ANGLE_TARGET, ignoreCase = true) -> builder.tiltAngle(value)
                track.target.startsWith(PARAMETER_TARGET_PREFIX, ignoreCase = true) -> {
                    val key = track.target.substring(PARAMETER_TARGET_PREFIX.length)
                    if (key.isNotBlank()) {
                        parameters[key] = value
                    }
                }
                track.target.equals(COLOR_RED_TARGET, ignoreCase = true) -> {
                    colorChannels[COLOR_RED_TARGET] = value
                    colorTouched = true
                }
                track.target.equals(COLOR_GREEN_TARGET, ignoreCase = true) -> {
                    colorChannels[COLOR_GREEN_TARGET] = value
                    colorTouched = true
                }
                track.target.equals(COLOR_BLUE_TARGET, ignoreCase = true) -> {
                    colorChannels[COLOR_BLUE_TARGET] = value
                    colorTouched = true
                }
            }
        }

        builder.parameters(parameters)
        if (colorTouched) {
            builder.color(
                Color.fromRGB(
                    colorChannels[COLOR_RED_TARGET]!!.toInt().coerceIn(MIN_COLOR_CHANNEL, MAX_COLOR_CHANNEL),
                    colorChannels[COLOR_GREEN_TARGET]!!.toInt().coerceIn(MIN_COLOR_CHANNEL, MAX_COLOR_CHANNEL),
                    colorChannels[COLOR_BLUE_TARGET]!!.toInt().coerceIn(MIN_COLOR_CHANNEL, MAX_COLOR_CHANNEL),
                )
            )
        }
        return builder.build()
    }

    companion object {
        val EMPTY = NumericAnimation(emptyList())

        private const val SCALE_TARGET = "scale"
        private const val ROTATION_YAW_TARGET = "rotationYaw"
        private const val TILT_ANGLE_TARGET = "tiltAngle"
        private const val PARAMETER_TARGET_PREFIX = "parameters."
        private const val COLOR_RED_TARGET = "color.r"
        private const val COLOR_GREEN_TARGET = "color.g"
        private const val COLOR_BLUE_TARGET = "color.b"
        private const val MIN_COLOR_CHANNEL = 0
        private const val MAX_COLOR_CHANNEL = 255
    }
}

// one animated numeric track with keyframes and configurable easing curve
data class NumericTrack(
    val target: String,
    val keyframes: List<NumericKeyframe>,
    val loop: Boolean = false,
    val easing: EasingType = EasingType.LINEAR,
) {
    private val sortedKeyframes = keyframes.sortedBy(NumericKeyframe::tick)

    // resolves the track value at the supplied tick using the configured easing curve
    fun valueAt(time: Double): Double {
        if (sortedKeyframes.isEmpty()) {
            return 0.0
        }
        if (sortedKeyframes.size == 1) {
            return sortedKeyframes.first().value
        }

        val lastTick = sortedKeyframes.last().tick
        val resolvedTime = when {
            loop && lastTick > 0.0 -> ((time % lastTick) + lastTick) % lastTick
            else -> time.coerceIn(sortedKeyframes.first().tick, lastTick)
        }

        val upperIndex = sortedKeyframes.indexOfFirst { frame -> frame.tick >= resolvedTime }
        if (upperIndex <= 0) {
            return sortedKeyframes.first().value
        }
        val lower = sortedKeyframes[upperIndex - 1]
        val upper = sortedKeyframes[upperIndex]
        if (upper.tick == lower.tick) {
            return upper.value
        }

        // linearAlpha = (time - lowerTick) / (upperTick - lowerTick): normalized progress inside the segment
        val linearAlpha = (resolvedTime - lower.tick) / (upper.tick - lower.tick)

        // effectiveEasing = keyframeEasing ?: trackEasing: per-keyframe easing overrides track default
        val effectiveEasing = lower.easing ?: easing

        // easedAlpha = easing.apply(linearAlpha): applies the non-linear easing function
        val easedAlpha = effectiveEasing.apply(linearAlpha)

        // value = lower + (upper - lower) * easedAlpha: interpolates the scalar value
        return lower.value + (upper.value - lower.value) * easedAlpha
    }
}

// one scalar keyframe inside a [NumericTrack] with optional local easing
data class NumericKeyframe(
    val tick: Double,
    val value: Double,
    val easing: EasingType? = null,
)