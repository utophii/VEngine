package com.utophii.effects

import com.utophii.api.EffectOptions
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.Yaml
import java.io.File

class ProceduralReferenceTest {
    @Test
    fun `generic generators preserve captured geometry and gradients of all former builtins`() {
        val cases = File("src/test/resources/procedural/reference.yml").inputStream().use {
            Yaml().load<List<Map<String, Any>>>(it)
        }
        assertEquals(14, cases.map { it.getValue("effect") }.distinct().size)
        for (case in cases) {
            val name = case.getValue("effect").toString()
            val time = (case.getValue("time") as Number).toDouble()
            val parameters = (case.getValue("parameters") as Map<*, *>).entries.associate { it.key.toString() to (it.value as Number).toDouble() }
            val expected = case.getValue("points") as List<*>
            val center = Location(null, 2.0, 3.0, 5.0)
            val buffer = ParticleBuffer(center)
            ProceduralFixtures.effect(name).calculateInto(buffer, center,
                EffectOptions(parameters = parameters, duration = 100L, scale = 1.25, rotationRoll = 0.2), time)
            val label = "$name at $time"
            assertEquals(expected.size, buffer.list().size, label)
            assertEquals(case.getValue("values"), buffer.hasValues(), label)
            expected.forEachIndexed { i, row ->
                val values = (row as List<*>).map { (it as Number).toDouble() }
                val actual = buffer.location(i)
                assertEquals(values[0], actual.x, 1e-8, "$label x[$i]")
                assertEquals(values[1], actual.y, 1e-8, "$label y[$i]")
                assertEquals(values[2], actual.z, 1e-8, "$label z[$i]")
                if (buffer.hasValues()) assertEquals(values[3], buffer.valueAt(i), 1e-8, "$label value[$i]")
            }
        }
    }
}
