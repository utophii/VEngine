package com.utophii.config

import com.utophii.api.ParametricConfig
import com.utophii.api.DisplayOptions
import com.utophii.api.EffectOptions
import com.utophii.effects.AbstractParticleEffect
import com.utophii.effects.DisplayOrientation
import com.utophii.effects.ParticleBuffer
import com.utophii.engine.FXEngine
import org.bukkit.Location
import org.bukkit.entity.Display
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.math.abs

class OrientationLoadingTest {
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
    private val fixedText = EffectOptions(display = DisplayOptions(type = DisplayOptions.Type.TEXT, billboard = Display.Billboard.FIXED))

    @AfterEach fun cleanup() = FXEngine.shutdown()

    private fun source(name: String, contents: String): File = File(directory, "effects/$name.yml").apply {
        parentFile.mkdirs()
        writeText(contents.trimIndent())
    }

    private fun rotations(effect: AbstractParticleEffect, opts: EffectOptions = fixedText, time: Double = 0.0): FloatArray? {
        val buffer = ParticleBuffer(center)
        effect.calculateInto(buffer, center, opts, time)
        return buffer.orientations()
    }

    private fun rotation(data: FloatArray, index: Int) = Quaternionf(data[index * 4], data[index * 4 + 1], data[index * 4 + 2], data[index * 4 + 3])

    // the three examples of docs/specs/per-point-display-orientation.md
    private fun examples() {
        source("outward_letters", """
            id: outward_letters
            duration: 200
            curve:
              variables: [t]
              samples: 12
              angularSpeed: 0
              x: '2 * cos(t)'
              y: 0
              z: '2 * sin(t)'
              normalX: 'cos(t)'
              normalY: 0
              normalZ: 'sin(t)'
            display:
              type: text
              text: 'F'
              billboard: fixed
        """)
        source("rotating_marks", """
            id: rotating_marks
            duration: 200
            points:
              samples: 5
              output:
                x: 'index - 2'
                y: 0
                z: 0
                rotZ: 'index * pi / 4 + time * 0.05'
            display:
              type: text
              text: 'F'
              billboard: fixed
        """)
        source("cylinder_labels", """
            id: cylinder_labels
            duration: 200
            surface:
              variables: [u, v]
              samples: [24, 4]
              angularSpeed: 0
              range:
                - {min: 0, max: 6.283185307179586}
                - {min: -1, max: 1}
              x: '2 * cos(u)'
              y: 'v'
              z: '-2 * sin(u)'
              alignToNormal: outward
            display:
              type: text
              text: 'F'
              billboard: fixed
        """)
    }

    @Test
    fun `the specification examples load, validate and orient their models`() {
        examples()
        loader.loadAll()
        assertTrue(warnings.isEmpty(), warnings.toString())
        val reports = loader.validate()
        assertTrue(reports.all { it.valid && it.issues.isEmpty() }, reports.toString())

        val letters = FXEngine.primitiveEffect("outward_letters") as AbstractParticleEffect
        val ring = rotations(letters)!!
        assertVector(Vector3f(1f, 0f, 0f), rotation(ring, 0).transform(Vector3f(0f, 0f, 1f)))
        assertVector(Vector3f(0f, 0f, 1f), rotation(ring, 3).transform(Vector3f(0f, 0f, 1f)))

        val marks = FXEngine.primitiveEffect("rotating_marks") as AbstractParticleEffect
        assertRotation(Quaternionf().rotateZ((Math.PI / 2 + 1.0).toFloat()), rotation(rotations(marks, time = 20.0)!!, 2))

        val cylinder = FXEngine.primitiveEffect("cylinder_labels") as AbstractParticleEffect
        assertVector(Vector3f(1f, 0f, 0f), rotation(rotations(cylinder)!!, 0).transform(Vector3f(0f, 0f, 1f)), 0.01f)
    }

    @Test
    fun `a named atom orients for a layer that gives it a fixed text display and not for particles`() {
        source("ring_atom", """
            curve:
              samples: 8
              x: 'cos(t)'
              y: 0
              z: 'sin(t)'
              normalX: 'cos(t)'
              normalY: 0
              normalZ: 'sin(t)'
        """)
        source("ring_show", """
            layers:
              - effect: ring_atom
                display: {type: text, text: 'F', billboard: fixed}
              - effect: ring_atom
                particle: END_ROD
        """)
        loader.loadAll()
        assertTrue(warnings.isEmpty(), warnings.toString())
        val atom = FXEngine.primitiveEffect("ring_atom") as AbstractParticleEffect
        assertNotNull(rotations(atom))
        assertNull(rotations(atom, EffectOptions()))
        val inline = source("inline_ring", """
            layers:
              - points:
                  samples: 3
                  output: {x: 'index', y: 0, z: 0, rotY: 'index'}
                display: {type: item}
        """)
        assertNotNull(loader.load(inline))
        assertTrue(warnings.isEmpty(), warnings.toString())
    }

