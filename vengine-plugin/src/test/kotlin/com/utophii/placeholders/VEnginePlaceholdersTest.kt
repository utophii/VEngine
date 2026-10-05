package com.utophii.placeholders

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// placeholder rules are pure data, so they are checked without a server or PlaceholderAPI on the classpath
class VEnginePlaceholdersTest {

    @Test
    fun `global counters and lists come from the snapshot`() {
        val resolved = VEnginePlaceholders.resolve("effects", snapshot())
        assertEquals("3", resolved)
        assertEquals("helix, sphere, torus", VEnginePlaceholders.resolve("effects_list", snapshot()))
        assertEquals("2", VEnginePlaceholders.resolve("active", snapshot()))
        assertEquals("helix#2, torus#1", VEnginePlaceholders.resolve("ACTIVE_LIST", snapshot()))
        assertEquals("Helix, torus", VEnginePlaceholders.resolve("active_names", snapshot()))
        assertEquals("64", VEnginePlaceholders.resolve("view_distance", snapshot()))
        assertNull(VEnginePlaceholders.resolve("version", snapshot()))
        assertNull(VEnginePlaceholders.resolve("has_torus", snapshot()))
        assertNull(VEnginePlaceholders.resolve("count_helix", snapshot()))
        assertNull(VEnginePlaceholders.resolve("playing_helix", snapshot()))
        assertNull(VEnginePlaceholders.resolve("world_world", snapshot()))
    }

    @Test
    fun `player tokens need a viewer and otherwise stay unresolved`() {
        assertNull(VEnginePlaceholders.resolve("nearest", snapshot()))
        assertNull(VEnginePlaceholders.resolve("nearest_id", snapshot()))
        assertNull(VEnginePlaceholders.resolve("nearest_distance", snapshot()))
        assertNull(VEnginePlaceholders.resolve("nearby", snapshot()))
        assertNull(VEnginePlaceholders.resolve("not_a_token", snapshot()))
        assertNull(VEnginePlaceholders.resolve("   ", snapshot()))
    }

    @Test
    fun `nearest ignores other worlds and breaks ties by id`() {
        val viewer = VEnginePlaceholders.Viewer("world", 0.0, 0.0, 0.0)
        assertEquals("torus", VEnginePlaceholders.resolve("nearest", snapshot(), viewer))
        assertEquals("torus#1", VEnginePlaceholders.resolve("nearest_id", snapshot(), viewer))
        assertEquals("3", VEnginePlaceholders.resolve("nearest_distance", snapshot(), viewer))
        assertEquals("1", VEnginePlaceholders.resolve("nearby", snapshot(), viewer))

        val tied = snapshot().copy(
            running = listOf(
                running("zeta#2", "zeta", "world", 5.0, 0.0, 0.0),
                running("alpha#1", "alpha", "world", 5.0, 0.0, 0.0),
            ),
        )
        assertEquals("alpha", VEnginePlaceholders.resolve("nearest", tied, viewer))
        assertEquals("alpha#1", VEnginePlaceholders.resolve("nearest_id", tied, viewer))
    }

    @Test
    fun `nearby includes the view-distance boundary and nothing beyond it`() {
        val viewer = VEnginePlaceholders.Viewer("world", 0.0, 0.0, 0.0)
        val edge = snapshot().copy(
            viewDistance = 10.0,
            running = listOf(
                running("edge#1", "edge", "world", 10.0, 0.0, 0.0),
                running("far#2", "far", "world", 10.1, 0.0, 0.0),
            ),
        )
        assertEquals("1", VEnginePlaceholders.resolve("nearby", edge, viewer))
        assertEquals("", VEnginePlaceholders.resolve("nearest", snapshot(), VEnginePlaceholders.Viewer("end", 0.0, 0.0, 0.0)))
    }

    @Test
    fun `every documented placeholder resolves`() {
        val viewer = VEnginePlaceholders.Viewer("world", 0.0, 0.0, 0.0)
        VEnginePlaceholders.documented.forEach { token ->
            val params = token.removePrefix("%vengine_").removeSuffix("%")
            val resolved = VEnginePlaceholders.resolve(params, snapshot(), viewer)
            assertEquals(false, resolved == null, token)
        }
    }

    private fun snapshot(): VEnginePlaceholders.Snapshot {
        return VEnginePlaceholders.Snapshot(
            effectNames = listOf("helix", "sphere", "torus"),
            viewDistance = 64.0,
            running = listOf(
                running("torus#1", "torus", "world", 3.0, 0.0, 0.0),
                running("helix#2", "Helix", "world_nether", 0.0, 80.0, 0.0),
            ),
        )
    }

    private fun running(id: String, name: String, world: String, x: Double, y: Double, z: Double): VEnginePlaceholders.Running {
        return VEnginePlaceholders.Running(id, name, world, x, y, z)
    }
}
