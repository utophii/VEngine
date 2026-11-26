package com.utophii.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.utophii.api.ParticleEffect
import com.utophii.effects.ScriptedEffect
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

data class ValidationIssue(val message: String, val warning: Boolean = false)

data class EffectValidation(val file: String, val id: String, val issues: List<ValidationIssue>) {
    val valid: Boolean get() = issues.none { !it.warning }
}

// the same bundled schema is used by editors and the command
internal object EffectSchema {
    private val mapper = ObjectMapper()
    private val schema by lazy {
        requireNotNull(EffectSchema::class.java.getResourceAsStream("/schemas/effect.schema.json")).use {
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(it)
        }
    }

    fun problems(raw: Any?): List<String> = schema.validate(mapper.valueToTree<JsonNode>(raw))
        .map { it.message }.sorted()
}

internal class EffectValidator(private val dataFolder: File, private val external: Map<String, ParticleEffect>) {
    private class Source(val file: EffectFile) {
        var id = file.id(null)
        var raw: Map<String, Any?>? = null
        var effect: ParticleEffect? = null
        val issues = mutableListOf<ValidationIssue>()
    }

    fun validate(): List<EffectValidation> {
        val sources = EffectFile.discover(File(dataFolder, "effects")).map(::Source)
        val yaml = Yaml(SafeConstructor(LoaderOptions().apply { isAllowDuplicateKeys = false }))
        sources.forEach { source ->
            try {
                val raw = source.file.diskFile.inputStream().use { yaml.load<Any?>(it) }
                if (raw is Map<*, *> && raw.keys.all { it is String }) {
                    source.id = source.file.id(raw["id"])
                    @Suppress("UNCHECKED_CAST")
                    source.raw = raw as Map<String, Any?>
                }
                source.issues += EffectSchema.problems(raw).map(::ValidationIssue)
            } catch (error: Exception) {
                source.issues += ValidationIssue(error.message ?: error.javaClass.simpleName)
            }
        }
        sources.groupBy { it.id.lowercase() }.values.filter { it.size > 1 }.forEach { duplicates ->
            val names = duplicates.joinToString { it.file.relativePath }
            duplicates.forEach { it.issues += ValidationIssue("Duplicate effect ID '${it.id}': $names") }
        }

        val primitives = external.toMutableMap()
        fun parse(source: Source) {
            val raw = source.raw ?: return
            if (source.issues.any { !it.warning }) return
            val logger = Logger.getAnonymousLogger().apply {
                useParentHandlers = false
                addHandler(object : Handler() {
                    override fun publish(record: LogRecord) {
                        source.issues += ValidationIssue(record.message, warning = true)
                    }
                    override fun flush() = Unit
                    override fun close() = Unit
                })
            }
            try {
                val parsed = YamlEffectLoader(dataFolder, logger) { primitives[it.lowercase()] }
                    .parseForValidation(source.file, raw)
                if (parsed == null) {
                    source.issues += ValidationIssue("Effect could not be built. See the parser diagnostics above.")
                } else {
                    source.effect = parsed.first
                    parsed.second?.let { primitives[it.name.lowercase()] = it }
                }
            } catch (error: Exception) {
                source.issues += ValidationIssue(error.message ?: error.javaClass.simpleName)
            }
        }
        val classifier = YamlEffectLoader(dataFolder, Logger.getAnonymousLogger())
        val (atoms, layered) = sources.partition { it.raw?.let(classifier::isAtom) == true }
        atoms.forEach(::parse)
        layered.forEach(::parse)

        val available = external.keys + sources.mapNotNull { it.effect?.name?.lowercase() }
        val graph = sources.mapNotNull { source ->
            (source.effect as? ScriptedEffect)?.let { it.name.lowercase() to it.emittedEffects.map(String::lowercase) }
        }.toMap()
        val cyclic = EmitterGraph.cyclic(graph)
        sources.forEach { source ->
            val references = graph[source.id.lowercase()].orEmpty()
            references.filter { it !in available }.distinct().forEach {
                source.issues += ValidationIssue("Unknown emitted effect '$it'.")
            }
            if (source.id.lowercase() in cyclic) {
                source.issues += ValidationIssue("Emitters/then form a cycle that spawns this effect again.")
            }
        }
        return sources.map { EffectValidation(it.file.relativePath, it.id, it.issues.toList()) }
    }
}
