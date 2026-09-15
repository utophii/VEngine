package com.utophii.api

import com.utophii.engine.FXEngine
import com.utophii.effects.ParamRange
import com.utophii.effects.ParametricEffect
import com.utophii.math.EasingType
import com.utophii.math.MathUtils
import com.utophii.modifiers.ColorModifier
import com.utophii.modifiers.MotionModifier
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
    val effectConfig = EffectConfig().apply(config)
    val options = effectConfig.build()
    val resolved = effectConfig.withAbsoluteMotion(options, toVector())
    return FXEngine.play(name, this, resolved)
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

    // tilt around the X-axis in radians: leans the effect forward/backward
    fun rotationPitch(radians: Double) = builder.rotationPitch(radians)

    // tilt around the Z-axis in radians: leans the effect sideways
    fun rotationRoll(radians: Double) = builder.rotationRoll(radians)

    // sets yaw, pitch and roll together (radians); omitted angles keep their previous values
    fun orientation(yaw: Double = 0.0, pitch: Double = 0.0, roll: Double = 0.0) {
        builder.rotationYaw(yaw)
        builder.rotationPitch(pitch)
        builder.rotationRoll(roll)
    }

    fun tiltAxis(axis: Vector) = builder.tiltAxis(axis)

    fun tiltAngle(radians: Double) = builder.tiltAngle(radians)

    fun duration(ticks: Long) = builder.duration(ticks)

    // restricts the effect to an explicit audience; empty means everyone nearby sees it
    fun receivers(vararg players: Player) = builder.receivers(players.toList())

    fun receivers(players: Iterable<Player>) = builder.receivers(players.toList())

    fun modifiers(block: ModifierConfig.() -> Unit) {
        builder.modifiers(ModifierConfig().apply(block).build())
    }

    // glides the whole effect from its spawn point to this absolute world position within ticks
    // chain multiple calls to travel through several coordinates; the effect holds at the last one
    fun moveTo(
        target: Location,
        ticks: Long,
        easing: EasingType = MotionModifier.DEFAULT_EASING,
    ) {
        absoluteTargets += MotionModifier.MotionLeg(target.toVector(), ticks.toDouble(), easing)
    }

    private val absoluteTargets = mutableListOf<MotionModifier.MotionLeg>()

    // converts absolute moveTo targets into a spawn-relative motion modifier; returns null when unused
    internal fun absoluteMotion(origin: Vector): MotionModifier? {
        if (absoluteTargets.isEmpty()) {
            return null
        }
        val legs = absoluteTargets.map { leg -> leg.copy(offset = leg.offset.clone().subtract(origin)) }
        return MotionModifier(legs)
    }

    // appends the absolute motion modifier (when configured) to already built options
    internal fun withAbsoluteMotion(options: EffectOptions, origin: Vector): EffectOptions {
        val motion = absoluteMotion(origin) ?: return options
        return options.toBuilder().modifiers(options.modifiers + motion).build()
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

    // moves the whole effect along a spawn-relative waypoint path
    //
    //     modifiers {
    //         motion(mode = MotionModifier.Mode.PING_PONG) {
    //             to(x = 5.0, y = 0.0, z = 0.0, ticks = 40L)
    //             to(x = 5.0, y = 3.0, z = 0.0, ticks = 20L, easing = EasingType.EASE_OUT_CUBIC)
    //         }
    //     }
    fun motion(
        mode: MotionModifier.Mode = MotionModifier.Mode.HOLD,
        path: MotionPath.() -> Unit,
    ) {
        modifiers += MotionModifier(MotionPath().apply(path).buildLegs(), mode)
    }

    // single-hop convenience: glide to the spawn-relative offset (x, y, z) within [ticks]
    fun motionTo(
        x: Double,
        y: Double,
        z: Double,
        ticks: Long,
        easing: EasingType = MotionModifier.DEFAULT_EASING,
        mode: MotionModifier.Mode = MotionModifier.Mode.HOLD,
    ) {
        modifiers += MotionModifier.to(Vector(x, y, z), ticks, easing, mode)
    }

    // appends a custom modifier implementation
    fun add(modifier: EffectModifier) = modifiers.add(modifier)

    fun build(): List<EffectModifier> = modifiers.toList()
}

// typesafe builder for a multi-leg motion path; every [to] adds one waypoint relative to the spawn point
@VEngineDsl
class MotionPath {
    private val legs = mutableListOf<MotionModifier.MotionLeg>()

    fun to(
        x: Double,
        y: Double,
        z: Double,
        ticks: Long,
        easing: EasingType = MotionModifier.DEFAULT_EASING,
    ) {
        legs += MotionModifier.MotionLeg(Vector(x, y, z), ticks.toDouble(), easing)
    }

    fun to(
        offset: Vector,
        ticks: Long,
        easing: EasingType = MotionModifier.DEFAULT_EASING,
    ) {
        legs += MotionModifier.MotionLeg(offset.clone(), ticks.toDouble(), easing)
    }

    fun buildLegs(): List<MotionModifier.MotionLeg> = legs.toList()
}

