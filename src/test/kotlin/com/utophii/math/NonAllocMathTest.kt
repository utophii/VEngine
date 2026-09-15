package com.utophii.math

import org.bukkit.Location
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// verifies that the non-allocating pooled math produces identical coordinates to the previous allocating math
class NonAllocMathTest {

    private fun location(x: Double, y: Double, z: Double): Location {
        // a world-free Location used only for math; no server required
        return Location(null, x, y, z)
    }

    // transformInto must match the original transform() for every axis with scale, yaw, tilt and translate
    @Test
    fun `transformInto matches transform for combined transform`() {
        val center = location(10.0, 20.0, -5.0)
        val local = Vector(1.5, -2.0, 3.0)
        val scale = 0.7
        val yaw = 1.1
        val tiltAxis = Vector(1.0, 2.0, 3.0)
        val tiltAngle = 0.4

        val expected = MathUtils.transform(local, center, scale, yaw, tiltAxis, tiltAngle)
        val actual = location(0.0, 0.0, 0.0)
        MathUtils.transformInto(actual, local, center, scale, yaw, tiltAxis, tiltAngle)

        assertEquals(expected.x, actual.x, EPS, "X mismatch")
        assertEquals(expected.y, actual.y, EPS, "Y mismatch")
        assertEquals(expected.z, actual.z, EPS, "Z mismatch")
    }

    // component-based overload must match the vector-based overload
    @Test
    fun `component transformInto matches vector transformInto`() {
        val center = location(-3.0, 1.0, 8.0)
        val local = Vector(2.0, -1.0, 0.5)
        val scale = 2.5
        val yaw = -0.9
        val tiltAxis = Vector(0.0, 0.0, 1.0)
        val tiltAngle = 0.8

        val fromVector = location(0.0, 0.0, 0.0)
        MathUtils.transformInto(fromVector, local, center, scale, yaw, tiltAxis, tiltAngle)
        val fromComponents = location(0.0, 0.0, 0.0)
        MathUtils.transformInto(fromComponents, local.x, local.y, local.z, center, scale, yaw, tiltAxis, tiltAngle)

        assertEquals(fromVector.x, fromComponents.x, EPS)
        assertEquals(fromVector.y, fromComponents.y, EPS)
        assertEquals(fromVector.z, fromComponents.z, EPS)
    }

    // identity transform (no yaw, no tilt, scale 1) yields center + local
    @Test
    fun `transformInto identity produces center plus local`() {
        val center = location(5.0, 6.0, 7.0)
        val local = Vector(1.0, 2.0, 3.0)
        val out = location(0.0, 0.0, 0.0)
        MathUtils.transformInto(out, local, center, scale = 1.0, rotationYaw = 0.0, tiltAxis = null, tiltAngle = 0.0)
        assertEquals(6.0, out.x, EPS)
        assertEquals(8.0, out.y, EPS)
        assertEquals(10.0, out.z, EPS)
    }

    // rotating the pole vector (0, r, 0) around Y leaves it unchanged
    @Test
    fun `transformInto yaw rotation preserves the vertical axis`() {
        val center = location(0.0, 0.0, 0.0)
        val local = Vector(0.0, 4.0, 0.0)
        val out = location(0.0, 0.0, 0.0)
        MathUtils.transformInto(out, local, center, scale = 1.0, rotationYaw = Math.PI, tiltAxis = null, tiltAngle = 0.0)
        assertEquals(0.0, out.x, EPS)
        assertEquals(4.0, out.y, EPS)
        assertEquals(0.0, out.z, EPS)
    }

    // rotateAndTranslateInto must match Rodrigues around a fixed axis + origin translation
    @Test
    fun `rotateAndTranslateInto matches Rodrigues translate`() {
        val originX = 1.0
        val originY = 2.0
        val originZ = 3.0
        val vx = 0.5
        val vy = -1.5
        val vz = 2.0
        val axis = Vector(0.0, 1.0, 0.0)
        val theta = 0.7

        // reference: Rodrigues(rel) + origin
        val rel = Vector(vx, vy, vz)
        val rotated = MathUtils.rotateRodrigues(rel, axis, theta)
        val expectedX = originX + rotated.x
        val expectedY = originY + rotated.y
        val expectedZ = originZ + rotated.z

        val out = location(0.0, 0.0, 0.0)
        MathUtils.rotateAndTranslateInto(out, originX, originY, originZ, vx, vy, vz, axis, theta)

        assertEquals(expectedX, out.x, EPS)
        assertEquals(expectedY, out.y, EPS)
        assertEquals(expectedZ, out.z, EPS)
    }

