package com.utophii.config

import com.utophii.api.EffectOptions
import com.utophii.api.EffectModifier
import com.utophii.api.ParticleEffect
import com.utophii.effects.NumericAnimation
import com.utophii.effects.NumericKeyframe
import com.utophii.effects.NumericTrack
import com.utophii.effects.ParamRange
import com.utophii.effects.ParametricEffect
import com.utophii.effects.ScriptedEffect
import com.utophii.effects.ScriptedLayer
import com.utophii.engine.FXEngine
import com.utophii.math.EasingType
import com.utophii.math.ExpressionEvaluator
import com.utophii.math.MathUtils
import com.utophii.modifiers.RotationModifier
import com.utophii.modifiers.TurbulenceModifier
import com.utophii.modifiers.VortexModifier
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import org.yaml.snakeyaml.Yaml
import java.io.File

// loads effect descriptions from YAML without recompilation
class YamlEffectLoader(private val plugin: JavaPlugin) {
    private val yaml = Yaml()

    // ensures the effects directory exists
    fun ensureDirectories() {
        effectsDirectory().mkdirs()
    }

    // loads and registers all YAML effects from plugins/VEngine/effects
    fun loadAll(): List<ParticleEffect> {
        ensureDirectories()
        FXEngine.clearScripted()

        val loaded = effectsDirectory()
            .listFiles { file -> file.isFile && file.extension.equals(YAML_EXTENSION, ignoreCase = true) }
            ?.sortedBy { file -> file.name }
            ?.mapNotNull(::load)
            ?: emptyList()

        loaded.forEach(FXEngine::registerScripted)
        return loaded
    }

    // loads one YAML effect file into a runtime effect
    fun load(file: File): ParticleEffect? {
        val raw = file.inputStream().use { stream -> yaml.load<Map<String, Any?>>(stream) } ?: return null
        val id = raw[ID_KEY]?.toString()?.takeIf(String::isNotBlank) ?: file.nameWithoutExtension
        val duration = raw[DURATION_KEY].number()?.toLong()?.coerceAtLeast(MIN_DURATION_TICKS) ?: DEFAULT_DURATION_TICKS
        return if (isParametric(raw)) {
            parseParametric(id, raw, file.name)
        } else {
            loadScripted(id, duration, raw, file.name)
        }
    }

    // builds a parametric formula effect from a `curve`/`surface` YAML block
    private fun parseParametric(id: String, raw: Map<String, Any?>, fileName: String): ParametricEffect? {
        val block = (raw[curveOrSurfaceKey(raw)] as? Map<*, *>) ?: run {
            plugin.logger.warning("Skipping parametric effect '${fileName}': missing 'curve' or 'surface' block.")
            return null
        }
        val variables = parseStringList(block[VARIABLES_KEY], defaultVariables(raw))
        val sampling = parseIntList(block[SAMPLES_KEY] ?: block[SAMPLES_X_KEY], if (variables.size > 1) DEFAULT_SURFACE_SAMPLES else DEFAULT_CURVE_SAMPLES)
            .let { sampleList ->
                if (variables.size > 1 && sampleList.size < 2) {
                    listOf(sampleList.firstOrNull() ?: DEFAULT_SURFACE_SAMPLES[0], sampleList.getOrElse(1) { DEFAULT_SURFACE_SAMPLES[1] })
                } else sampleList
            }
        val ranges = parseRanges(block[RANGE_KEY], variables.size)
        val x = block[X_KEY]?.toString()?.takeIf(String::isNotBlank) ?: DEFAULT_FORMULA_X
        val y = block[Y_KEY]?.toString()?.takeIf(String::isNotBlank) ?: DEFAULT_FORMULA_Y
        val z = block[Z_KEY]?.toString()?.takeIf(String::isNotBlank) ?: DEFAULT_FORMULA_Z
        val defaults = parseParameters(block[PARAMETERS_KEY])
        val angularSpeed = block[ANGULAR_SPEED_KEY]?.number() ?: DEFAULT_ANGULAR_SPEED

        // fail fast on a malformed formula so the user sees a clear message instead of a silent no-op
        val valid = listOf(x, y, z).all { formula -> ExpressionEvaluator.compile(formula) != null }
        if (!valid) {
            plugin.logger.warning("Skipping parametric effect '${fileName}': one of the x/y/z formulas is invalid.")
            return null
        }
        return ParametricEffect(
            name = id,
            variables = variables,
            sampling = sampling,
            ranges = ranges,
            xFormula = x,
            yFormula = y,
            zFormula = z,
            defaults = defaults,
            angularSpeed = angularSpeed,
        )
    }

