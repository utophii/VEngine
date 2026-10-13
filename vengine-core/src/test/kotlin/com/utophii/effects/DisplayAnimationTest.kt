package com.utophii.effects

import com.utophii.api.DisplayOptions
import com.utophii.api.EffectOptions
import com.utophii.math.EasingType
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// verifies that display tracks animate display options and that options carry display settings through layers
class DisplayAnimationTest {

    private fun track(target: String, vararg keyframes: Pair<Double, Double>) = NumericTrack(
        target = target,
        keyframes = keyframes.map { (tick, value) -> NumericKeyframe(tick = tick, value = value) },
        easing = EasingType.LINEAR,
    )

    @Test
    fun `display size and rotation tracks animate the display options`() {
        val animation = NumericAnimation(
            listOf(
                track("display.size", 0.0 to 0.0, 20.0 to 1.0),
                track("display.rotation.y", 0.0 to 0.0, 20.0 to 2.0),
            ),
        )
        val base = EffectOptions(display = DisplayOptions(rotation = Vector(0.3, 0.0, 0.0)))

        val display = animation.apply(base, 10.0).display!!

        assertEquals(0.5, display.size.x, EPSILON)
        assertEquals(0.5, display.size.z, EPSILON)
        assertEquals(1.0, display.rotation.y, EPSILON)
        // other rotation axes and the base options are left untouched
        assertEquals(0.3, display.rotation.x, EPSILON)
        assertEquals(0.0, base.display!!.rotation.y, EPSILON)
    }

    @Test
    fun `display tracks are ignored on particle layers`() {
        val animation = NumericAnimation(listOf(track("display.size", 0.0 to 2.0)))

        assertNull(animation.apply(EffectOptions(), 0.0).display)
    }

    @Test
    fun `layer display survives option resolution and runtime display overrides it`() {
        val layerDisplay = DisplayOptions(type = DisplayOptions.Type.TEXT, text = listOf("rune"))
        val layer = ScriptedLayer(
            effect = "sphere",
            startTick = 0,
            durationTicks = null,
            options = EffectOptions(display = layerDisplay),
        )

        assertEquals(layerDisplay, layer.resolveOptions(EffectOptions(), 0.0).display)

        val runtimeDisplay = DisplayOptions(type = DisplayOptions.Type.BLOCK)
        assertEquals(runtimeDisplay, layer.resolveOptions(EffectOptions(display = runtimeDisplay), 0.0).display)
    }

    @Test
    fun `toBuilder keeps the display options`() {
        val options = EffectOptions(display = DisplayOptions(spin = Vector(0.0, 0.1, 0.0)))

        assertEquals(options, options.toBuilder().build())
    }

    @Test
    fun `text displays face the viewer by default`() {
        assertEquals(org.bukkit.entity.Display.Billboard.CENTER, DisplayOptions(type = DisplayOptions.Type.TEXT).resolvedBillboard)
        assertEquals(org.bukkit.entity.Display.Billboard.FIXED, DisplayOptions(type = DisplayOptions.Type.ITEM).resolvedBillboard)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