/**
 * Type-safe entry point for a user-defined parametric effect expressed as math formulas.
 *
 * ```
 * player.location.playParametric("ring") {
 *     variables("t")
 *     samples(96)
 *     range(0.0, MathUtils.TAU)
 *     x("R * cos(t)"); y("0"); z("R * sin(t)")
 *     default("R", 2.0)
 *     render { color(Color.AQUA); duration(160L); scale(1.5) }
 * }
 * ```
 *
 * @param name name used for the spawned effect handle
 * @param config formula geometry and rendering DSL
 * @return handle to control the spawned effect, or null if the formula is invalid
 */
fun Location.playParametric(name: String = "parametric", config: ParametricConfig.() -> Unit): EffectHandle? {
    val spec = ParametricConfig().apply(config)
    val options = spec.withAbsoluteMotion(toVector())
    return spec.buildEffect(name).play(this, options)
}

// configures a user-defined parametric formula effect (curve or surface) plus its rendering options
@VEngineDsl
class ParametricConfig {
    private var variableNames = DEFAULT_VARIABLES
    private var sampling = DEFAULT_CURVE_SAMPLES
    private var ranges = emptyList<ParamRange>()
    private var xFormula = DEFAULT_FORMULA
    private var yFormula = DEFAULT_FORMULA
    private var zFormula = DEFAULT_FORMULA
    private var angularSpeed = DEFAULT_ANGULAR_SPEED
    private val formulaDefaults = linkedMapOf<String, Double>()
    private val optionsConfig = EffectConfig()

    // declares the sampling variables (one for a curve, two for a surface)
    fun variables(vararg names: String) {
        variableNames = names.toList()
    }

    fun variables(names: List<String>) {
        variableNames = names.toList()
    }

    // sets the sample count; a surface accepts a second (latitude) count which defaults to the first
    fun samples(count: Int) {
        sampling = listOf(count)
    }

    fun samples(longitude: Int, latitude: Int) {
        sampling = listOf(longitude, latitude)
    }

    // sets the parameter range for the single curve variable
    fun range(min: Double, max: Double) {
        ranges = listOf(ParamRange(min, max))
    }

    // sets per-variable ranges for a surface
    fun ranges(vararg range: ParamRange) {
        ranges = range.toList()
    }

    fun x(formula: String) {
        xFormula = formula
    }

    fun y(formula: String) {
        yFormula = formula
    }

    fun z(formula: String) {
        zFormula = formula
    }

    fun angularSpeed(value: Double) {
        angularSpeed = value
    }

    // registers a formula variable default value
    fun default(key: String, value: Double) {
        formulaDefaults[key] = value
    }

    fun defaults(values: Map<String, Double>) {
        formulaDefaults.putAll(values)
    }

    // nested DSL for the shared rendering/transform fields (particle, color, scale, modifiers, ...)
    fun render(block: EffectConfig.() -> Unit) {
        optionsConfig.apply(block)
    }

    val options: EffectOptions
        get() = optionsConfig.build()

    // appends the absolute moveTo motion (configured inside render {}) resolved against the spawn origin
    internal fun withAbsoluteMotion(origin: Vector): EffectOptions =
        optionsConfig.withAbsoluteMotion(optionsConfig.build(), origin)

    internal fun buildEffect(effectName: String = "parametric"): ParametricEffect {
        val variableCount = variableNames.size
        val resolvedSamples = resolveSamples(variableCount)
        val resolvedRanges = resolveRanges(variableCount)
        return ParametricEffect(
            name = effectName,
            variables = variableNames,
            sampling = resolvedSamples,
            ranges = resolvedRanges,
            xFormula = xFormula,
            yFormula = yFormula,
            zFormula = zFormula,
            defaults = formulaDefaults,
            angularSpeed = angularSpeed,
        )
    }

    private fun resolveSamples(variableCount: Int): List<Int> {
        return when {
            sampling.size >= variableCount -> sampling.take(variableCount)
            sampling.size == 1 && variableCount == 2 -> listOf(sampling[0], sampling[0])
            else -> List(variableCount) { index -> sampling.getOrElse(index) { defaultSample(index) } }
        }
    }

    private fun resolveRanges(variableCount: Int): List<ParamRange> {
        return if (ranges.size >= variableCount) {
            ranges.take(variableCount)
        } else {
            (ranges + List(variableCount - ranges.size) { DEFAULT_RANGE }).take(variableCount)
        }
    }

    private fun defaultSample(index: Int): Int = if (variableNames.size > 1) DEFAULT_SURFACE_LATITUDE else DEFAULT_CURVE_SAMPLES.first()

    companion object {
        private val DEFAULT_VARIABLES = listOf("t")
        private val DEFAULT_CURVE_SAMPLES = listOf(64)
        private const val DEFAULT_SURFACE_LATITUDE = 20
        private const val DEFAULT_ANGULAR_SPEED = 0.05
        private const val DEFAULT_FORMULA = "0"
        private val DEFAULT_RANGE = ParamRange(0.0, MathUtils.TAU)
    }
}
