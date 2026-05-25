package com.utophii.config

import com.utophii.api.EffectOptions
import com.utophii.engine.FXEngine
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Vector
import org.yaml.snakeyaml.Yaml
import java.io.File

// loads effect descriptions from YAML files without recompilation
class YamlEffectLoader(private val plugin: JavaPlugin) {
    private val yaml = Yaml()

    // ensures the effects directory exists
    fun ensureDirectories() {
        effectsDirectory().mkdirs()
    }

    // loads all YAML files from plugins/VEngine/effects
    fun loadAll(): List<YamlEffectDefinition> {
        ensureDirectories()
        return effectsDirectory()
            .listFiles { file -> file.isFile && file.extension.equals(YAML_EXTENSION, ignoreCase = true) }
            ?.mapNotNull(::load)
            ?: emptyList()
    }

    // loads one YAML effect file
    fun load(file: File): YamlEffectDefinition? {
        val raw = file.inputStream().use { stream -> yaml.load<Map<String, Any?>>(stream) } ?: return null
        val effectName = raw["effect"]?.toString() ?: file.nameWithoutExtension
        return YamlEffectDefinition(
            id = raw["id"]?.toString() ?: file.nameWithoutExtension,
            effect = effectName,
            options = options(raw),
        )
    }

    // plays a loaded YAML definition at a center location
    fun play(definition: YamlEffectDefinition, center: Location) {
        FXEngine.play(definition.effect, center, definition.options)
    }

    private fun options(raw: Map<String, Any?>): EffectOptions {
        val builder = EffectOptions.builder()
        raw["particle"]?.toString()?.let { builder.particle(Particle.valueOf(it.uppercase())) }
        raw["scale"]?.number()?.let(builder::scale)
        raw["rotationYaw"]?.number()?.let(builder::rotationYaw)
        raw["duration"]?.number()?.toLong()?.let(builder::duration)
        parseColor(raw["color"])?.let(builder::color)
        parseVector(raw["tiltAxis"])?.let(builder::tiltAxis)
        raw["tiltAngle"]?.number()?.let(builder::tiltAngle)

        val params = raw["parameters"]
        if (params is Map<*, *>) {
            builder.parameters(params.entries.mapNotNull { (key, value) ->
                val parameterKey = key?.toString() ?: return@mapNotNull null
                val parameterValue = value.number() ?: return@mapNotNull null
                parameterKey to parameterValue
            }.toMap())
        }

        return builder.build()
    }

    private fun parseColor(value: Any?): Color? {
        if (value is Map<*, *>) {
            val red = value["r"].number()?.toInt() ?: return null
            val green = value["g"].number()?.toInt() ?: return null
            val blue = value["b"].number()?.toInt() ?: return null
            return Color.fromRGB(red.coerceColor(), green.coerceColor(), blue.coerceColor())
        }
        return value?.toString()?.removePrefix("#")?.toIntOrNull(HEX_RADIX)?.let { Color.fromRGB(it) }
    }

    private fun parseVector(value: Any?): Vector? {
        if (value !is Map<*, *>) {
            return null
        }
        val x = value["x"].number() ?: return null
        val y = value["y"].number() ?: return null
        val z = value["z"].number() ?: return null
        return Vector(x, y, z)
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
    }
}

// parsed YAML effect definition
data class YamlEffectDefinition(
    val id: String,
    val effect: String,
    val options: EffectOptions,
)
