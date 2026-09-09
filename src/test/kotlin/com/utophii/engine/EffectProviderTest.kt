package com.utophii.engine

import com.utophii.api.EffectHandle
import com.utophii.api.EffectOptions
import com.utophii.api.EffectProvider
import com.utophii.api.ParticleEffect
import com.utophii.api.SimpleEffectHandle
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// verifies the public SPI: an external EffectProvider can register custom effects into the registry
class EffectProviderTest {

    private class TestEffect(val id: String) : ParticleEffect {
        override val name: String = id
        override fun play(center: Location, opts: EffectOptions): EffectHandle =
            SimpleEffectHandle("$id#test", id) { }

        override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> = emptyList()
    }

    @Test
    fun `registerProvider makes its effects available to the registry`() {
        // FXEngine is a process-wide singleton; a unique name avoids cross-test collisions
        val name = "spi_test_${System.nanoTime()}"
        val effect = TestEffect(name)
        FXEngine.registerProvider(EffectProvider { listOf(effect) })

        val registered = FXEngine.effect(name)
        assertNotNull(registered, "provider effect must be registered")
        assertEquals(name, registered?.name)
        assertTrue(FXEngine.effectNames().contains(name))
    }

    @Test
    fun `provider effects can be used as layer bases`() {
        val name = "spi_atom_${System.nanoTime()}"
        FXEngine.registerProvider(EffectProvider { listOf(TestEffect(name)) })

        // primitive (atom) effect, so it must be resolvable and usable by layered scripts
        assertNotNull(FXEngine.primitiveEffect(name))
    }
}
