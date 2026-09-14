package com.utophii.modifiers

import com.utophii.math.EasingType
import org.bukkit.Location
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// verifies waypoint path displacement math for the motion modifier
class MotionModifierTest {

    private fun linearLeg(x: Double, y: Double, z: Double, ticks: Long) =
        MotionModifier.MotionLeg(Vector(x, y, z), ticks.toDouble(), EasingType.LINEAR)

    @Test
    fun `displacement is zero at the start of the path`() {
        val motion = MotionModifier(listOf(linearLeg(10.0, 5.0, -2.0, 40L)))

        val displacement = motion.displacementAt(0.0)

        assertEquals(0.0, displacement.x, EPSILON)
        assertEquals(0.0, displacement.y, EPSILON)
        assertEquals(0.0, displacement.z, EPSILON)
    }

    @Test
    fun `displacement follows the eased progress along a leg`() {
        val motion = MotionModifier(listOf(linearLeg(10.0, 0.0, 0.0, 40L)))

        // linear progress at half of the leg is exactly the midpoint
        assertEquals(5.0, motion.displacementAt(20.0).x, EPSILON)
        assertEquals(10.0, motion.displacementAt(40.0).x, EPSILON)
    }

    @Test
    fun `default sine easing crosses the midpoint of a leg`() {
        val motion = MotionModifier.to(Vector(8.0, 0.0, 0.0), ticks = 40L)

        // easeInOutSine(0.5) == 0.5, so half of the duration covers half of the distance
        assertEquals(4.0, motion.displacementAt(20.0).x, EPSILON)
    }

    @Test
    fun `hold mode clamps the displacement at the final waypoint`() {
        val motion = MotionModifier(listOf(linearLeg(3.0, 4.0, 0.0, 10L)))

        val displacement = motion.displacementAt(500.0)

        assertEquals(3.0, displacement.x, EPSILON)
        assertEquals(4.0, displacement.y, EPSILON)
    }

    @Test
    fun `loop mode restarts the path after the total duration`() {
        val motion = MotionModifier(listOf(linearLeg(10.0, 0.0, 0.0, 40L)), MotionModifier.Mode.LOOP)

        assertEquals(motion.displacementAt(10.0).x, motion.displacementAt(50.0).x, EPSILON)
        assertEquals(motion.displacementAt(0.0).x, motion.displacementAt(40.0).x, EPSILON)
    }

    @Test
    fun `ping pong mode mirrors the second half of the cycle`() {
        val motion = MotionModifier(listOf(linearLeg(10.0, 0.0, 0.0, 40L)), MotionModifier.Mode.PING_PONG)

        // t=60 on the way back equals t=20 on the way forward
        assertEquals(motion.displacementAt(20.0).x, motion.displacementAt(60.0).x, EPSILON)
        // a full ping-pong cycle (2 * totalTicks) returns to the spawn point
        assertEquals(0.0, motion.displacementAt(80.0).x, EPSILON)
    }

    @Test
    fun `multi leg path passes exactly through intermediate waypoints`() {
        val motion = MotionModifier(
            listOf(
                linearLeg(10.0, 0.0, 0.0, 40L),
                linearLeg(10.0, 5.0, 0.0, 20L),
            ),
        )

        val atJoint = motion.displacementAt(40.0)
        assertEquals(10.0, atJoint.x, EPSILON)
        assertEquals(0.0, atJoint.y, EPSILON)

        val atEnd = motion.displacementAt(60.0)
        assertEquals(10.0, atEnd.x, EPSILON)
        assertEquals(5.0, atEnd.y, EPSILON)

        assertEquals(60.0, motion.totalTicks, EPSILON)
    }

    @Test
    fun `modify shifts the particle position by the current displacement`() {
        val motion = MotionModifier(listOf(linearLeg(10.0, 2.0, -4.0, 20L)), MotionModifier.Mode.HOLD)
        val location = Location(null, 100.0, 64.0, 100.0)

        motion.modify(location, 10.0)

        assertEquals(105.0, location.x, EPSILON)
        assertEquals(65.0, location.y, EPSILON)
        assertEquals(98.0, location.z, EPSILON)
    }

    @Test
    fun `invalid paths are rejected`() {
        assertThrows<IllegalArgumentException> {
            MotionModifier(emptyList())
        }
        assertThrows<IllegalArgumentException> {
            MotionModifier(listOf(MotionModifier.MotionLeg(Vector(1.0, 0.0, 0.0), 0.0)))
        }
    }

    companion object {
        private const val EPSILON = 1e-9
    }
}
