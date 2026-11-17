package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.ParticleEffect
import com.utophii.config.YamlEffectLoader
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

internal object YamlEffectFixtures {
    val names = listOf("points", "curve", "surface", "ode", "iteration", "field", "layered")
    private val directory = File("src/test/resources/golden")

    data class Input(
        val name: String,
        val time: Double,
        val options: EffectOptions = EffectOptions(duration = 12),
        val x: Double = 2.0,
        val y: Double = -1.0,
        val z: Double = 3.0,
    ) {
        fun center(): Location = Location(null, x, y, z)
    }

    val inputs = listOf(
        Input("initial", 0.0),
        Input("animated", 2.0),
        Input("overridden", 5.0, EffectOptions(
            duration = 10,
            parameters = mapOf("radius" to 1.75, "bias" to -0.4, "points" to 6.0, "life" to 5.0),
            scale = 1.25, rotationYaw = 0.2, rotationPitch = -0.15, rotationRoll = 0.3,
        )),
        Input("layers_finished", 8.0),
        Input("period_wrapped", 12.0),
    )

    // only immutable numbers leave the buffer, never pooled Location objects or list views
    data class Frame(val hasValues: Boolean, val points: List<List<Double?>>)

    fun load(name: String): ParticleEffect {
        val warnings = mutableListOf<String>()
        val logger = Logger.getAnonymousLogger().apply {
            useParentHandlers = false
            addHandler(object : Handler() {
                override fun publish(record: LogRecord) { warnings += record.message }
                override fun flush() = Unit
                override fun close() = Unit
            })
        }
        val effect = YamlEffectLoader(directory, logger) { null }.load(File(directory, "effects/$name.yml"))
        check(warnings.isEmpty()) { "$name: $warnings" }
        return checkNotNull(effect) { "Could not load golden fixture $name" }
    }

    fun capture(effect: ParticleEffect, input: Input): Frame {
        val center = input.center()
        if (effect !is AbstractParticleEffect) {
            // scriptedEffect.calculate exposes geometry only, appearance belongs to individual layers
            return Frame(false, effect.calculate(center, input.options, input.time).map {
                listOf(it.x, it.y, it.z, null, null)
            })
        }
        val buffer = ParticleBufferPool.obtain(center)
        try {
            effect.calculateInto(buffer, center, input.options, input.time)
            return Frame(buffer.hasValues(), buffer.list().mapIndexed { index, point ->
                listOf(point.x, point.y, point.z,
                    buffer.valueAt(index).takeUnless(Double::isNaN),
                    buffer.ageAt(index).takeUnless(Double::isNaN))
            })
        } finally {
            ParticleBufferPool.release(buffer)
        }
    }

    fun assertFrame(expected: Frame, actual: Frame, label: String) {
        assertEquals(expected.hasValues, actual.hasValues, "$label hasValues")
        assertEquals(expected.points.size, actual.points.size, "$label particle count")
        expected.points.zip(actual.points).forEachIndexed { index, (want, got) ->
            assertEquals(5, want.size, "$label golden row $index must contain x, y, z, value, age")
            want.zip(got).forEachIndexed { axis, (a, b) ->
                val coordinate = "$label particle[$index].${listOf("x", "y", "z", "value", "age")[axis]}"
                if (a == null) {
                    assertTrue(axis >= 3, "$coordinate must be present")
                    assertEquals(null, b, coordinate)
                } else {
                    assertTrue(a.isFinite(), "$coordinate golden must be finite")
                    assertTrue(b != null && b.isFinite(), "$coordinate must be finite, got $b")
                    assertEquals(a, b!!, 1e-8, coordinate)
                }
            }
        }
    }

    fun readGolden(name: String): Map<String, Frame> {
        val file = File(directory, "snapshots/$name.yml")
        check(file.isFile) { "Missing golden $file. See src/test/resources/golden/README.md" }
        val raw = file.inputStream().use { Yaml().load<Map<String, Map<String, Any>>>(it) }
        assertEquals(inputs.map(Input::name).toSet(), raw.keys, "$name golden frames")
        return raw.mapValues { (_, frame) ->
            Frame(frame.getValue("hasValues") as Boolean, (frame.getValue("points") as List<*>).map { row ->
                (row as List<*>).map { (it as Number?)?.toDouble() }
            })
        }
    }

    // invoked only by the explicit updateEffectGoldens Gradle task
    @JvmStatic
    fun main(args: Array<String>) {
        val snapshots = File(directory, "snapshots").apply { mkdirs() }
        for (name in names) {
            val effect = load(name)
            val text = buildString {
                appendLine("# Captured YAML geometry, each row is [x, y, z, value, age].")
                for (input in inputs) {
                    val frame = capture(effect, input)
                    assertFrame(frame, frame, "$name/${input.name}")
                    appendLine("${input.name}:")
                    appendLine("  hasValues: ${frame.hasValues}")
                    if (frame.points.isEmpty()) appendLine("  points: []")
                    else {
                        appendLine("  points:")
                        frame.points.forEach { appendLine("    - ${it.joinToString(prefix = "[", postfix = "]")}") }
                    }
                }
            }
            File(snapshots, "$name.yml").writeText(text)
        }
    }
}