package com.utophii.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ParticleBudgetTest {

    @Test
    fun `without limits every particle is admitted`() {
        val budget = ParticleBudget()
        val load = budget.load(UUID.randomUUID())
        repeat(PARTICLES) { index ->
            assertTrue(budget.admit(load, ParticleBudget.rank(index), budget.baseKeep(0.0, lodEnabled = true), 1))
        }
    }

    @Test
    fun `player limit thins the next ticks down to the limit`() {
        val budget = ParticleBudget().apply { playerLimit = PARTICLES / 4 }
        val player = UUID.randomUUID()
        val other = UUID.randomUUID()
        frame(budget, player)
        budget.startTick()
        val admitted = frame(budget, player)
        assertEquals(PARTICLES / 4.0, admitted.toDouble(), PARTICLES * TOLERANCE)
        // another player has its own share
        assertEquals(PARTICLES, frame(budget, other))
    }

    @Test
    fun `global limit is shared by every player`() {
        val budget = ParticleBudget().apply { globalLimit = PARTICLES }
        val players = List(4) { UUID.randomUUID() }
        players.forEach { frame(budget, it) }
        budget.startTick()
        val admitted = players.sumOf { frame(budget, it) }
        assertEquals(PARTICLES.toDouble(), admitted.toDouble(), PARTICLES * TOLERANCE)
    }

    @Test
    fun `a lower fraction keeps a subset of a higher one`() {
        val half = (0 until PARTICLES).filter { ParticleBudget.rank(it) < 0.5 }.toSet()
        val quarter = (0 until PARTICLES).filter { ParticleBudget.rank(it) < 0.25 }.toSet()
        assertTrue(half.containsAll(quarter))
    }

    @Test
    fun `lod interpolates between steps and holds the last one`() {
        val budget = ParticleBudget().apply {
            lod = listOf(ParticleBudget.LodStep(32.0, 0.5), ParticleBudget.LodStep(16.0, 1.0))
        }
        assertEquals(1.0, budget.distanceKeep(0.0))
        assertEquals(0.75, budget.distanceKeep(24.0 * 24.0), 1e-9)
        assertEquals(0.5, budget.distanceKeep(100.0 * 100.0))
        assertEquals(1.0, budget.baseKeep(100.0 * 100.0, lodEnabled = false))
    }

    @Test
    fun `auto quality drops at once and recovers with hysteresis`() {
        val quality = ParticleBudget.QualityControl().apply {
            enabled = true
            recoverMargin = 3.0
            levels = listOf(ParticleBudget.QualityLevel(50.0, 0.25), ParticleBudget.QualityLevel(40.0, 0.75))
        }
        assertFalse(quality.update(30.0))
        assertEquals(1.0, quality.keep)
        assertTrue(quality.update(55.0))
        assertEquals(0.25, quality.keep)
        // inside the margin nothing changes
        assertFalse(quality.update(48.0))
        assertTrue(quality.update(46.0))
        assertEquals(0.75, quality.keep)
        assertTrue(quality.update(20.0))
        assertEquals(1.0, quality.keep)
    }

    @Test
    fun `disabled auto quality keeps full density`() {
        val quality = ParticleBudget.QualityControl().apply {
            levels = listOf(ParticleBudget.QualityLevel(40.0, 0.5))
        }
        quality.update(100.0)
        assertEquals(1.0, quality.keep)
    }

    // one frame of PARTICLES at the player's feet; returns how many were admitted
    private fun frame(budget: ParticleBudget, player: UUID): Int {
        val load = budget.load(player)
        return (0 until PARTICLES).count { index ->
            budget.admit(load, ParticleBudget.rank(index), budget.baseKeep(0.0, lodEnabled = true), 1)
        }
    }

    private companion object {
        const val PARTICLES = 4000
        const val TOLERANCE = 0.05
    }
}
