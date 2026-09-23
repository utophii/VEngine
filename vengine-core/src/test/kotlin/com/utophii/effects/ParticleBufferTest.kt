package com.utophii.effects

import org.bukkit.Location
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Verifies the pooled render buffer reuses Location objects and preserves correct values across frames.
class ParticleBufferTest {

    private fun location(x: Double, y: Double, z: Double): Location {
        return Location(null, x, y, z)
    }

    @Test
    fun `acquire exposes reusable location slots`() {
        val base = location(0.0, 0.0, 0.0)
        val buffer = ParticleBuffer(base)
        buffer.acquire(3)

        val l0 = buffer.location(0)
        val l1 = buffer.location(1)
        val l2 = buffer.location(2)
        assertTrue(l0 !== l1 && l1 !== l2 && l0 !== l2, "slots must be distinct objects")

        l0.x = 1.0
        l0.y = 2.0
        l0.z = 3.0
        assertEquals(1.0, buffer.location(0).x, EPS)
        assertEquals(2.0, buffer.location(0).y, EPS)
        assertEquals(3.0, buffer.location(0).z, EPS)
    }

    @Test
    fun `released buffer preserves object identity for reuse`() {
        val buffer = ParticleBuffer(location(0.0, 0.0, 0.0))
        buffer.acquire(2)
        val slot = buffer.location(0)
        buffer.release()

        buffer.acquire(2)
        // after release+acquire the same underlying Location object is reused
        assertSame(slot, buffer.location(0))
    }

    @Test
    fun `add appends and list reflects filled count`() {
        val buffer = ParticleBuffer(location(0.0, 0.0, 0.0))
        buffer.acquire(0)
        buffer.add(1.0, 2.0, 3.0)
        buffer.add(4.0, 5.0, 6.0)

        val list = buffer.list()
        assertEquals(2, list.size)
        assertEquals(1.0, list[0].x, EPS)
        assertEquals(5.0, list[1].y, EPS)
    }

    @Test
    fun `pool hands out fresh buffer when empty and returns it`() {
        val base = location(0.0, 0.0, 0.0)
        val a = ParticleBufferPool.obtain(base)
        a.acquire(4)
        ParticleBufferPool.release(a)

        val b = ParticleBufferPool.obtain(base)
        assertSame(a, b, "pooled buffer should be reused after release")
        assertSame(a.location(0), b.location(0), "retained slot is reused")
    }

    @Test
    fun `pool does not reuse a buffer that grew beyond the bound`() {
        // obtain and grow far beyond MAX_CAPACITY so it is not returned to the pool
        val huge = ParticleBufferPool.obtain(location(0.0, 0.0, 0.0))
        huge.acquire(5000)
        ParticleBufferPool.release(huge) // dropped due to size bound

        val fresh = ParticleBufferPool.obtain(location(0.0, 0.0, 0.0))
        assertTrue(fresh !== huge, "oversized buffer must not be reused")
    }

    @Test
    fun `rebind keeps slots distinct and re-acquirable`() {
        // rebind repoints the buffer to a new base center; retained slots stay distinct and stay usable
        val buffer = ParticleBuffer(location(0.0, 0.0, 0.0))
        buffer.acquire(2)
        val slot0 = buffer.location(0)
        val slot1 = buffer.location(1)
        buffer.rebind(location(9.0, 9.0, 9.0))
        buffer.acquire(2)
        assertTrue(buffer.location(0) !== buffer.location(1))
        assertEquals(2, buffer.list().size)

        // the buffer remains writable after rebinding; add appends at the logical end
        buffer.add(11.0, 12.0, 13.0)
        assertSame(slot0, buffer.location(0))
        assertTrue(slot0 !== slot1)
    }

    companion object {
        private const val EPS = 1.0E-9
    }
}
