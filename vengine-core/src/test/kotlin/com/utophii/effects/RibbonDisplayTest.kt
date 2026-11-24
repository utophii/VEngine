package com.utophii.effects

import com.utophii.api.DisplayOptions
import com.utophii.api.EffectOptions
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.PI

class RibbonDisplayTest {
    private val ribbon = DisplayOptions(type = DisplayOptions.Type.RIBBON)
    private fun point(x: Int, y: Int = 0, z: Int = 0) = Location(null, x.toDouble(), y.toDouble(), z.toDouble())

    private fun frame(points: List<Location>, display: DisplayOptions = ribbon, opts: EffectOptions = EffectOptions(),
                      rotation: Quaternionf? = null, colors: IntArray? = null, sizes: FloatArray? = null) =
        DisplayFrame(points, opts, display, 0.0, rotation, colors, sizes)

    @Test
    fun `single-space background reaches both endpoints after centering and rotation`() {
        val start = point(9, -5, 12)
        val end = point(6, -3, 18)
        val frame = frame(listOf(start, end))
        assertEquals(1, frame.count)
        val center = frame.position(0)
        val transform = frame.transform(0)
        // native single-space background spans 1/8 x 1/4 block with its center offset from the origin
        for ((x, endpoint) in listOf(-0.05f to start, 0.075f to end)) {
            val edge = Vector3f(x, 0.125f, 0f).mul(transform.scale)
            transform.leftRotation.transform(edge).add(transform.translation)
            assertEquals(endpoint.x, center.x + edge.x, 1e-5)
            assertEquals(endpoint.y, center.y + edge.y, 1e-5)
            assertEquals(endpoint.z, center.z + edge.z, 1e-5)
        }
    }

    @Test
    fun `profiles follow arc length and combine with layer scale lifetime size and background alpha`() {
        val display = ribbon.copy(width = listOf(0f, 2f), opacity = listOf(0f, 1f),
            textBackground = Color.fromARGB(0x804080FF.toInt()))
        val points = listOf(point(0), point(1), point(1), point(1, 0, 3))
        val frame = frame(points, display, EffectOptions(scale = 2.0), sizes = floatArrayOf(1f, 1f, 0f, 1f))
        assertEquals(2, frame.count)
        // the two segment midpoints are at 1/8 and 5/8 of the four-block path
        assertEquals(0.5f, frame.transform(0).scale.y / 4)
        assertEquals(1.25f, frame.transform(1).scale.y / 4)
        assertEquals(0x104080FF, frame.color(0))
        assertEquals(0x504080FF, frame.color(1))
        val colored = frame(points, display, colors = intArrayOf(0, 0, 0xFF0000FF.toInt(), 0xFFFF0000.toInt()))
        assertEquals(0x9F7F007F.toInt(), colored.color(1))
    }

    @Test
    fun `local normal turns with layer and follow rotations while the tangent is already in world space`() {
        val display = ribbon.copy(normal = Vector(0, 0, 1), billboard = Display.Billboard.CENTER)
        assertEquals(Display.Billboard.FIXED, display.resolvedBillboard)
        val opts = EffectOptions(rotationPitch = PI / 2)
        val follow = Quaternionf().rotateZ((PI / 2).toFloat())
        val basis = frame(listOf(point(0), point(0, 0, 2)), display, opts, follow).transform(0).leftRotation
        val facing = basis.transform(Vector3f(0f, 0f, 1f))
        val tangent = basis.transform(Vector3f(1f, 0f, 0f))
        assertEquals(1f, facing.x, 1e-5f)
        assertEquals(1f, tangent.z, 1e-5f)
    }

    @Test
    fun `empty frames duplicates and tangents parallel to the normal are safe and the cap counts segments`() {
        for (points in listOf(emptyList(), listOf(point(0)), listOf(point(0), point(0)))) {
            assertEquals(0, frame(points).count)
        }
        val points = List(300) { point(0, it) }
        val frame = frame(points)
        assertEquals(DisplayEntityPool.MAX_DISPLAYS, frame.count)
        val tangent = frame.transform(0).leftRotation.transform(Vector3f(1f, 0f, 0f))
        assertEquals(1f, tangent.y, 1e-5f)
        assertEquals(point(0, 255).add(0.0, 0.5, 0.0), frame.position(255))
    }
}