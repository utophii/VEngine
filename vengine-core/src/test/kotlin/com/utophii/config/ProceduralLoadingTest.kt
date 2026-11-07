package com.utophii.config

import com.utophii.api.EffectOptions
import com.utophii.effects.PointEffect
import com.utophii.effects.ScriptedEffect
import com.utophii.engine.FXEngine
import org.bukkit.Location
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

class ProceduralLoadingTest {
    @TempDir lateinit var directory: File
    private val warnings = mutableListOf<String>()
    private val logger = Logger.getAnonymousLogger().apply {
        useParentHandlers = false
        addHandler(object : Handler() {
            override fun publish(record: LogRecord) { warnings += record.message }
            override fun flush() = Unit
            override fun close() = Unit
        })
    }
    private val loader get() = YamlEffectLoader(directory, logger)
    private val center = Location(null, 0.0, 0.0, 0.0)

    @AfterEach fun cleanup() {
        FXEngine.clearScripted()
        FXEngine.clearParametricPrimitives()
    }

    private fun source(name: String, contents: String): File = File(directory, "effects/$name.yml").apply {
        parentFile.mkdirs()
        writeText(contents.trimIndent())
    }

    @Test
    fun `each of the three bundled examples loads and calculates without other files`() {
        val files = File("../vengine-plugin/src/main/resources/effects").listFiles()!!.filter { it.extension == "yml" }
        assertEquals(setOf("basic", "animated", "display"), files.map { it.nameWithoutExtension }.toSet())
        for (file in files) {
            val loaded = loader.load(file)
            assertNotNull(loaded, file.name)
            for (time in listOf(0.0, 20.0, 70.0)) {
                val points = loaded!!.calculate(center, EffectOptions(), time)
                assertTrue(points.isNotEmpty(), "${file.name} at $time")
                assertTrue(points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
            }
        }
        assertTrue(warnings.isEmpty(), warnings.toString())
    }

    @Test
    fun `inline geometry is private and reload does not replace objects owned by older scripts`() {
        val file = source("local", """
            functions: {'twice(v)': '2*v'}
            layers:
              - points:
                  samples: 1
                  output: {x: 'twice(3)', y: '0', z: '0'}
        """)
        val before = loader.loadAll().single() as ScriptedEffect
        assertNull(FXEngine.primitiveEffect("local"))
        assertEquals(6.0, before.calculate(center, EffectOptions(), 0.0).single().x)
        file.writeText(file.readText().replace("twice(3)", "twice(5)"))
        val after = loader.loadAll().single()
        assertEquals(10.0, after.calculate(center, EffectOptions(), 0.0).single().x)
        assertEquals(6.0, before.calculate(center, EffectOptions(), 0.0).single().x)
    }

    @Test
    fun `named procedural atoms still resolve before scripts and disappear on reload`() {
        source("z_atom", "id: custom\npoints: {samples: 1, output: {x: '7', y: '0', z: '0'}}")
        source("a_script", "layers: [{effect: custom}]")
        assertEquals(2, loader.loadAll().size)
        assertEquals(7.0, FXEngine.effect("a_script")!!.calculate(center, EffectOptions(), 0.0).single().x)
        assertTrue(FXEngine.primitiveEffect("custom") is PointEffect)
        File(directory, "effects/z_atom.yml").delete()
        loader.loadAll()
        assertNull(FXEngine.effect("custom"))
    }

    @Test
    fun `provider-created generators survive reload and reappear after removing a YAML override`() {
        val name = "provider_${System.nanoTime()}"
        val provided = PointEffect(name, "1", mapOf("x" to "9", "y" to "0", "z" to "0"))
        FXEngine.registerEffect(provided)
        val file = source(name, "points: {samples: 1, output: {x: '3', y: '0', z: '0'}}")
        loader.loadAll()
        assertEquals(3.0, FXEngine.effect(name)!!.calculate(center, EffectOptions(), 0.0).single().x)
        file.delete()
        loader.loadAll()
        assertSame(provided, FXEngine.effect(name))
    }

    @Test
    fun `invalid generator settings and ambiguous layer sources produce warnings`() {
        val invalid = listOf(
            "points: {samples: 3}",
            "points: {output: {x: 'bad(x)', y: '0', z: '0'}}",
            "ode: {initial: {x: 0}, derivatives: {y: 1}}",
            "ode: {initial: {x: 0}, derivatives: {x: 1}, method: magic}",
            "iteration: {initial: {x: 0}, branches: []}",
            "field: {f: 'u', domain: triangle}",
            "points: {output: {x: '0', y: '0', z: '0'}}\ncurve: {x: '1'}",
            "layers: [{effect: unknown, curve: {x: '1'}}]",
        )
        invalid.forEachIndexed { i, contents -> assertNull(loader.load(source("invalid_$i", contents)), contents) }
        assertTrue(warnings.size >= invalid.size)
    }

    @Test
    fun `all archived recipes load together with their optional shape assets`() {
        File("src/test/resources/recipes").listFiles()!!.forEach { source(it.nameWithoutExtension, it.readText()) }
        File("src/test/resources/shapes").copyRecursively(File(directory, "shapes"), overwrite = true)
        val loaded = loader.loadAll()
        assertEquals(46, loaded.size, warnings.toString())
        assertTrue(warnings.isEmpty(), warnings.toString())
        loaded.forEach { effect ->
            assertTrue(effect.calculate(center, EffectOptions(), 20.0).isNotEmpty(), effect.name)
        }
    }
}