    // builds a layered scripted effect from `layers` or a legacy single layer
    private fun loadScripted(id: String, duration: Long, raw: Map<String, Any?>, fileName: String): ScriptedEffect? {
        val layers = parseLayers(raw)
        if (layers.isEmpty()) {
            plugin.logger.warning("Skipping effect '${fileName}': no valid layers were found.")
            return null
        }
        return ScriptedEffect(id, duration, layers)
    }

    private fun isParametric(raw: Map<String, Any?>): Boolean {
        val hasBlock = raw.containsKey(CURVE_KEY) || raw.containsKey(SURFACE_KEY)
        val type = raw[TYPE_KEY]?.toString()?.lowercase()
        return hasBlock || type in PARAMETRIC_TYPES
    }

    private fun curveOrSurfaceKey(raw: Map<String, Any?>): String {
        return if (raw.containsKey(SURFACE_KEY)) SURFACE_KEY else CURVE_KEY
    }

    private fun defaultVariables(raw: Map<String, Any?>): List<String> {
        // a surface block implies two variables; a curve block implies one
        return if (raw.containsKey(SURFACE_KEY)) DEFAULT_SURFACE_VARIABLES else DEFAULT_CURVE_VARIABLES
    }

    private fun parseStringList(value: Any?, fallback: List<String>): List<String> {
        val list = value as? List<*> ?: return fallback
        val parsed = list.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        return parsed.ifEmpty { fallback }
    }

    private fun parseIntList(value: Any?, fallback: List<Int>): List<Int> {
        // a scalar `samples: 100` applies to the first (and only) sampling dimension
        val scalar = value?.number()
        if (scalar != null) {
            return listOf(scalar.toInt().coerceAtLeast(MIN_SAMPLES))
        }
        val list = value as? List<*> ?: return listOf(fallback.firstOrNull() ?: MIN_SAMPLES)
        val parsed = list.mapNotNull { it.number()?.toInt()?.coerceAtLeast(MIN_SAMPLES) }
        return parsed.ifEmpty { listOf(fallback.firstOrNull() ?: MIN_SAMPLES) }
    }

    private fun parseRanges(value: Any?, variableCount: Int): List<ParamRange> {
        val ranges = if (value is List<*>) {
            value.mapNotNull { rangeNode ->
                val range = (rangeNode as? Map<*, *>) ?: return@mapNotNull null
                val first = range[MIN_KEY].number() ?: return@mapNotNull null
                val last = range[MAX_KEY].number() ?: return@mapNotNull null
                ParamRange(first, last)
            }
        } else {
            emptyList()
        }
        return if (ranges.size >= variableCount) ranges.take(variableCount)
        else (ranges + List(variableCount - ranges.size) { DEFAULT_RANGE }).take(variableCount)
    }

    private fun parseLayers(raw: Map<String, Any?>): List<ScriptedLayer> {
        val configuredLayers = raw[LAYERS_KEY]
        if (configuredLayers is List<*>) {
            return configuredLayers.mapNotNull(::parseLayer)
        }

        // backward-compatible single-layer format
        val legacyLayer = parseLayer(raw)
        return legacyLayer?.let(::listOf) ?: emptyList()
    }

    private fun parseLayer(raw: Any?): ScriptedLayer? {
        if (raw !is Map<*, *>) {
            return null
        }

        val effectName = raw[EFFECT_KEY]?.toString()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        if (FXEngine.primitiveEffect(effectName) == null) {
            plugin.logger.warning("Skipping layer for unknown primitive effect '$effectName'.")
            return null
        }

        val startTick = raw[START_KEY].number()?.toLong()?.coerceAtLeast(MIN_START_TICKS) ?: DEFAULT_START_TICKS
        val durationTicks = raw[DURATION_KEY].number()?.toLong()?.coerceAtLeast(MIN_DURATION_TICKS)
        val options = parseOptions(raw)
        val animation = parseAnimation(raw[ANIMATION_KEY])

        return ScriptedLayer(
            effect = effectName,
            startTick = startTick,
            durationTicks = durationTicks,
            options = options,
            animation = animation,
        )
    }

