package com.utophii.config

import com.utophii.KeepSteps
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeepStepsTest {

    @Test
    fun `integer and fractional thresholds both load`() {
        val config = YamlConfiguration()
        config.loadFromString(
            """
            levels:
              0.1: 0.25
              40: 0.75
              47.5: 0.5
              60: 2
            """.trimIndent(),
        )
        val invalid = mutableListOf<String>()
        val steps = KeepSteps.parse(config.getConfigurationSection("levels")!!, invalid::add).sortedBy { it.first }
        assertEquals(listOf(0.1 to 0.25, 40.0 to 0.75, 47.5 to 0.5, 60.0 to 1.0), steps)
        assertEquals(emptyList<String>(), invalid)
    }

    @Test
    fun `non-numeric entries are reported`() {
        val config = YamlConfiguration()
        config.loadFromString("lod:\n  near: 1.0\n  16: high\n")
        val invalid = mutableListOf<String>()
        assertEquals(emptyList<Pair<Double, Double>>(), KeepSteps.parse(config.getConfigurationSection("lod")!!, invalid::add))
        assertEquals(setOf("near", "16"), invalid.toSet())
    }
}