    @Test
    fun `load errors name the file, the layer and the key`() {
        val cases = listOf(
            """
            curve: {x: 't', normalX: 'cos(t)', normalZ: 'sin(t)'}
            """ to listOf("curve.normalY", "missing"),
            """
            curve: {x: 't', normalX: 1, normalY: 0, normalZ: 0, rotZ: 1}
            """ to listOf("curve sets more than one orientation", "normalX", "rotZ"),
            """
            surface: {x: 'u', y: 'v', rotX: 1, alignToNormal: outward}
            """ to listOf("surface sets more than one orientation", "rotX", "alignToNormal"),
            """
            curve: {x: 't', alignToNormal: outward}
            """ to listOf("curve.alignToNormal is not supported"),
            """
            points: {samples: 2, alignToNormal: outward, output: {x: index, y: 0, z: 0}}
            """ to listOf("points.alignToNormal is not supported"),
            """
            surface: {x: 'u', y: 'v', alignToNormal: sideways}
            """ to listOf("surface.alignToNormal must be outward or inward", "sideways"),
            """
            surface: {variables: [u, v, w], x: 'u', y: 'v', alignToNormal: inward}
            """ to listOf("surface.alignToNormal needs exactly two surface variables", "3"),
            """
            curve: {x: 't', rotZ: ''}
            """ to listOf("curve.rotZ must be a number or a nonempty formula"),
            """
            curve: {x: 't', rotZ: [1, 2]}
            """ to listOf("curve.rotZ must be a number or a nonempty formula"),
            """
            ode: {initial: {x: 1}, derivatives: {x: '-x'}, output: {x: x, y: 0, z: 0, normalX: 1}}
            """ to listOf("ode.output.normalX is not supported"),
            """
            layers:
              - curve: {x: 't'}
              - points:
                  samples: 2
                  output: {x: index, y: 0, z: 0, rotY: 'sin(('}
            """ to listOf("layers[1].points.output.rotY", "invalid formula"),
            """
            layers:
              - effect: missing_but_irrelevant
              - surface: {x: 'u', y: 'v', normalX: 1, normalY: 0}
            """ to listOf("layers[1].surface.normalZ", "missing"),
        )
        cases.forEachIndexed { index, (yaml, expected) ->
            warnings.clear()
            val file = source("broken_$index", yaml)
            loader.load(file)
            val message = warnings.joinToString(" | ")
            assertTrue(message.contains("broken_$index.yml"), message)
            expected.forEach { part -> assertTrue(message.contains(part), "case $index: expected '$part' in $message") }
        }
    }

    @Test
    fun `the schema accepts orientation fields and rejects conflicts and misplaced modes`() {
        fun problems(yaml: String) = EffectSchema.problems(Yaml().load<Any>(yaml.trimIndent()))
        assertEquals(emptyList<String>(), problems("curve: {x: t, normalX: 1, normalY: 0, normalZ: 0}"))
        assertEquals(emptyList<String>(), problems("surface: {x: u, rotZ: 'time'}"))
        assertEquals(emptyList<String>(), problems("layers: [{surface: {x: u, alignToNormal: inward}}]"))
        assertEquals(emptyList<String>(), problems("points: {output: {x: index, y: 0, z: 0, rotX: 1}}"))
        for (yaml in listOf(
            "curve: {x: t, normalX: 1, normalZ: 0}",
            "curve: {x: t, normalX: 1, normalY: 0, normalZ: 0, rotY: 1}",
            "surface: {x: u, rotX: 1, alignToNormal: outward}",
            "curve: {x: t, alignToNormal: outward}",
            "layers: [{curve: {x: t, alignToNormal: outward}}]",
            "surface: {variables: [u], x: u, alignToNormal: outward}",
            "surface: {x: u, alignToNormal: sideways}",
            "points: {alignToNormal: outward, output: {x: index, y: 0, z: 0}}",
            "points: {output: {x: index, y: 0, z: 0, alignToNormal: outward}}",
            "field: {f: x, output: {x: u, y: 0, z: v, rotZ: 1}}",
        )) {
            assertTrue(problems(yaml).isNotEmpty(), yaml)
        }
    }

    @Test
    fun `the DSL builds the same orientations and rejects mixed modes`() {
        val effect = ParametricConfig().apply {
            variables("u", "v")
            samples(8, 4)
            x("u")
            y("v")
            z("0")
            alignToNormal(DisplayOrientation.Side.INWARD)
        }.buildEffect()
        assertVector(Vector3f(0f, 0f, -1f), rotation(rotations(effect)!!, 5).transform(Vector3f(0f, 0f, 1f)))

        val angles = ParametricConfig().apply {
            x("t")
            angles(z = "0.5")
        }.buildEffect()
        assertRotation(Quaternionf().rotateZ(0.5f), rotation(rotations(angles)!!, 2))

        val normal = ParametricConfig().apply {
            x("cos(t)")
            z("sin(t)")
            normal("cos(t)", "0", "sin(t)")
        }.buildEffect()
        assertVector(Vector3f(1f, 0f, 0f), rotation(rotations(normal)!!, 0).transform(Vector3f(0f, 0f, 1f)))

        assertThrows<IllegalArgumentException> {
            ParametricConfig().apply {
                normal("1", "0", "0")
                angles(z = "1")
            }
        }
        assertThrows<IllegalArgumentException> { ParametricConfig().apply { alignToNormal() }.buildEffect() }
    }

    private fun assertVector(expected: Vector3f, actual: Vector3f, tolerance: Float = 1e-4f) {
        assertTrue(Vector3f(expected).sub(actual).length() < tolerance, "expected $expected, got $actual")
    }

    private fun assertRotation(expected: Quaternionf, actual: Quaternionf) {
        assertTrue(abs(expected.dot(actual)) > 1f - 1e-5f, "expected $expected, got $actual")
    }
}
