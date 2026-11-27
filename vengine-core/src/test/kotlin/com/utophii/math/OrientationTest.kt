package com.utophii.math

import org.bukkit.Location
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class OrientationTest {

    @Test
    fun `facing sends +Z along the normal and +Y to its projected top in a right-handed basis`() {
        val random = Random(7)
        repeat(200) {
            val n = Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1)
            if (n.length() < 1e-3f) return@repeat
            val q = Quaternionf()
            assertTrue(Orientation.facing(n.x.toDouble(), n.y.toDouble(), n.z.toDouble(), q))
            val unit = Vector3f(n).normalize()
            assertVector(unit, q.transform(Vector3f(0f, 0f, 1f)))
            if (abs(unit.y) < Orientation.POLE_THRESHOLD) {
                val up = Vector3f(0f, 1f, 0f).sub(Vector3f(unit).mul(unit.y)).normalize()
                assertVector(up, q.transform(Vector3f(0f, 1f, 0f)))
            }
            // the quaternion is a proper rotation, so X = Y x Z and text is never mirrored
            val x = q.transform(Vector3f(1f, 0f, 0f))
            val expected = q.transform(Vector3f(0f, 1f, 0f)).cross(q.transform(Vector3f(0f, 0f, 1f)))
            assertVector(expected, x)
        }
    }

    @Test
    fun `poles use +Z as the top and stay finite and right-handed`() {
        for (sign in listOf(1.0, -1.0)) {
            val q = Quaternionf()
            assertTrue(Orientation.facing(0.0, sign, 0.0, q))
            assertTrue(q.isFinite)
            assertVector(Vector3f(0f, sign.toFloat(), 0f), q.transform(Vector3f(0f, 0f, 1f)))
            assertVector(Vector3f(0f, 0f, 1f), q.transform(Vector3f(0f, 1f, 0f)))
            assertEquals(1f, q.lengthSquared(), 1e-5f)
        }
        // just inside the threshold still projects +Y
        val q = Quaternionf()
        assertTrue(Orientation.facing(0.02, 1.0, 0.0, q))
        assertTrue(q.transform(Vector3f(0f, 1f, 0f)).y > 0f)
    }

    @Test
    fun `scaling keeps the rotation, negating turns the face around and degenerate normals are rejected`() {
        val a = Quaternionf()
        val b = Quaternionf()
        Orientation.facing(1.0, 2.0, -3.0, a)
        Orientation.facing(5.0, 10.0, -15.0, b)
        assertEquals(1f, abs(a.dot(b)), 1e-6f)
        Orientation.facing(-1.0, -2.0, 3.0, b)
        assertEquals(-1f, a.transform(Vector3f(0f, 0f, 1f)).dot(b.transform(Vector3f(0f, 0f, 1f))), 1e-5f)
        val untouched = Quaternionf(0.1f, 0.2f, 0.3f, 0.9f)
        for (n in listOf(Triple(0.0, 0.0, 0.0), Triple(Double.NaN, 0.0, 1.0), Triple(Double.POSITIVE_INFINITY, 0.0, 0.0))) {
            val q = Quaternionf(untouched)
            assertFalse(Orientation.facing(n.first, n.second, n.third, q))
            assertEquals(untouched, q)
        }
    }

    @Test
    fun `angles compose exactly like display rotation`() {
        val q = Quaternionf()
        assertTrue(Orientation.angles(0.3, -1.1, 2.0, q))
        val expected = Quaternionf().rotateXYZ(0.3f, -1.1f, 2.0f)
        assertEquals(1f, abs(q.dot(expected)), 1e-6f)
        // large accumulated angles are wrapped before the float conversion
        assertTrue(Orientation.angles(0.0, 0.0, 2.0 + MathUtils.TAU * 1e6, q))
        assertEquals(1f, abs(q.dot(Quaternionf().rotateZ(2f))), 1e-4f)
        assertFalse(Orientation.angles(0.0, Double.NaN, 0.0, q))
    }

    @Test
    fun `geometry rotation turns basis vectors exactly like FrameTransform`() {
        val transform = FrameTransform()
        val output = Location(null, 0.0, 0.0, 0.0)
        val origin = Location(null, 0.0, 0.0, 0.0)
        val axes = listOf(null, Vector(1.0, 2.0, 3.0), Vector(0.0, 0.0, 0.0))
        val q = Quaternionf()
        for (scale in listOf(1.0, 2.5, -1.5)) for (axis in axes) for (tilt in ANGLES) for (yaw in ANGLES) for (pitch in ANGLES) for (roll in ANGLES) {
            transform.prepare(scale, yaw, axis, tilt, pitch, roll)
            Orientation.geometry(yaw, pitch, roll, axis, tilt, q)
            for (basis in BASIS) {
                transform.applyInto(output, basis.x.toDouble(), basis.y.toDouble(), basis.z.toDouble(), origin)
                // the rotation part is M / scale, the sign of a negative scale is not a rotation
                val expected = Vector3f((output.x / scale).toFloat(), (output.y / scale).toFloat(), (output.z / scale).toFloat())
                assertVector(expected, q.transform(Vector3f(basis)))
            }
        }
    }

    @Test
    fun `point spin is applied before every layer rotation`() {
        val q = Quaternionf()
        val spin = 0.7
        val yaw = 0.4
        Orientation.geometry(yaw, 0.3, 0.2, null, 0.0, q, spin)
        // FormulaEffect turns x toward z by spin, then the frame transforms
        val x = 1.0
        val z = 0.0
        val spunX = x * cos(spin) - z * sin(spin)
        val spunZ = x * sin(spin) + z * cos(spin)
        val expected = Location(null, 0.0, 0.0, 0.0)
        MathUtils.transformInto(expected, spunX, 0.0, spunZ, Location(null, 0.0, 0.0, 0.0), 1.0, yaw, null, 0.0, 0.3, 0.2)
        assertVector(Vector3f(expected.x.toFloat(), expected.y.toFloat(), expected.z.toFloat()), q.transform(Vector3f(1f, 0f, 0f)))
    }

    private fun assertVector(expected: Vector3f, actual: Vector3f) {
        val d = Vector3f(expected).sub(actual)
        assertTrue(sqrt(d.lengthSquared()) < 1e-4f, "expected $expected, got $actual")
    }

    private companion object {
        val ANGLES = listOf(0.0, 0.7, -2.1)
        val BASIS = listOf(Vector3f(1f, 0f, 0f), Vector3f(0f, 1f, 0f), Vector3f(0f, 0f, 1f))
    }
}