    private fun parseOptions(raw: Map<*, *>): EffectOptions {
        val builder = EffectOptions.builder()
        parseParticle(raw[PARTICLE_KEY])?.let(builder::particle)
        raw[SCALE_KEY]?.number()?.let(builder::scale)
        raw[ROTATION_YAW_KEY]?.number()?.let(builder::rotationYaw)
        raw[DURATION_KEY]?.number()?.toLong()?.let(builder::duration)

        parseColor(raw[COLOR_KEY])?.let(builder::color)
        parseColor(raw[TO_COLOR_KEY] ?: raw[TRANSITION_COLOR_KEY])?.let(builder::toColor)
        raw[DUST_SIZE_KEY]?.number()?.toFloat()?.let(builder::dustSize)

        parseMaterial(raw[MATERIAL_KEY])?.let(builder::material)
    
        raw[COUNT_KEY]?.number()?.toInt()?.let(builder::count)
        raw[SPEED_KEY]?.number()?.let(builder::speed)
        parseOffset(raw[OFFSET_KEY])?.let { (x, y, z) -> builder.offset(x, y, z) }

        parseVector(raw[TILT_AXIS_KEY])?.let(builder::tiltAxis)
        raw[TILT_ANGLE_KEY]?.number()?.let(builder::tiltAngle)

        val parameters = parseParameters(raw[PARAMETERS_KEY])
        if (parameters.isNotEmpty()) {
            builder.parameters(parameters)
        }
        val modifiers = parseModifiers(raw[MODIFIERS_KEY])
        if (modifiers.isNotEmpty()) {
            builder.modifiers(modifiers)
        }
        return builder.build()
    }

    private fun parseMaterial(value: Any?): Material? {
        val name = value?.toString()?.uppercase() ?: return null
        return Material.matchMaterial(name) ?: runCatching { Material.valueOf(name) }.getOrNull()
    }

    private fun parseOffset(value: Any?): Triple<Double, Double, Double>? {
        if (value == null) return null
        if (value is Number) {
            val uniform = value.toDouble()
            return Triple(uniform, uniform, uniform)
        }
        if (value is Map<*, *>) {
            val x = value[X_KEY].number() ?: 0.0
            val y = value[Y_KEY].number() ?: 0.0
            val z = value[Z_KEY].number() ?: 0.0
            return Triple(x, y, z)
        }
        return null
    }

    private fun parseParameters(raw: Any?): Map<String, Double> {
        if (raw !is Map<*, *>) {
            return emptyMap()
        }
        return raw.entries.mapNotNull { (key, value) ->
            val parameterKey = key?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val parameterValue = value.number() ?: return@mapNotNull null
            parameterKey to parameterValue
        }.toMap()
    }

    private fun parseModifiers(raw: Any?): List<EffectModifier> {
        if (raw !is List<*>) return emptyList()
        return raw.mapNotNull { modifierNode ->
            val node = modifierNode as? Map<*, *> ?: return@mapNotNull null
            when (node[TYPE_KEY]?.toString()?.lowercase()) {
                TURBULENCE_MODIFIER -> TurbulenceModifier(
                    strength = node[STRENGTH_KEY].number() ?: DEFAULT_TURBULENCE_STRENGTH,
                    frequency = node[FREQUENCY_KEY].number() ?: DEFAULT_TURBULENCE_FREQUENCY,
                    octaves = node[OCTAVES_KEY].number()?.toInt() ?: DEFAULT_TURBULENCE_OCTAVES,
                )
                ROTATION_MODIFIER -> RotationModifier(
                    axis = parseVector(node[AXIS_KEY]) ?: DEFAULT_ROTATION_AXIS.clone(),
                    angularVelocity = node[ANGULAR_VELOCITY_KEY].number()
                        ?: node[ANGULAR_SPEED_KEY].number()
                        ?: DEFAULT_ROTATION_ANGULAR_VELOCITY,
                    initialAngle = node[INITIAL_ANGLE_KEY].number() ?: DEFAULT_ROTATION_INITIAL_ANGLE,
                    pivotOffset = parseVector(node[PIVOT_OFFSET_KEY]) ?: DEFAULT_ROTATION_PIVOT_OFFSET.clone(),
                )
                VORTEX_MODIFIER -> VortexModifier(
                    axis = parseVector(node[AXIS_KEY]) ?: DEFAULT_VORTEX_AXIS.clone(),
                    coreRadius = node[CORE_RADIUS_KEY].number() ?: DEFAULT_VORTEX_CORE_RADIUS,
                    vortexStrength = node[STRENGTH_KEY].number() ?: DEFAULT_VORTEX_STRENGTH,
                )
                else -> null
            }
        }
    }

