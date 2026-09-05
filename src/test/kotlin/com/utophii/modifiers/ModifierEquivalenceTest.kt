package com.utophii.modifiers

import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// verifies that the in-place modifier refactor produces the same coordinates as the previous copy-based math
class ModifierEquivalenceTest {

    private fun location(x: Double, y: Double, z: Double): Location {
        return Location(null, x, y, z)
    }

    // in-place turbulence must equal the old loc.clone().add(dx, dy, dz)
    @Test
    fun `turbulence modifier matches copy-based displacement`() {
        val modifier = TurbulenceModifier(strength = 0.12, frequency = 0.35, octaves = 4)
        val before = location(1.0, 2.0, 3.0)

        // expected: the old implementation cloned loc and added three fbm displacements
        val expected = before.clone().add(
            MathUtils.fbm(1.0 * 0.35 + 2.0, 2.0 * 0.35, 3.0 * 0.35, 4) * 0.12,
            MathUtils.fbm(1.0 * 0.35, 2.0 * 0.35 + 2.0, 3.0 * 0.35, 4) * 0.12,
            MathUtils.fbm(1.0 * 0.35, 2.0 * 0.35, 3.0 * 0.35 + 2.0, 4) * 0.12,
        )

        val actual = location(1.0, 2.0, 3.0)
        val returned = modifier.modify(actual, 2.0)

        assertEquals(expected.x, returned.x, EPS)
        assertEquals(expected.y, returned.y, EPS)
        assertEquals(expected.z, returned.z, EPS)
    }

    // in-place rotation around center + pivot must equal origin + Rodrigues(relative)
    @Test
    fun `rotation modifier matches copy-based rotation`() {
        val modifier = RotationModifier(axis = Vector(0.0, 1.0, 0.0), angularVelocity = 0.6, initialAngle = 0.2, pivotOffset = Vector(0.0, 0.0, 0.0))
        val center = location(10.0, 20.0, 30.0)
        val loc = location(11.0, 21.0, 29.0)

        // expected (old copy-based): origin = center + pivotOffset, relative = loc - origin, rotated = Rodrigues(relative, axis, theta); origin + rotated
        val origin = center.clone()
        val relative = loc.toVector().subtract(origin.toVector())
        val theta = 0.2 + 0.6 * 1.5
        val rotated = MathUtils.rotateRodrigues(relative, Vector(0.0, 1.0, 0.0), theta)
        val expected = origin.clone().add(rotated)

        val actual = location(11.0, 21.0, 29.0)
        val returned = modifier.modify(actual, center, 1.5)

        assertEquals(expected.x, returned.x, EPS)
        assertEquals(expected.y, returned.y, EPS)
        assertEquals(expected.z, returned.z, EPS)
    }

    // in-place vortex must equal center + Rodrigues(rel, axis, theta(r))
    @Test
    fun `vortex modifier matches copy-based rotation`() {
        val axis = Vector(0.0, 1.0, 0.0)
        val modifier = VortexModifier(axis = axis, coreRadius = 1.2, vortexStrength = 0.5)
        val center = location(0.0, 0.0, 0.0)
        val loc = location(1.0, 2.0, 0.5)

        val rel = loc.toVector().subtract(center.toVector())
        val distSq = rel.lengthSquared()
        val theta = 0.5 * Math.exp(-distSq / (2.0 * 1.2 * 1.2)) * 3.0
        val rotated = MathUtils.rotateRodrigues(rel, axis, theta)
        val expected = center.clone().add(rotated)

        val actual = location(1.0, 2.0, 0.5)
        val returned = modifier.modify(actual, center, 3.0)

        assertEquals(expected.x, returned.x, EPS)
        assertEquals(expected.y, returned.y, EPS)
        assertEquals(expected.z, returned.z, EPS)
    }

    companion object {
        private const val EPS = 1.0E-9
    }
}
