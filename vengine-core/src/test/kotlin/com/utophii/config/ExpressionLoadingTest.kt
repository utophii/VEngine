package com.utophii.config

import com.utophii.api.EffectOptions
import com.utophii.api.ParametricConfig
import com.utophii.effects.ImplicitEffect
import com.utophii.engine.FXEngine
import com.utophii.engine.PlaceholderExpression
import com.utophii.math.ExpressionFunctions
import org.bukkit.Location
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.math.sqrt

class ExpressionLoadingTest {
    @TempDir
    lateinit var directory: File
    private val messages = mutableListOf<String>()
    private val logger = Logger.getAnonymousLogger().apply {
        useParentHandlers = false
        addHandler(object : Handler() {
            override fun publish(record: LogRecord) { messages += record.message }
            override fun flush() = Unit
            override fun close() = Unit
        })
    }
    private val center = Location(null, 0.0, 0.0, 0.0)

    @AfterEach
    fun cleanup() {
        FXEngine.clearScripted()
        FXEngine.clearParametricPrimitives()
    }

    private fun file(name: String, contents: String): File = File(directory, "effects/$name.yml").apply {
        parentFile.mkdirs()
        writeText(contents.trimIndent())
    }

    @Test
    fun `local functions survive bulk loading and stay local across atoms and layers`() {
        file("first", """
            id: local_first
            functions:
              'height(u, a)': 'a * u'
            curve:
              samples: 4
              angularSpeed: 0
              range: [{min: 0, max: 1}]
              x: 'if(t == 0, 0, 1 / t)'
              y: 'mix(0, height(time, A), clamp(t, 0, 1))'
              z: 'noise(t, 0, 0)'
              parameters: {A: 2}
        """)
        file("second", """
            id: local_second
            functions:
              'height(u, a)': 'a * u + 100'
            curve:
              samples: 1
              x: 'height(3, 2)'
        """)
        file("script", """
            id: local_script
            functions:
              'twice(v)': 'v * 2'
            duration: 'twice(5)'
            layers:
              - effect: local_first
                scale: 'twice(2)'
                parameters: {A: 'twice(3)'}
        """)
        val loader = YamlEffectLoader(directory, logger)
        assertEquals(3, loader.loadAll().size)
        val first = FXEngine.effect("local_first")!!
        assertEquals(1.5, first.calculate(center, EffectOptions(), 3.0)[1].y, 1e-9)
        val options = EffectOptions(parameters = mapOf("A" to 4.0))
        assertEquals(3.0, first.calculate(center, options, 3.0)[1].y, 1e-9)
        assertEquals(106.0, FXEngine.effect("local_second")!!.calculate(center, EffectOptions(), 0.0)[0].x, 1e-9)
        val script = FXEngine.effect("local_script")!!
        assertEquals(18.0, script.calculate(center, EffectOptions(), 3.0)[1].y, 1e-9)
        assertTrue(script.calculate(center, EffectOptions(), 11.0).isEmpty())
        assertTrue(messages.isEmpty(), messages.toString())
    }

    @Test
    fun `single file sdf functions retain time dependency`() {
        val source = file("sdf", """
            functions:
              'sphere(a, b, c, r)': 'sdSphere(a, b, c, r)'
              'offset(u)': 'u * 0.01'
            sdf:
              f: 'sphere(x - offset(time), y, z, 0.5)'
              value: 'clamp(y + 0.5, 0, 1)'
              bounds: 1
              points: 32
              angularSpeed: 0
        """)
        val effect = YamlEffectLoader(directory, logger).load(source) as ImplicitEffect
        for (time in listOf(0.0, 25.0)) {
            val positions = effect.calculate(center, EffectOptions(), time)
            assertTrue(positions.isNotEmpty())
            positions.forEach { p ->
                val x = p.x - time * 0.01
                assertEquals(0.5, sqrt(x * x + p.y * p.y + p.z * p.z), 1e-3)
            }
        }
    }

    @Test
    fun `bad functions skip the file and report the cause`() {
        val loader = YamlEffectLoader(directory, logger)
        val invalid = listOf(
            "functions: []" to "must map signatures",
            "functions: {'bad(x)': 'other(x)'}" to "Unknown function",
            "functions: {'bad(x)': 'bad(x)'}" to "Recursive functions",
            "functions: {'bad(x)': 'x + time'}" to "Unknown function parameter",
            "functions: {'bad(x)': 2}" to "must be strings",
            "functions: {'bad(x)': 'x', 'bad(x)': '2 * x'}" to "duplicate key",
            "functions: {'bad(x)': 'x'}\ncurve: {x: 'bad(1, 2)'}" to "expects 1 arguments",
        )
        invalid.forEachIndexed { index, (yaml, error) ->
            val source = file("invalid$index", if ("curve:" in yaml) yaml else "$yaml\ncurve: {x: '0'}")
            assertNull(loader.load(source))
            assertTrue(messages.last().contains(error), messages.last())
        }
        assertNotNull(loader.load(file("good", "curve: {x: 'mix(0, 2, 0.5)'}")))
    }

    @Test
    fun `DSL and placeholder expressions accept local functions`() {
        val effect = ParametricConfig().apply {
            function("wave(u, a)", "a * sin(u)")
            samples(1)
            x("wave(time, amplitude)")
            default("amplitude", 3.0)
        }.buildEffect("local_dsl")
        assertEquals(3.0, effect.calculate(center, EffectOptions(), Math.PI / 2)[0].x, 1e-9)
        val functions = ExpressionFunctions(mapOf("inverse(x)" to "if(x == 0, 0, 1 / x)"))
        val expression = PlaceholderExpression.compile("inverse(value)", functions)!!
        assertEquals(0.5, expression.evaluate(null, mapOf("value" to 2.0)))
        assertTrue(PlaceholderExpression.compile("inverse(%player_health%)", functions)!!.hasPlaceholders)
    }

    @Test
    fun `bundled formula atoms still load`() {
        val resources = File("../vengine-plugin/src/main/resources/effects")
        val yaml = Yaml()
        val loader = YamlEffectLoader(directory, logger)
        val sources = resources.walkTopDown().filter { it.isFile && it.extension == "yml" }.filter { source ->
            val raw = source.inputStream().use { yaml.load<Map<String, Any?>>(it) }
            raw.keys.any { it in setOf("curve", "surface", "sdf") }
        }.toList()
        assertTrue(sources.size > 10, "Expected bundled formula fixtures")
        sources.forEach { assertNotNull(loader.load(it), "${it.name}: $messages") }
    }

    @Test
    fun `standalone atom formulas preserve duration overrides`() {
        file("duration", """
            id: local_duration
            functions: {'twice(u)': 'u * 2'}
            duration: 5
            scale: 'twice(2)'
            parameters: {radius: 'twice(3)'}
            curve:
              samples: 4
              x: 'radius'
        """)
        YamlEffectLoader(directory, logger).loadAll()
        val effect = FXEngine.effect("local_duration")!!
        assertTrue(effect.calculate(center, EffectOptions(), 6.0).isEmpty())
        val extended = effect.calculate(center, EffectOptions(duration = 10), 6.0)
        assertEquals(4, extended.size)
        assertEquals(24.0, extended[0].x, 1e-9)
    }
}