    private fun parseAnimation(raw: Any?): NumericAnimation {
        if (raw !is Map<*, *>) {
            return NumericAnimation.EMPTY
        }
        val inheritedLoop = raw[LOOP_KEY] as? Boolean ?: false
        val inheritedEasing = EasingType.fromString(raw[EASING_KEY]?.toString())
        val tracks = (raw[TRACKS_KEY] as? List<*>)
            ?.mapNotNull { trackNode -> parseTrack(trackNode, inheritedLoop, inheritedEasing) }
            ?: emptyList()
        return NumericAnimation(tracks)
    }

    private fun parseTrack(raw: Any?, inheritedLoop: Boolean, inheritedEasing: EasingType): NumericTrack? {
        if (raw !is Map<*, *>) {
            return null
        }
        val target = raw[TARGET_KEY]?.toString()?.takeIf(String::isNotBlank) ?: return null
        val loop = raw[LOOP_KEY] as? Boolean ?: inheritedLoop
        val trackEasing = raw[EASING_KEY]?.toString()?.let(EasingType::fromString) ?: inheritedEasing
        val keyframes = (raw[KEYFRAMES_KEY] as? List<*>)
            ?.mapNotNull(::parseKeyframe)
            ?: emptyList()
        if (keyframes.isEmpty()) {
            return null
        }
        return NumericTrack(
            target = target,
            keyframes = keyframes,
            loop = loop,
            easing = trackEasing,
        )
    }

    private fun parseKeyframe(raw: Any?): NumericKeyframe? {
        if (raw !is Map<*, *>) {
            return null
        }
        val tick = raw[TICK_KEY].number() ?: return null
        val value = raw[VALUE_KEY].number() ?: return null
        val easing = raw[EASING_KEY]?.toString()?.let(EasingType::fromString)
        return NumericKeyframe(
            tick = tick,
            value = value,
            easing = easing,
        )
    }

    private fun parseColor(value: Any?): Color? {
        if (value is Map<*, *>) {
            val red = value[RED_KEY].number()?.toInt() ?: return null
            val green = value[GREEN_KEY].number()?.toInt() ?: return null
            val blue = value[BLUE_KEY].number()?.toInt() ?: return null
            return Color.fromRGB(red.coerceColor(), green.coerceColor(), blue.coerceColor())
        }
        return value?.toString()?.removePrefix("#")?.toIntOrNull(HEX_RADIX)?.let { Color.fromRGB(it) }
    }

    private fun parseVector(value: Any?): Vector? {
        if (value !is Map<*, *>) {
            return null
        }
        val x = value[X_KEY].number() ?: return null
        val y = value[Y_KEY].number() ?: return null
        val z = value[Z_KEY].number() ?: return null
        return Vector(x, y, z)
    }

    private fun parseParticle(value: Any?): Particle? {
        val particleName = value?.toString()?.uppercase() ?: return null
        return runCatching { Particle.valueOf(particleName) }
            .onFailure { plugin.logger.warning("Unknown particle '$particleName' in VEngine YAML.") }
            .getOrNull()
    }

    private fun effectsDirectory(): File = File(plugin.dataFolder, EFFECTS_DIRECTORY)

