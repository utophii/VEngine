package com.utophii.config

import com.utophii.api.DisplayOptions
import com.utophii.api.EffectOptions
import com.utophii.api.EffectModifier
import com.utophii.api.FollowDefaults
import com.utophii.api.FollowRotation
import com.utophii.api.ParticleEffect
import com.utophii.effects.AbstractParticleEffect
import com.utophii.effects.EffectEmitter
import com.utophii.effects.ImplicitEffect
import com.utophii.effects.LSystemEffect
import com.utophii.effects.NumericAnimation
import com.utophii.effects.NumericKeyframe
import com.utophii.effects.NumericTrack
import com.utophii.effects.ParamRange
import com.utophii.effects.ParametricEffect
import com.utophii.effects.ScriptedEffect
import com.utophii.effects.ScriptedLayer
import com.utophii.engine.FXEngine
import com.utophii.engine.PlaceholderExpression
import com.utophii.engine.Placeholders
import com.utophii.math.EasingType
import com.utophii.math.ExpressionEvaluator
import com.utophii.math.MathUtils
import com.utophii.modifiers.MotionModifier
import com.utophii.modifiers.RotationModifier
import com.utophii.modifiers.TurbulenceModifier
import com.utophii.modifiers.VortexModifier
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Display
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
    // parametric formula effects are atoms and are registered as primitives (so they can be used as
    // layer bases too); layered scripted effects are registered separately
    //
    // loading runs in two passes: formula atoms are parsed and registered first, because scripted
    // layers resolve their base primitives at parse time and would otherwise race file order
    fun loadAll(): List<ParticleEffect> {
        ensureDirectories()
        FXEngine.clearScripted()
        FXEngine.clearParametricPrimitives()
        Placeholders.resetWarnings()

        val parsedFiles = effectsDirectory()
            .listFiles { file -> file.isFile && file.extension.equals(YAML_EXTENSION, ignoreCase = true) }
            ?.sortedBy { file -> file.name }
            ?.mapNotNull { file -> readRaw(file)?.let { raw -> file to raw } }
            ?: emptyList()

        val loaded = mutableListOf<ParticleEffect>()

        // pass 1: register every formula atom so scripted layers can reference them regardless of file names
        parsedFiles.forEach { (file, raw) ->
            if (isAtom(raw)) {
                val effect = parseAtom(effectId(raw, file), raw, file.name) ?: return@forEach
                if (raw.containsKey(EMITTERS_KEY) || raw.containsKey(THEN_KEY)) {
                    plugin.logger.warning(
                        "'${file.name}' is a formula effect; its emitters/then are ignored. " +
                            "Use it as a layer of a layered effect to spawn other effects.",
                    )
                }
                FXEngine.registerPrimitive(effect)
                // played on its own, the atom renders with the particle/color/gradient written next to its block
                FXEngine.registerScripted(standaloneAtom(effect, raw))
                loaded += effect
            }
        }

        // pass 2: layered scripted effects; all YAML atoms are guaranteed to be resolvable now
        parsedFiles.forEach { (file, raw) ->
            if (!isAtom(raw)) {
                // a duration formula is only known at play time, so layer timing falls back to the default
                val duration = raw[DURATION_KEY].number()?.toLong()?.coerceAtLeast(MIN_DURATION_TICKS)
                    ?: DEFAULT_DURATION_TICKS
                val effect = loadScripted(effectId(raw, file), duration, raw, file.name) ?: return@forEach
                FXEngine.registerScripted(effect)
                loaded += effect
            }
        }
        return loaded - rejectEmitterProblems(loaded).toSet()
    }

    // warns about emitters pointing at unknown effects and unregisters effects that would spawn themselves forever
    private fun rejectEmitterProblems(loaded: List<ParticleEffect>): List<ParticleEffect> {
        val scripted = loaded.filterIsInstance<ScriptedEffect>()
        scripted.forEach { effect ->
            effect.emittedEffects.filter { FXEngine.effect(it) == null }.distinct().forEach { missing ->
                plugin.logger.warning("Effect '${effect.name}' emits unknown effect '$missing'; that emitter will do nothing.")
            }
        }
        val cyclic = EmitterGraph.cyclic(scripted.associate { it.name.lowercase() to it.emittedEffects.map(String::lowercase) })
        val rejected = scripted.filter { it.name.lowercase() in cyclic }
        rejected.forEach { effect ->
            plugin.logger.warning("Skipping effect '${effect.name}': its emitters/then spawn it again, directly or through other effects.")
            FXEngine.unregisterScripted(effect.name)
        }
        return rejected
    }

    // loads one YAML effect file into a runtime effect (parametric formula or a layered scripted effect)
    // note: scripted files only resolve layers whose primitives are already registered; bulk loading
    // should use [loadAll], which handles the ordering internally
    fun load(file: File): ParticleEffect? {
        val raw = readRaw(file) ?: return null
        val duration = raw[DURATION_KEY].number()?.toLong()?.coerceAtLeast(MIN_DURATION_TICKS) ?: DEFAULT_DURATION_TICKS
        return if (isAtom(raw)) {
            parseAtom(effectId(raw, file), raw, file.name)
        } else {
            loadScripted(effectId(raw, file), duration, raw, file.name)
        }
    }

    private fun readRaw(file: File): Map<String, Any?>? =
        file.inputStream().use { stream -> yaml.load<Map<String, Any?>>(stream) }

    private fun effectId(raw: Map<String, Any?>, file: File): String =
        raw[ID_KEY]?.toString()?.takeIf(String::isNotBlank) ?: file.nameWithoutExtension

    // wraps an atom into a one-layer effect carrying the file's top-level options, layers of other
    // effects still reference the bare primitive and bring their own options
    private fun standaloneAtom(atom: AbstractParticleEffect, raw: Map<String, Any?>): ScriptedEffect {
        val duration = raw[DURATION_KEY].number()?.toLong()?.coerceAtLeast(MIN_DURATION_TICKS) ?: DEFAULT_DURATION_TICKS
        val layer = ScriptedLayer(effect = atom.name.lowercase(), startTick = DEFAULT_START_TICKS, durationTicks = null, options = parseOptions(raw))
        return ScriptedEffect(atom.name, duration, listOf(layer), atom.followDefaults)
    }

    // builds a formula atom
    private fun parseAtom(id: String, raw: Map<String, Any?>, fileName: String): AbstractParticleEffect? = when {
        raw.containsKey(SDF_KEY) -> parseImplicit(id, raw, fileName)
        raw.containsKey(LSYSTEM_KEY) -> parseLSystem(id, raw, fileName)
        else -> parseParametric(id, raw, fileName)
    }

    // builds an implicit surface effect from an sdf YAML block
    private fun parseImplicit(id: String, raw: Map<String, Any?>, fileName: String): ImplicitEffect? {
        val block = raw[SDF_KEY] as? Map<*, *> ?: run {
            plugin.logger.warning("Skipping sdf effect '$fileName': 'sdf' must be a block with an 'f' formula.")
            return null
        }
        val formula = block[F_KEY]?.toString()?.takeIf(String::isNotBlank) ?: run {
            plugin.logger.warning("Skipping sdf effect '$fileName': missing 'f' formula.")
            return null
        }
        val value = block[VALUE_KEY]?.toString()?.takeIf(String::isNotBlank)
        val invalid = listOfNotNull(formula, value).firstOrNull { ExpressionEvaluator.compile(it) == null }
        if (invalid != null) {
            plugin.logger.warning("Skipping sdf effect '$fileName': invalid formula '$invalid'.")
            return null
        }
        return ImplicitEffect(
            name = id,
            formula = formula,
            valueFormula = value,
            bounds = block[BOUNDS_KEY]?.number()?.takeIf { it > 0.0 } ?: DEFAULT_SDF_BOUNDS,
            defaultPoints = block[POINTS_KEY]?.number()?.toInt() ?: DEFAULT_SDF_POINTS,
            defaults = parseParameters(block[PARAMETERS_KEY]),
            angularSpeed = block[ANGULAR_SPEED_KEY]?.number() ?: DEFAULT_ANGULAR_SPEED,
            followDefaults = parseFollow(raw[FOLLOW_KEY], fileName),
        )
    }

    // builds a turtle-drawn L-system from an lsystem YAML block
    private fun parseLSystem(id: String, raw: Map<String, Any?>, fileName: String): LSystemEffect? {
        val block = raw[LSYSTEM_KEY] as? Map<*, *> ?: run {
            plugin.logger.warning("Skipping lsystem effect '$fileName': 'lsystem' must be a block with 'axiom' and 'rules'.")
            return null
        }
        val axiom = block[AXIOM_KEY]?.toString()?.takeIf(String::isNotBlank) ?: run {
            plugin.logger.warning("Skipping lsystem effect '$fileName': missing 'axiom'.")
            return null
        }
        // rules: one symbol -> its replacement, or a list of replacements picked at random
        val rules = (block[RULES_KEY] as? Map<*, *>).orEmpty().mapNotNull { (symbol, replacement) ->
            val key = symbol?.toString()
            val productions = parseProductions(replacement, fileName, symbol)
            if (key == null || key.length != 1 || productions.isNullOrEmpty()) {
                plugin.logger.warning(
                    "In '$fileName': ignoring lsystem rule '$symbol' " +
                        "(a rule maps one symbol to a string or to a list of strings / { to, weight } entries).",
                )
                return@mapNotNull null
            }
            key[0] to productions
        }.toMap()
        val iterations = block[ITERATIONS_KEY]?.number()?.toInt()?.coerceAtLeast(0) ?: DEFAULT_LSYSTEM_ITERATIONS
        val anchor = block[ANCHOR_KEY]?.toString()?.lowercase()
        if (anchor != null && anchor != ANCHOR_BASE && anchor != ANCHOR_CENTER) {
            plugin.logger.warning("In '$fileName': unknown lsystem anchor '$anchor'; expected '$ANCHOR_BASE' or '$ANCHOR_CENTER'.")
        }
        val effect = LSystemEffect(
            name = id,
            axiom = axiom.filterNot(Char::isWhitespace),
            rules = rules,
            iterations = iterations,
            angleDegrees = block[ANGLE_KEY]?.number() ?: DEFAULT_LSYSTEM_ANGLE,
            size = block[SIZE_KEY]?.number()?.takeIf { it > 0.0 } ?: DEFAULT_LSYSTEM_SIZE,
            spacing = block[SPACING_KEY]?.number()?.takeIf { it > 0.0 } ?: DEFAULT_LSYSTEM_SPACING,
            maxPoints = block[POINTS_KEY]?.number()?.toInt()?.coerceIn(1, MAX_LSYSTEM_POINTS) ?: DEFAULT_LSYSTEM_POINTS,
            growthTicks = block[GROWTH_KEY]?.number()?.coerceAtLeast(0.0) ?: 0.0,
            anchorCenter = anchor == ANCHOR_CENTER,
            angularSpeed = block[ANGULAR_SPEED_KEY]?.number() ?: DEFAULT_ANGULAR_SPEED,
            followDefaults = parseFollow(raw[FOLLOW_KEY], fileName),
            seed = block[SEED_KEY]?.number()?.toLong(),
        )
        if (effect.appliedIterations < iterations) {
            plugin.logger.warning(
                "In '$fileName': lsystem stopped after ${effect.appliedIterations} of $iterations iterations " +
                    "(more than ${LSystemEffect.MAX_SYMBOLS} symbols).",
            )
        }
        return effect
    }

    // "F[+F]F" is one production, ["F[+F]F", "F[-F]F"] are equally likely; [{ to: "F[+F]F", weight: 2 }, ...] are weighted
    private fun parseProductions(value: Any?, fileName: String, symbol: Any?): List<LSystemEffect.Production>? = when (value) {
        null -> null
        is List<*> -> value.mapNotNull { entry ->
            when (entry) {
                is Map<*, *> -> {
                    val successor = entry[TO_KEY]?.toString()
                    val weight = entry[WEIGHT_KEY]?.number() ?: DEFAULT_PRODUCTION_WEIGHT
                    if (successor == null || weight <= 0.0) {
                        plugin.logger.warning("In '$fileName': ignoring a production of lsystem rule '$symbol' (needs 'to' and a positive 'weight').")
                        null
                    } else {
                        LSystemEffect.Production(successor.filterNot(Char::isWhitespace), weight)
                    }
                }
                null -> null
                else -> LSystemEffect.Production(entry.toString().filterNot(Char::isWhitespace))
            }
        }
        is Map<*, *> -> null
        else -> listOf(LSystemEffect.Production(value.toString().filterNot(Char::isWhitespace)))
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
        val value = block[VALUE_KEY]?.toString()?.takeIf(String::isNotBlank)

        // fail fast on a malformed formula so the user sees a clear message instead of a silent no-op
        val valid = listOfNotNull(x, y, z, value).all { formula -> ExpressionEvaluator.compile(formula) != null }
        if (!valid) {
            plugin.logger.warning("Skipping parametric effect '${fileName}': one of the x/y/z/value formulas is invalid.")
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
            followDefaults = parseFollow(raw[FOLLOW_KEY], fileName),
            valueFormula = value,
        )
    }

    // builds a layered scripted effect from `layers` or a legacy single layer
    private fun loadScripted(id: String, duration: Long, raw: Map<String, Any?>, fileName: String): ScriptedEffect? {
        val layers = parseLayers(raw, duration, fileName)
        if (layers.isEmpty()) {
            plugin.logger.warning("Skipping effect '${fileName}': no valid layers were found.")
            return null
        }
        val emitters = parseEmitters(raw[EMITTERS_KEY], fromEnd = false, fileName) +
            parseEmitters(raw[THEN_KEY], fromEnd = true, fileName)
        val durationFormula = parseFormula(raw[DURATION_KEY], DURATION_KEY, fileName)
        return ScriptedEffect(id, duration, layers, parseFollow(raw[FOLLOW_KEY], fileName), emitters, durationFormula)
    }

    // parses emitters: or then:
    private fun parseEmitters(value: Any?, fromEnd: Boolean, fileName: String): List<EffectEmitter> {
        val entries = when (value) {
            null -> return emptyList()
            is List<*> -> value
            else -> listOf(value)
        }
        return entries.mapNotNull { entry ->
            val node = entry as? Map<*, *> ?: mapOf(EFFECT_KEY to entry)
            val effect = node[EFFECT_KEY]?.toString()?.trim()?.lowercase()?.takeIf(String::isNotBlank) ?: run {
                plugin.logger.warning("Skipping an ${if (fromEnd) THEN_KEY else EMITTERS_KEY} entry without 'effect' in '$fileName'.")
                return@mapNotNull null
            }
            val every = node[EVERY_KEY].number()?.toLong()?.takeIf { it > 0L }
            val count = node[COUNT_KEY].number()?.toInt()?.coerceAtLeast(1)
            EffectEmitter(
                effect = effect,
                startTick = (node[if (fromEnd) DELAY_KEY else START_KEY].number()?.toLong() ?: DEFAULT_START_TICKS)
                    .coerceAtLeast(MIN_START_TICKS),
                // `count` alone repeats every tick
                every = every ?: count?.takeIf { it > 1 }?.let { 1L },
                count = count,
                offset = parseOffset(node[OFFSET_KEY])?.let { (x, y, z) -> Vector(x, y, z) },
                scale = node[SCALE_KEY].number()?.takeIf { it > 0.0 } ?: 1.0,
                duration = node[DURATION_KEY].number()?.toLong()?.coerceAtLeast(MIN_DURATION_TICKS),
                fromEnd = fromEnd,
            )
        }
    }

    // an effect with this block follows whoever plays it
    private fun parseFollow(value: Any?, fileName: String): FollowDefaults? {
        if (value == true) {
            return FollowDefaults()
        }
        val node = value as? Map<*, *> ?: return null
        val offset = parseOffset(node[OFFSET_KEY])?.let { (x, y, z) -> Vector(x, y, z) }
        val rotationName = node[ROTATION_KEY]?.toString()?.trim()
        val rotation = rotationName?.let { name ->
            FollowRotation.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: run {
                    plugin.logger.warning(
                        "Unknown follow rotation '$name' in '$fileName'; expected one of " +
                            FollowRotation.entries.joinToString { it.name.lowercase() } + ".",
                    )
                    null
                }
        }
        val stopWithEntity = node[STOP_WITH_ENTITY_KEY] as? Boolean
        return FollowDefaults(offset, rotation, stopWithEntity)
    }

    // formula atoms
    private fun isAtom(raw: Map<String, Any?>): Boolean {
        val hasBlock = raw.containsKey(CURVE_KEY) || raw.containsKey(SURFACE_KEY) || raw.containsKey(SDF_KEY) ||
            raw.containsKey(LSYSTEM_KEY)
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

    private fun parseLayers(raw: Map<String, Any?>, effectDuration: Long, fileName: String): List<ScriptedLayer> {
        val configuredLayers = raw[LAYERS_KEY]
        if (configuredLayers is List<*>) {
            val nodes = configuredLayers.filterIsInstance<Map<*, *>>()
            val timeline = LayerTimeline.resolve(nodes.map(::parseTiming), effectDuration)
            timeline.problems.forEach { problem -> plugin.logger.warning("In '$fileName': $problem.") }
            return nodes.zip(timeline.starts).mapNotNull { (node, start) -> start?.let { parseLayer(node, it, fileName) } }
        }

        // backward-compatible single-layer format
        val legacyLayer = parseLayer(raw, parseTiming(raw).start, fileName)
        return legacyLayer?.let(::listOf) ?: emptyList()
    }

    private fun parseTiming(raw: Map<*, *>): LayerTimeline.Timing = LayerTimeline.Timing(
        id = raw[ID_KEY]?.toString()?.takeIf(String::isNotBlank),
        start = raw[START_KEY].number()?.toLong()?.coerceAtLeast(MIN_START_TICKS) ?: DEFAULT_START_TICKS,
        duration = raw[DURATION_KEY].number()?.toLong()?.coerceAtLeast(MIN_DURATION_TICKS),
        after = raw[AFTER_KEY]?.toString()?.takeIf(String::isNotBlank),
        delay = raw[DELAY_KEY].number()?.toLong()?.coerceAtLeast(MIN_START_TICKS) ?: DEFAULT_START_TICKS,
    )

    private fun parseLayer(raw: Map<*, *>, startTick: Long, fileName: String): ScriptedLayer? {
        val effectName = raw[EFFECT_KEY]?.toString()?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        if (FXEngine.primitiveEffect(effectName) == null) {
            plugin.logger.warning("Skipping layer for unknown primitive effect '$effectName'.")
            return null
        }

        val durationTicks = raw[DURATION_KEY].number()?.toLong()?.coerceAtLeast(MIN_DURATION_TICKS)
        val options = parseOptions(raw)
        val animation = parseAnimation(raw[ANIMATION_KEY])

        return ScriptedLayer(
            effect = effectName,
            startTick = startTick,
            durationTicks = durationTicks,
            options = options,
            animation = animation,
            scaleFormula = parseFormula(raw[SCALE_KEY], SCALE_KEY, fileName),
            parameterFormulas = (raw[PARAMETERS_KEY] as? Map<*, *>).orEmpty().mapNotNull { (name, value) ->
                val key = name?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                parseFormula(value, "$PARAMETERS_KEY.$key", fileName)?.let { key to it }
            }.toMap(),
        )
    }

    // a string that is not a plain number is a formula
    private fun parseFormula(value: Any?, key: String, fileName: String): PlaceholderExpression? {
        val formula = (value as? String)?.takeIf { it.isNotBlank() && it.trim().toDoubleOrNull() == null } ?: return null
        return PlaceholderExpression.compile(formula) ?: run {
            plugin.logger.warning("In '$fileName': invalid expression for '$key': $formula")
            null
        }
    }

    private fun parseOptions(raw: Map<*, *>): EffectOptions {
        val builder = EffectOptions.builder()
        parseParticle(raw[PARTICLE_KEY])?.let(builder::particle)
        raw[SCALE_KEY]?.number()?.let(builder::scale)
        raw[ROTATION_YAW_KEY]?.number()?.let(builder::rotationYaw)
        raw[ROTATION_PITCH_KEY]?.number()?.let(builder::rotationPitch)
        raw[ROTATION_ROLL_KEY]?.number()?.let(builder::rotationRoll)
        raw[DURATION_KEY]?.number()?.toLong()?.let(builder::duration)

        parseColor(raw[COLOR_KEY])?.let(builder::color)
        parseColor(raw[TO_COLOR_KEY] ?: raw[TRANSITION_COLOR_KEY])?.let(builder::toColor)
        (raw[GRADIENT_KEY] as? List<*>)?.mapNotNull(::parseColor)?.takeIf { it.isNotEmpty() }?.let(builder::gradient)
        raw[DUST_SIZE_KEY]?.number()?.toFloat()?.let(builder::dustSize)

        parseMaterial(raw[MATERIAL_KEY])?.let(builder::material)
        parseDisplay(raw[DISPLAY_KEY])?.let(builder::display)
    
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

    // parses a `display:` block: the layer renders display entities at its points instead of particles
    private fun parseDisplay(value: Any?): DisplayOptions? {
        val node = value as? Map<*, *> ?: return null
        val typeName = node[TYPE_KEY]?.toString()?.trim()?.uppercase() ?: DisplayOptions.Type.ITEM.name
        val type = runCatching { DisplayOptions.Type.valueOf(typeName) }.getOrElse {
            plugin.logger.warning("Unknown display type '$typeName' in VEngine YAML; expected item, block, text or pixel.")
            return null
        }
        val defaults = if (type == DisplayOptions.Type.PIXEL) {
            val edge = DisplayOptions.DEFAULT_PIXEL_SIZE
            DisplayOptions(type = type, size = Vector(edge, edge, edge))
        } else {
            DisplayOptions(type = type)
        }
        val brightness = node[BRIGHTNESS_KEY]
        val shadow = node[SHADOW_KEY] as? Map<*, *>
        return DisplayOptions(
            type = type,
            material = parseMaterial(node[MATERIAL_KEY]) ?: if (type == DisplayOptions.Type.BLOCK) Material.AMETHYST_BLOCK else defaults.material,
            text = when (val text = node[TEXT_KEY]) {
                is List<*> -> text.mapNotNull { it?.toString() }
                null -> emptyList()
                else -> listOf(text.toString())
            },
            size = parseScale(node[SIZE_KEY]) ?: defaults.size,
            rotation = parseVector(node[ROTATION_KEY]) ?: defaults.rotation,
            spin = parseVector(node[SPIN_KEY]) ?: defaults.spin,
            phase = node[PHASE_KEY].number() ?: defaults.phase,
            billboard = node[BILLBOARD_KEY]?.toString()?.trim()?.uppercase()?.let { name ->
                runCatching { Display.Billboard.valueOf(name) }
                    .onFailure { plugin.logger.warning("Unknown display billboard '$name' in VEngine YAML.") }
                    .getOrNull()
            },
            blockLight = (brightness as? Map<*, *>)?.get(BLOCK_KEY).number()?.toInt() ?: brightness.number()?.toInt(),
            skyLight = (brightness as? Map<*, *>)?.get(SKY_KEY).number()?.toInt() ?: brightness.number()?.toInt(),
            glowColor = parseColor(node[GLOW_KEY]),
            interpolationTicks = node[INTERPOLATION_KEY].number()?.toInt() ?: defaults.interpolationTicks,
            viewRange = node[VIEW_RANGE_KEY].number()?.toFloat(),
            shadowRadius = shadow?.get(RADIUS_KEY).number()?.toFloat() ?: defaults.shadowRadius,
            shadowStrength = shadow?.get(STRENGTH_KEY).number()?.toFloat() ?: defaults.shadowStrength,
            textBackground = parseArgb(node[BACKGROUND_KEY]),
            textShadow = node[TEXT_SHADOW_KEY] as? Boolean ?: defaults.textShadow,
            seeThrough = node[SEE_THROUGH_KEY] as? Boolean ?: defaults.seeThrough,
            withParticles = node[PARTICLES_KEY] as? Boolean ?: defaults.withParticles,
        )
    }

    // uniform `0.5` or per-axis `{x, y, z}`
    private fun parseScale(value: Any?): Vector? {
        value.number()?.let { uniform -> return Vector(uniform, uniform, uniform) }
        return parseVector(value)
    }

    // "#AARRGGBB" (or "#RRGGBB", treated as opaque)
    private fun parseArgb(value: Any?): Color? {
        val hex = value?.toString()?.removePrefix("#") ?: return null
        val argb = hex.toLongOrNull(HEX_RADIX)?.toInt() ?: return null
        return if (hex.length > RGB_HEX_LENGTH) Color.fromARGB(argb) else Color.fromRGB(argb)
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
                MOTION_MODIFIER -> parseMotionModifier(node)
                else -> null
            }
        }
    }

    // parses a motion modifier: the whole effect glides along spawn-relative waypoints over time
    //
    //     - type: motion
    //       mode: hold | loop | ping_pong
    //       easing: ease_in_out_sine   # default for legs without their own easing
    //       path:
    //         - {x: 10, y: 0, z: 0, ticks: 40}
    //         - {x: 10, y: 5, z: 0, ticks: 20, easing: ease_out_cubic}
    private fun parseMotionModifier(node: Map<*, *>): MotionModifier? {
        val path = node[PATH_KEY] as? List<*> ?: return null
        val defaultEasing = node[EASING_KEY]?.toString()?.let(EasingType::fromString)
            ?: MotionModifier.DEFAULT_EASING
        val legs = path.mapNotNull { legNode ->
            val leg = legNode as? Map<*, *> ?: return@mapNotNull null
            val ticks = leg[TICKS_KEY].number()?.takeIf { it > 0.0 } ?: return@mapNotNull null
            val offset = Vector(
                leg[X_KEY].number() ?: 0.0,
                leg[Y_KEY].number() ?: 0.0,
                leg[Z_KEY].number() ?: 0.0,
            )
            val easing = leg[EASING_KEY]?.toString()?.let(EasingType::fromString) ?: defaultEasing
            MotionModifier.MotionLeg(offset, ticks, easing)
        }
        if (legs.isEmpty()) {
            return null
        }
        val mode = when (node[MODE_KEY]?.toString()?.trim()?.lowercase()?.replace('-', '_')) {
            MODE_LOOP -> MotionModifier.Mode.LOOP
            MODE_PING_PONG -> MotionModifier.Mode.PING_PONG
            else -> MotionModifier.Mode.HOLD
        }
        return MotionModifier(legs, mode)
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
        private const val SDF_KEY = "sdf"
        private const val F_KEY = "f"
        private const val POINTS_KEY = "points"
        private const val BOUNDS_KEY = "bounds"
        private const val DEFAULT_SDF_BOUNDS = 1.5
        private const val DEFAULT_SDF_POINTS = 600
        private const val LSYSTEM_KEY = "lsystem"
        private const val AXIOM_KEY = "axiom"
        private const val RULES_KEY = "rules"
        private const val ITERATIONS_KEY = "iterations"
        private const val ANGLE_KEY = "angle"
        private const val SPACING_KEY = "spacing"
        private const val GROWTH_KEY = "growth"
        private const val ANCHOR_KEY = "anchor"
        private const val SEED_KEY = "seed"
        private const val TO_KEY = "to"
        private const val WEIGHT_KEY = "weight"
        private const val DEFAULT_PRODUCTION_WEIGHT = 1.0
        private const val ANCHOR_BASE = "base"
        private const val ANCHOR_CENTER = "center"
        private const val DEFAULT_LSYSTEM_ITERATIONS = 4
        private const val DEFAULT_LSYSTEM_ANGLE = 25.0
        private const val DEFAULT_LSYSTEM_SIZE = 3.0
        private const val DEFAULT_LSYSTEM_SPACING = 0.08
        private const val DEFAULT_LSYSTEM_POINTS = 1500
        private const val MAX_LSYSTEM_POINTS = 4096
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
        private const val AFTER_KEY = "after"
        private const val EMITTERS_KEY = "emitters"
        private const val THEN_KEY = "then"
        private const val EVERY_KEY = "every"
        private const val DELAY_KEY = "delay"
        private const val DURATION_KEY = "duration"
        private const val PARTICLE_KEY = "particle"
        private const val COLOR_KEY = "color"
        private const val SCALE_KEY = "scale"
        private const val ROTATION_YAW_KEY = "rotationYaw"
        private const val ROTATION_PITCH_KEY = "rotationPitch"
        private const val ROTATION_ROLL_KEY = "rotationRoll"
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
        private const val FOLLOW_KEY = "follow"
        private const val STOP_WITH_ENTITY_KEY = "stopWithEntity"
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
        private const val GRADIENT_KEY = "gradient"
        private const val TRANSITION_COLOR_KEY = "transitionColor"
        private const val DUST_SIZE_KEY = "dustSize"
        private const val MATERIAL_KEY = "material"
        private const val COUNT_KEY = "count"
        private const val OFFSET_KEY = "offset"
        private const val SPEED_KEY = "speed"
        private const val DISPLAY_KEY = "display"
        private const val TEXT_KEY = "text"
        private const val SIZE_KEY = "size"
        private const val ROTATION_KEY = "rotation"
        private const val SPIN_KEY = "spin"
        private const val PHASE_KEY = "phase"
        private const val BILLBOARD_KEY = "billboard"
        private const val BRIGHTNESS_KEY = "brightness"
        private const val BLOCK_KEY = "block"
        private const val SKY_KEY = "sky"
        private const val GLOW_KEY = "glow"
        private const val INTERPOLATION_KEY = "interpolation"
        private const val VIEW_RANGE_KEY = "viewRange"
        private const val SHADOW_KEY = "shadow"
        private const val RADIUS_KEY = "radius"
        private const val BACKGROUND_KEY = "background"
        private const val TEXT_SHADOW_KEY = "textShadow"
        private const val SEE_THROUGH_KEY = "seeThrough"
        private const val PARTICLES_KEY = "particles"
        private const val RGB_HEX_LENGTH = 6

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
        private const val MOTION_MODIFIER = "motion"
        private const val PATH_KEY = "path"
        private const val TICKS_KEY = "ticks"
        private const val MODE_KEY = "mode"
        private const val MODE_LOOP = "loop"
        private const val MODE_PING_PONG = "ping_pong"
        private const val CORE_RADIUS_KEY = "coreRadius"
        private const val DEFAULT_VORTEX_CORE_RADIUS = 1.2
        private const val DEFAULT_VORTEX_STRENGTH = 0.15
        private val DEFAULT_VORTEX_AXIS = Vector(0.0, 1.0, 0.0)
    }
}
