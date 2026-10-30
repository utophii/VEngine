package com.utophii.math

import org.bukkit.Location
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// the frame matrix must place every particle where the per-particle reference transform does
class FrameTransformTest {

    @Test
    fun `matrix matches transformInto for every rotation combination`() {
        val center = Location(null, 10.0, 64.0, -5.0)
        val transform = FrameTransform()
        val axes = listOf(null, Vector(1.0, 2.0, 3.0), Vector(0.0, 0.0, 0.0))
        for (axis in axes) for (tilt in ANGLES) for (yaw in ANGLES) for (pitch in ANGLES) for (roll in ANGLES) {
            transform.prepare(SCALE, yaw, axis, tilt, pitch, roll)
            POINTS.forEach { (x, y, z) -> assertSame(transform, center, x, y, z, yaw, axis, tilt, pitch, roll) }
        }
    }

    @Test
    fun `changing one input rebuilds the matrix`() {
        val center = Location(null, 0.0, 0.0, 0.0)
        val transform = FrameTransform()
        val axis = Vector(0.0, 1.0, 0.0)
        transform.prepare(SCALE, 0.3, axis, 0.5)
        assertSame(transform, center, 1.0, 2.0, 3.0, 0.3, axis, 0.5, 0.0, 0.0)
        // a mutated axis with the same instance must not reuse the stale matrix
        axis.x = 1.0
        transform.prepare(SCALE, 0.3, axis, 0.5)
        assertSame(transform, center, 1.0, 2.0, 3.0, 0.3, axis, 0.5, 0.0, 0.0)
        transform.prepare(SCALE, 0.3, null, 0.5)
        assertSame(transform, center, 1.0, 2.0, 3.0, 0.3, null, 0.5, 0.0, 0.0)
        transform.prepare(2.0, 0.3, null, 0.5)
        val expected = Location(null, 0.0, 0.0, 0.0)
        MathUtils.transformInto(expected, 1.0, 2.0, 3.0, center, 2.0, 0.3, null, 0.5)
        val actual = Location(null, 0.0, 0.0, 0.0)
        transform.applyInto(actual, 1.0, 2.0, 3.0, center)
        assertEquals(expected.x, actual.x, EPS)
    }

    private fun assertSame(
        transform: FrameTransform,
        center: Location,
        x: Double,
        y: Double,
        z: Double,
        yaw: Double,
        axis: Vector?,
        tilt: Double,
        pitch: Double,
        roll: Double,
    ) {
        val expected = Location(null, 0.0, 0.0, 0.0)
        MathUtils.transformInto(expected, x, y, z, center, SCALE, yaw, axis, tilt, pitch, roll)
        val actual = Location(null, 0.0, 0.0, 0.0)
        transform.applyInto(actual, x, y, z, center)
        assertEquals(expected.x, actual.x, EPS)
        assertEquals(expected.y, actual.y, EPS)
        assertEquals(expected.z, actual.z, EPS)
    }

    private companion object {
        const val EPS = 1.0E-9
        const val SCALE = 1.7
        val ANGLES = listOf(0.0, 0.4, -1.3, Math.PI / 2)
        val POINTS = listOf(Triple(1.0, 0.0, 0.0), Triple(-2.5, 1.5, 0.75), Triple(0.0, -3.0, 4.0))
    }
}