    private fun Any?.number(): Double? = when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }

    private fun Int.coerceColor(): Int = coerceIn(MIN_COLOR_CHANNEL, MAX_COLOR_CHANNEL)

    companion object {
        private const val EFFECTS_DIRECTORY = "effects"
        private const val YAML_EXTENSION = "yml"
        private const val HEX_RADIX = 16
        private const val MIN_COLOR_CHANNEL = 0
        private const val MAX_COLOR_CHANNEL = 255
        private const val MIN_START_TICKS = 0L
        private const val MIN_DURATION_TICKS = 1L
        private const val DEFAULT_START_TICKS = 0L
        private const val DEFAULT_DURATION_TICKS = 60L

        private const val ID_KEY = "id"
        private const val EFFECT_KEY = "effect"
        private const val LAYERS_KEY = "layers"
        private const val CURVE_KEY = "curve"
        private const val SURFACE_KEY = "surface"
        private const val VARIABLES_KEY = "variables"
        private const val SAMPLES_KEY = "samples"
        private const val SAMPLES_X_KEY = "samplesX"
        private const val RANGE_KEY = "range"
        private const val MIN_KEY = "min"
        private const val MAX_KEY = "max"
        private const val MIN_SAMPLES = 4
        private const val DEFAULT_ANGULAR_SPEED = 0.05
        private const val DEFAULT_FORMULA_X = "0"
        private const val DEFAULT_FORMULA_Y = "0"
        private const val DEFAULT_FORMULA_Z = "0"
        private val DEFAULT_RANGE = ParamRange(0.0, MathUtils.TAU)
        private val DEFAULT_CURVE_VARIABLES = listOf("t")
        private val DEFAULT_SURFACE_VARIABLES = listOf("theta", "phi")
        private val DEFAULT_CURVE_SAMPLES = listOf(64)
        private val DEFAULT_SURFACE_SAMPLES = listOf(40, 20)
        private val PARAMETRIC_TYPES = setOf("parametric", "parametric_curve", "parametric_surface")
        private const val START_KEY = "start"
        private const val DURATION_KEY = "duration"
        private const val PARTICLE_KEY = "particle"
        private const val COLOR_KEY = "color"
        private const val SCALE_KEY = "scale"
        private const val ROTATION_YAW_KEY = "rotationYaw"
        private const val TILT_AXIS_KEY = "tiltAxis"
        private const val TILT_ANGLE_KEY = "tiltAngle"
        private const val PARAMETERS_KEY = "parameters"
        private const val MODIFIERS_KEY = "modifiers"
        private const val ANIMATION_KEY = "animation"
        private const val TRACKS_KEY = "tracks"
        private const val TARGET_KEY = "target"
        private const val KEYFRAMES_KEY = "keyframes"
        private const val TICK_KEY = "tick"
        private const val VALUE_KEY = "value"
        private const val LOOP_KEY = "loop"
        private const val TYPE_KEY = "type"
        private const val RED_KEY = "r"
        private const val GREEN_KEY = "g"
        private const val BLUE_KEY = "b"
        private const val X_KEY = "x"
        private const val Y_KEY = "y"
        private const val Z_KEY = "z"
        private const val STRENGTH_KEY = "strength"
        private const val FREQUENCY_KEY = "frequency"
        private const val OCTAVES_KEY = "octaves"
        private const val AXIS_KEY = "axis"
        private const val ANGULAR_VELOCITY_KEY = "angularVelocity"
        private const val ANGULAR_SPEED_KEY = "angularSpeed"
        private const val INITIAL_ANGLE_KEY = "initialAngle"
        private const val PIVOT_OFFSET_KEY = "pivotOffset"
        private const val EASING_KEY = "easing"
        private const val TO_COLOR_KEY = "toColor"
        private const val TRANSITION_COLOR_KEY = "transitionColor"
        private const val DUST_SIZE_KEY = "dustSize"
        private const val MATERIAL_KEY = "material"
        private const val COUNT_KEY = "count"
        private const val OFFSET_KEY = "offset"
        private const val SPEED_KEY = "speed"

        private const val TURBULENCE_MODIFIER = "turbulence"
        private const val ROTATION_MODIFIER = "rotation"
        private const val DEFAULT_TURBULENCE_STRENGTH = 0.12
        private const val DEFAULT_TURBULENCE_FREQUENCY = 0.35
        private const val DEFAULT_TURBULENCE_OCTAVES = 4
        private const val DEFAULT_ROTATION_ANGULAR_VELOCITY = 0.05
        private const val DEFAULT_ROTATION_INITIAL_ANGLE = 0.0
        private val DEFAULT_ROTATION_AXIS = Vector(0.0, 1.0, 0.0)
        private val DEFAULT_ROTATION_PIVOT_OFFSET = Vector(0.0, 0.0, 0.0)
        private const val VORTEX_MODIFIER = "vortex"
        private const val CORE_RADIUS_KEY = "coreRadius"
        private const val DEFAULT_VORTEX_CORE_RADIUS = 1.2
        private const val DEFAULT_VORTEX_STRENGTH = 0.15
        private val DEFAULT_VORTEX_AXIS = Vector(0.0, 1.0, 0.0)
    }
}
