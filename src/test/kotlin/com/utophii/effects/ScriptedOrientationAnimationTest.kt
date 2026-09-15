package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.EasingType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// verifies that numeric animation tracks can drive the euler orientation fields of a layer
class ScriptedOrientationAnimationTest {

    private fun track(target: String, vararg keyframes: Pair<Double, Double>) = NumericTrack(
        target = target,
        keyframes = keyframes.map { (tick, value) -> NumericKeyframe(tick = tick, value = value) },
        easing = EasingType.LINEAR,
    )

    @Test
    fun `rotation tracks animate yaw pitch and roll over time`() {
        val animation = NumericAnimation(
            listOf(
                track("rotationYaw", 0.0 to 0.0, 20.0 to 2.0),
                track("rotationPitch", 0.0 to 0.0, 20.0 to 1.0),
                track("rotationRoll", 0.0 to 1.0, 20.0 to -1.0),
            ),
        )
        val base = EffectOptions()

        val animated = animation.apply(base, 10.0)

        // halfway through a linear 20-tick track
        assertEquals(1.0, animated.rotationYaw, EPSILON)
        assertEquals(0.5, animated.rotationPitch, EPSILON)
        assertEquals(0.0, animated.rotationRoll, EPSILON)
        // untouched base options are not mutated
        assertEquals(0.0, base.rotationYaw, EPSILON)
    }

    @Test
    fun `track targets are case insensitive`() {
        val animation = NumericAnimation(listOf(track("RotationPitch", 0.0 to 3.0)))

        val animated = animation.apply(EffectOptions(), 0.0)

        assertEquals(3.0, animated.rotationPitch, EPSILON)
    }

    companion object {
        private const val EPSILON = 1.0E-9
    }
}
