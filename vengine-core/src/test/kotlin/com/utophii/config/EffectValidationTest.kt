package com.utophii.config

import com.utophii.effects.PointEffect
import com.utophii.engine.FXEngine
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.logging.Logger
import kotlin.test.*

class EffectValidationTest {
    @TempDir lateinit var directory: File
    private val loader get() = YamlEffectLoader(directory, Logger.getAnonymousLogger())

    @AfterEach fun cleanup() = FXEngine.shutdown()

    private fun source(name: String, yaml: String) = File(directory, "effects/$name.yml").apply {
        parentFile.mkdirs()
        writeText(yaml.trimIndent())
    }

    @Test fun `bundled examples and all recipe files satisfy the schema`() {
        val files = File("../vengine-plugin/src/main/resources/effects").listFiles()!!.toList() +
            File("src/test/resources/recipes").listFiles()!!.toList()
        assertTrue(files.size > 40)
        files.filter { it.extension == "yml" }.forEach { file ->
            val raw = file.inputStream().use { Yaml().load<Any>(it) }
            assertEquals(emptyList(), EffectSchema.problems(raw), file.name)
        }
    }

    @Test fun `validation resolves new atoms before scripts without changing the live registry`() {
        source("existing", "curve: {x: '1'}")
        loader.loadAll()
        val existing = FXEngine.effect("existing")
        val primitive = FXEngine.primitiveEffect("existing")
        val names = FXEngine.effectNames()
        source("z_atom", "id: NewAtom\ncurve: {x: '2'}")
        source("a_script", "layers: [{effect: newatom}]")

        val reports = loader.validate()
        assertEquals(3, reports.size)
        assertTrue(reports.all { it.valid && it.issues.isEmpty() }, reports.toString())
        assertEquals(names, FXEngine.effectNames())
        assertSame(existing, FXEngine.effect("existing"))
        assertSame(primitive, FXEngine.primitiveEffect("existing"))
        assertNull(FXEngine.effect("newatom"))
    }

    @Test fun `deleted YAML atoms are not resolved from the live registry`() {
        val file = source("old", "curve: {x: '1'}")
        loader.loadAll()
        file.delete()
        source("script", "layers: [{effect: old}]")
        val report = loader.validate().single()
        assertFalse(report.valid)
        assertTrue(report.issues.any { "unknown primitive" in it.message })
        assertNotNull(FXEngine.effect("old"))
    }

    @Test fun `external provider primitives remain available`() {
        val provider = PointEffect("provider", "1", mapOf("x" to "0", "y" to "0", "z" to "0"))
        FXEngine.registerPrimitive(provider)
        source("script", "layers: [{effect: provider}]")
        assertTrue(loader.validate().single().issues.isEmpty())
        assertSame(provider, FXEngine.primitiveEffect("provider"))
    }

    @Test fun `reports malformed YAML empty files wrong roots duplicate keys and schema paths`() {
        source("broken", "layers: [")
        source("empty", "")
        source("list", "- one\n- two")
        source("duplicate", "id: a\nid: b\ncurve: {}")
        source("typo", "layers: [{curve: {}, partcle: FLAME}]")
        source("wrong_type", "curve: {samples: false}")
        source("good", "curve: {}")
        val reports = loader.validate().associateBy { it.file }
        assertEquals(6, reports.values.count { !it.valid })
        assertTrue(reports.getValue("good.yml").valid)
        assertTrue(reports.getValue("typo.yml").issues.any { "partcle" in it.message && "layers" in it.message })
    }

    @Test fun `invalid formulas duplicate IDs missing shapes and emitter cycles are reported`() {
        source("formula", "curve: {x: 'sin('}")
        source("duplicate_a", "id: same\ncurve: {}")
        source("duplicate_b", "id: SAME\ncurve: {}")
        source("shape", "shape: {image: missing.png}")
        source("a", "layers: [{curve: {}}]\nthen: b")
        source("b", "layers: [{curve: {}}]\nthen: a")
        source("missing", "layers: [{curve: {}}]\nemitters: absent")
        val reports = loader.validate()
        assertEquals(7, reports.size)
        assertTrue(reports.none { it.valid }, reports.toString())
        assertTrue(reports.first { it.id == "a" }.issues.any { "cycle" in it.message })
        assertTrue(reports.first { it.id == "missing" }.issues.any { "Unknown emitted" in it.message })
    }

    @Test fun `schema rejects ambiguous geometry and malformed nested options`() {
        val cases = listOf(
            "curve: {}\nsurface: {}",
            "curve: {}\nlayers: [{curve: {}}]",
            "layers: [{effect: named, points: {output: {x: 1}}}]",
            "layers: [{curve: {}, modifiers: [{type: motion}]}]",
            "curve: {}\ndisplay: {type: unknown}",
            "curve: {}\nanimation: {tracks: [{target: scale, keyframes: [{tick: 0}]}]}",
            "shape: {image: a.png, text: b}",
            "ode: {initial: {x: 0}, derivatives: {x: 1}, collision: {substeps: 65}}",
        )
        cases.forEach { yaml -> assertTrue(EffectSchema.problems(Yaml().load<Any>(yaml)).isNotEmpty(), yaml) }
    }

    @Test fun `placeholder formulas custom functions legacy layers and aliases are accepted`() {
        source("atom", "curve: {x: 0}")
        source("legacy", "effect: atom\nscale: 'max(1, %player_level%)'")
        source("shared", """
            functions: {'twice(v)': 'v*2'}
            layers:
              - curve: &geometry {x: 'twice(t)'}
                duration: 20
              - curve: *geometry
        """)
        assertTrue(loader.validate().all { it.issues.isEmpty() })
    }

    @Test fun `empty directory is read without creating directories`() {
        assertEquals(emptyList(), loader.validate())
        assertFalse(File(directory, "effects").exists())
    }
}