    // catmullRomComponent must reproduce each axis of the original catmullRom(Location)
    @Test
    fun `catmullRomComponent matches catmullRom`() {
        val p0 = location(0.0, 1.0, 2.0)
        val p1 = location(1.0, 2.0, 3.0)
        val p2 = location(2.0, 3.0, 4.0)
        val p3 = location(3.0, 4.0, 5.0)
        for (t in doubleArrayOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            val expected = AdvancedMathUtils.catmullRom(p0, p1, p2, p3, t)
            val cx = AdvancedMathUtils.catmullRomComponent(p0.x, p1.x, p2.x, p3.x, t)
            val cy = AdvancedMathUtils.catmullRomComponent(p0.y, p1.y, p2.y, p3.y, t)
            val cz = AdvancedMathUtils.catmullRomComponent(p0.z, p1.z, p2.z, p3.z, t)
            assertEquals(expected.x, cx, EPS, "X at t=$t")
            assertEquals(expected.y, cy, EPS, "Y at t=$t")
            assertEquals(expected.z, cz, EPS, "Z at t=$t")
        }
    }

    // pitch = +90 degrees around X maps the up vector to +Z
    @Test
    fun `pitch tilts the local up axis forward`() {
        val center = location(0.0, 0.0, 0.0)
        val up = Vector(0.0, 1.0, 0.0)
        val out = location(0.0, 0.0, 0.0)

        MathUtils.transformInto(out, up, center, 1.0, 0.0, null, 0.0, Math.PI / 2, 0.0)

        assertEquals(0.0, out.x, EPS)
        assertEquals(0.0, out.y, EPS)
        assertEquals(1.0, out.z, EPS)
    }

    // roll = +90 degrees around Z maps the east vector to +Y
    @Test
    fun `roll tilts the local east axis upward`() {
        val center = location(0.0, 0.0, 0.0)
        val east = Vector(1.0, 0.0, 0.0)
        val out = location(0.0, 0.0, 0.0)

        MathUtils.transformInto(out, east, center, 1.0, 0.0, null, 0.0, 0.0, Math.PI / 2)

        assertEquals(0.0, out.x, EPS)
        assertEquals(1.0, out.y, EPS)
        assertEquals(0.0, out.z, EPS)
    }

    // spot check: a flat circle (XZ plane) pitched by 90 degrees becomes a standing circle (XY plane)
    @Test
    fun `pitched flat ring stands upright`() {
        val center = location(0.0, 0.0, 0.0)
        val ringPoint = Vector(1.0, 0.0, 0.0)
        val out = location(0.0, 0.0, 0.0)

        // (1, 0, 0) lies on the pitch axis, so it must stay put under pure pitch
        MathUtils.transformInto(out, ringPoint, center, 1.0, 0.0, null, 0.0, Math.PI / 2, 0.0)
        assertEquals(1.0, out.x, EPS)
        assertEquals(0.0, out.y, EPS)
        assertEquals(0.0, out.z, EPS)

        // (0, 0, 1) rotates to (0, -1, 0): y' = y cos - z sin = -1 at pitch = +90deg
        MathUtils.transformInto(out, 0.0, 0.0, 1.0, center, 1.0, 0.0, null, 0.0, Math.PI / 2, 0.0)
        assertEquals(0.0, out.x, EPS)
        assertEquals(-1.0, out.y, EPS)
        assertEquals(0.0, out.z, EPS)
    }

    // transformInto must keep matching transform() when pitch and roll are non-zero
    @Test
    fun `transformInto matches transform with euler angles`() {
        val center = location(3.0, 64.0, -2.0)
        val local = Vector(0.5, 1.5, -1.0)
        val pitch = 0.6
        val roll = -1.2
        val yaw = 2.2

        val expected = MathUtils.transform(local, center, 1.4, yaw, null, 0.0, pitch, roll)
        val actual = location(0.0, 0.0, 0.0)
        MathUtils.transformInto(actual, local, center, 1.4, yaw, null, 0.0, pitch, roll)

        assertEquals(expected.x, actual.x, EPS)
        assertEquals(expected.y, actual.y, EPS)
        assertEquals(expected.z, actual.z, EPS)
    }

    companion object {
        private const val EPS = 1.0E-9
    }
}
