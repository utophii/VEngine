package com.utophii.effects

import com.utophii.api.DisplayOptions
import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import com.utophii.math.Orientation
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// per-point orientation of fixed display models, from the generators to the shared display frame
class PointOrientationTest {
    private val center = Location(null, 3.0, 64.0, -7.0)
    private val text = DisplayOptions(type = DisplayOptions.Type.TEXT, text = listOf("F"), billboard = Display.Billboard.FIXED)
    private val textOptions = EffectOptions(display = text)

    private class Frame(val buffer: ParticleBuffer, val display: DisplayFrame) {
        fun rotation(index: Int): Quaternionf = display.transform(index).leftRotation
        fun facing(index: Int): Vector3f = rotation(index).transform(Vector3f(0f, 0f, 1f))
        fun top(index: Int): Vector3f = rotation(index).transform(Vector3f(0f, 1f, 0f))
        fun position(index: Int): Location = buffer.location(index)
    }

    private fun frame(
        effect: AbstractParticleEffect,
        opts: EffectOptions = textOptions,
        time: Double = 0.0,
        follow: Quaternionf? = null,
        buffer: ParticleBuffer = ParticleBuffer(center),
    ): Frame {
        effect.calculateInto(buffer, center, opts, time)
        val display = DisplayFrame(buffer.list(), opts, opts.display!!, time, follow, null, null, buffer.orientations())
        return Frame(buffer, display)
    }

    private fun ring(orientation: DisplayOrientation?) = ParametricEffect(
        "ring", listOf("t"), listOf(12), listOf(ParamRange(0.0, MathUtils.TAU)),
        "2 * cos(t)", "0", "2 * sin(t)", emptyMap(), 0.0, orientation = orientation,
    )

    private fun surface(x: String, y: String, z: String, side: DisplayOrientation.Side?, samples: List<Int> = listOf(24, 4),
                        ranges: List<ParamRange> = listOf(ParamRange(0.0, MathUtils.TAU), ParamRange(-1.0, 1.0))) =
        ParametricEffect("surface", listOf("u", "v"), samples, ranges, x, y, z, emptyMap(), 0.0,
            orientation = side?.let(DisplayOrientation::AlignToNormal))

    private fun points(samples: String, output: Map<String, String>, parameters: Map<String, Double> = emptyMap()) =
        PointEffect("points", samples, output, parameters)

    @Test
    fun `a ring of letters faces outward and stays upright`() {
        val frame = frame(ring(DisplayOrientation.Normal("cos(t)", "0", "sin(t)")))
        assertEquals(12, frame.display.count)
        for (i in 0 until 12) {
            val t = i * MathUtils.TAU / 12
            assertVector(Vector3f(cos(t).toFloat(), 0f, sin(t).toFloat()), frame.facing(i))
            assertVector(Vector3f(0f, 1f, 0f), frame.top(i))
        }
        // (2, 0, 0) faces +X and (0, 0, 2) faces +Z
        assertVector(Vector3f(1f, 0f, 0f), frame.facing(0))
        assertVector(Vector3f(0f, 0f, 1f), frame.facing(3))

        val scaled = frame(ring(DisplayOrientation.Normal("3 * cos(t)", "0", "3 * sin(t)")))
        val flipped = frame(ring(DisplayOrientation.Normal("-cos(t)", "0", "-sin(t)")))
        for (i in 0 until 12) {
            assertRotation(frame.rotation(i), scaled.rotation(i))
            assertEquals(-1f, frame.facing(i).dot(flipped.facing(i)), 1e-5f)
        }
    }

    @Test
    fun `layer pitch yaw tilt and scale keep the ring facing outward`() {
        val opts = textOptions.toBuilder().rotationPitch(0.6).rotationYaw(1.1).tiltAxis(Vector(1.0, 2.0, 3.0))
            .tiltAngle(0.5).scale(1.7).build()
        val frame = frame(ring(DisplayOrientation.Normal("cos(t)", "0", "sin(t)")), opts)
        val geometry = Orientation.geometry(opts.rotationYaw, opts.rotationPitch, opts.rotationRoll, opts.tiltAxis, opts.tiltAngle, Quaternionf())
        for (i in 0 until 12) {
            val p = frame.position(i)
            val outward = Vector3f((p.x - center.x).toFloat(), (p.y - center.y).toFloat(), (p.z - center.z).toFloat()).normalize()
            assertVector(outward, frame.facing(i))
            assertVector(geometry.transform(Vector3f(0f, 1f, 0f)), frame.top(i))
        }
    }

    @Test
    fun `the follow rotation is applied exactly once on top of the point rotation`() {
        val effect = ring(DisplayOrientation.Normal("cos(t)", "0", "sin(t)"))
        val follow = Quaternionf().rotateY(0.8f).rotateX(-0.3f)
        val still = frame(effect)
        val followed = frame(effect, follow = follow)
        for (i in 0 until 12) {
            assertRotation(Quaternionf(follow).mul(still.rotation(i)), followed.rotation(i))
        }
    }

    @Test
    fun `display rotation and spin turn the model around its own normal`() {
        val display = text.copy(rotation = Vector(0.0, 0.0, PI / 2), spin = Vector(0.0, 0.0, 0.1), phase = 2.0)
        val opts = EffectOptions(display = display)
        val frame = frame(ring(DisplayOrientation.Normal("cos(t)", "0", "sin(t)")), opts, time = 5.0)
        val point = Quaternionf()
        for (i in 0 until 12) {
            val t = i * MathUtils.TAU / 12
            Orientation.facing(cos(t), 0.0, sin(t), point)
            val local = Quaternionf().rotateXYZ(0f, 0f, (PI / 2 + 0.1 * (5.0 + i * 2.0)).toFloat())
            assertRotation(Quaternionf(point).mul(local), frame.rotation(i))
            assertVector(Vector3f(cos(t).toFloat(), 0f, sin(t).toFloat()), frame.facing(i))
        }
    }

    @Test
    fun `fixed points turn in place with time and keep their coordinates`() {
        val effect = points("5", mapOf("x" to "index - 2", "y" to "0", "z" to "0", "rotZ" to "index * pi / 4 + time * 0.05"))
        val first = frame(effect, time = 0.0)
        val later = frame(effect, time = 20.0)
        assertEquals(5, first.display.count)
        assertEquals(5, later.display.count)
        for (i in 0 until 5) {
            assertEquals(first.position(i), later.position(i))
            assertEquals(center.x + i - 2, first.position(i).x, 1e-9)
            assertRotation(Quaternionf().rotateZ((i * PI / 4).toFloat()), first.rotation(i))
            assertRotation(Quaternionf().rotateZ((i * PI / 4 + 1.0).toFloat()), later.rotation(i))
        }
    }

    @Test
    fun `two angles compose in rotateXYZ order, not their reverse`() {
        val frame = frame(points("1", mapOf("x" to "0", "y" to "0", "z" to "0", "rotX" to "0.4", "rotY" to "0.9")))
        assertRotation(Quaternionf().rotateX(0.4f).rotateY(0.9f), frame.rotation(0))
        val reversed = Quaternionf().rotateY(0.9f).rotateX(0.4f)
        assertTrue(abs(reversed.dot(frame.rotation(0))) < 0.999f)
    }

    @Test
    fun `per-point yaw and spin turn coordinates and orientation together`() {
        val effect = points("6", mapOf("x" to "1 + index * 0.1", "y" to "0", "z" to "0", "yaw" to "index * pi / 3",
            "spin" to "index * 0.5", "normalX" to "1", "normalY" to "0", "normalZ" to "0"))
        val opts = textOptions.toBuilder().rotationYaw(0.25).rotationPitch(0.4).build()
        val frame = frame(effect, opts)
        for (i in 0 until 6) {
            val p = frame.position(i)
            val outward = Vector3f((p.x - center.x).toFloat(), (p.y - center.y).toFloat(), (p.z - center.z).toFloat()).normalize()
            assertVector(outward, frame.facing(i))
        }
    }

    @Test
    fun `skipped coordinates keep each orientation with its own index`() {
        val effect = points("4", mapOf("x" to "if(index == 1, sqrt(-1), index)", "y" to "0", "z" to "0", "rotZ" to "index"))
        val frame = frame(effect)
        assertEquals(3, frame.display.count)
        listOf(0, 2, 3).forEachIndexed { slot, index ->
            assertEquals(center.x + index, frame.position(slot).x, 1e-9)
            assertRotation(Quaternionf().rotateZ(index.toFloat()), frame.rotation(slot))
        }
    }

    @Test
    fun `degenerate values fall back to the identity point rotation for that point only`() {
        val opts = textOptions.toBuilder().rotationYaw(0.7).build()
        val geometry = Orientation.geometry(0.7, 0.0, 0.0, null, 0.0, Quaternionf())
        val angles = frame(points("3", mapOf("x" to "index", "y" to "0", "z" to "0", "rotZ" to "if(index == 1, sqrt(-1), 0.5)")), opts)
        assertEquals(3, angles.display.count)
        assertRotation(geometry, angles.rotation(1))
        assertRotation(Quaternionf(geometry).rotateZ(0.5f), angles.rotation(2))
        val normals = frame(points("2", mapOf("x" to "index", "y" to "0", "z" to "0",
            "normalX" to "index", "normalY" to "0", "normalZ" to "0")), opts)
        assertRotation(geometry, normals.rotation(0))
        assertTrue(normals.display.transform(0).translation.isFinite)
    }

    @Test
    fun `cylinder labels face outward with a small error only at the grid edge`() {
        val outward = frame(surface("2 * cos(u)", "v", "-2 * sin(u)", DisplayOrientation.Side.OUTWARD))
        val inward = frame(surface("2 * cos(u)", "v", "-2 * sin(u)", DisplayOrientation.Side.INWARD))
        assertEquals(96, outward.display.count)
        assertEquals(96, inward.display.count)
        var edgeError = 0.0
        for (i in 0 until 24) for (j in 0 until 4) {
            val index = i * 4 + j
            val u = i * MathUtils.TAU / 24
            val expected = Vector3f(cos(u).toFloat(), 0f, (-sin(u)).toFloat())
            val error = angle(expected, outward.facing(index))
            if (i == 0 || i == 23) edgeError = maxOf(edgeError, error) else assertTrue(error < 1e-3, "interior $i: $error")
            assertEquals(-1f, outward.facing(index).dot(inward.facing(index)), 1e-5f)
            assertVector(Vector3f(0f, 1f, 0f), outward.top(index))
            assertEquals(outward.position(index), inward.position(index))
        }
        // second-order one-sided differences keep the seam within half a degree
        assertTrue(edgeError > 0.0 && edgeError < Math.toRadians(0.5), "edge error $edgeError")
        // u = 0 is the seam, its analytic direction is +X
        assertVector(Vector3f(1f, 0f, 0f), outward.facing(0), tolerance = 0.01f)
    }

    @Test
    fun `a reversed range keeps the analytic side`() {
        val reversed = frame(surface("2 * cos(u)", "v", "-2 * sin(u)", DisplayOrientation.Side.OUTWARD,
            ranges = listOf(ParamRange(MathUtils.TAU, 0.0), ParamRange(-1.0, 1.0))))
        for (index in 4 until 92) {
            val p = reversed.position(index)
            val radial = Vector3f((p.x - center.x).toFloat(), 0f, (p.z - center.z).toFloat()).normalize()
            assertTrue(radial.dot(reversed.facing(index)) > 0.999f)
        }
    }

    @Test
    fun `a plane faces +Z outward and -Z inward at every sample including the edges`() {
        for ((side, z) in listOf(DisplayOrientation.Side.OUTWARD to 1f, DisplayOrientation.Side.INWARD to -1f)) {
            val frame = frame(surface("u", "v", "0", side, samples = listOf(6, 5)))
            for (index in 0 until 30) assertVector(Vector3f(0f, 0f, z), frame.facing(index), tolerance = 1e-5f)
        }
    }

    @Test
    fun `the inner side of a torus faces the axis, which a center heuristic would get wrong`() {
        val frame = frame(surface("(2 + 0.5 * cos(v)) * cos(u)", "0.5 * sin(v)", "-(2 + 0.5 * cos(v)) * sin(u)",
            DisplayOrientation.Side.OUTWARD, samples = listOf(24, 12), ranges = listOf(ParamRange(0.0, MathUtils.TAU), ParamRange(0.0, MathUtils.TAU))))
        for (i in 1 until 23) for (j in 1 until 11) {
            val u = i * MathUtils.TAU / 24
            val v = j * MathUtils.TAU / 12
            val expected = Vector3f((cos(v) * cos(u)).toFloat(), sin(v).toFloat(), (-cos(v) * sin(u)).toFloat())
            assertTrue(angle(expected, frame.facing(i * 12 + j)) < 0.02, "($i, $j)")
        }
        // v = pi is the inner equator, its normal points to the axis while the point lies away from the center
        val inner = 5 * 12 + 6
        val p = frame.position(inner)
        val fromCenter = Vector3f((p.x - center.x).toFloat(), (p.y - center.y).toFloat(), (p.z - center.z).toFloat()).normalize()
        assertTrue(fromCenter.dot(frame.facing(inner)) < -0.99f)
    }

    @Test
    fun `collinear tangents fall back to a finite transform and a tiny surface still orients`() {
        val opts = textOptions.toBuilder().rotationYaw(0.3).build()
        val geometry = Orientation.geometry(0.3, 0.0, 0.0, null, 0.0, Quaternionf())
        val line = frame(surface("u", "u", "0", DisplayOrientation.Side.OUTWARD), opts)
        for (index in 0 until line.display.count) {
            assertRotation(geometry, line.rotation(index))
            assertTrue(line.display.transform(index).translation.isFinite)
        }
        val tiny = frame(surface("1e-6 * cos(u)", "1e-6 * v", "-1e-6 * sin(u)", DisplayOrientation.Side.OUTWARD))
        assertTrue(angle(Vector3f(cos(MathUtils.TAU / 24).toFloat(), 0f, (-sin(MathUtils.TAU / 24)).toFloat()), tiny.facing(4)) < 1e-3)
    }

    @Test
    fun `normals near the display cap use the whole grid`() {
        val frame = frame(surface("2 * cos(u)", "v", "-2 * sin(u)", DisplayOrientation.Side.OUTWARD, samples = listOf(30, 10)))
        assertEquals(DisplayEntityPool.MAX_DISPLAYS, frame.display.count)
        // index 255 is sample (25, 5), an interior node whose neighbours (24, 5) and (26, 5) are both computed
        val u = 25 * MathUtils.TAU / 30
        assertTrue(angle(Vector3f(cos(u).toFloat(), 0f, (-sin(u)).toFloat()), frame.facing(255)) < 1e-3)
    }

    @Test
    fun `effects without orientation keep the legacy transform`() {
        val opts = textOptions.toBuilder().rotationYaw(0.9).build()
        val frame = frame(ring(null), opts, time = 3.0)
        assertNull(frame.buffer.orientations())
        for (i in 0 until 12) {
            assertEquals(DisplayEntityPool.modelTransformation(text, opts, i, 3.0), frame.display.transform(i))
        }
        // an explicit rotZ: 0 already follows the geometry rotation
        val zero = frame(points("2", mapOf("x" to "index", "y" to "0", "z" to "0", "rotZ" to "0")), opts)
        assertRotation(Quaternionf().rotateY(-0.9f), zero.rotation(1))
    }

    @Test
    fun `orientation is skipped for particles, viewer-facing billboards and ribbons`() {
        val effect = ring(DisplayOrientation.Normal("cos(t)", "0", "sin(t)"))
        val skipped = listOf(
            EffectOptions(),
            EffectOptions(display = text.copy(billboard = null)),
            EffectOptions(display = text.copy(billboard = Display.Billboard.VERTICAL)),
            EffectOptions(display = text.copy(billboard = Display.Billboard.HORIZONTAL)),
            EffectOptions(display = DisplayOptions(type = DisplayOptions.Type.RIBBON)),
            EffectOptions(display = DisplayOptions(type = DisplayOptions.Type.PIXEL)),
        )
        for (opts in skipped) {
            val buffer = ParticleBuffer(center)
            effect.calculateInto(buffer, center, opts, 0.0)
            assertNull(buffer.orientations(), opts.display.toString())
        }
        for (display in listOf(text.copy(withParticles = true), DisplayOptions(), DisplayOptions(type = DisplayOptions.Type.BLOCK),
            DisplayOptions(type = DisplayOptions.Type.PIXEL, billboard = Display.Billboard.FIXED))) {
            val buffer = ParticleBuffer(center)
            effect.calculateInto(buffer, center, EffectOptions(display = display), 0.0)
            assertNotNull(buffer.orientations(), display.toString())
        }
        // a frame built for a viewer-facing billboard ignores orientations it is handed
        val buffer = ParticleBuffer(center)
        effect.calculateInto(buffer, center, textOptions, 0.0)
        val viewer = text.copy(billboard = Display.Billboard.CENTER)
        val ignored = DisplayFrame(buffer.list(), EffectOptions(display = viewer), viewer, 0.0, null, null, null, buffer.orientations())
        assertEquals(DisplayEntityPool.modelTransformation(viewer, EffectOptions(display = viewer), 1, 0.0), ignored.transform(1))
    }

    @Test
    fun `block and pixel models keep their centre compensation under the point rotation`() {
        val effect = ring(DisplayOrientation.Normal("cos(t)", "0", "sin(t)"))
        val block = DisplayOptions(type = DisplayOptions.Type.BLOCK, size = Vector(0.4, 0.6, 0.8))
        val blockFrame = frame(effect, EffectOptions(display = block))
        for (i in 0 until 12) {
            val transform = blockFrame.display.transform(i)
            val expected = transform.leftRotation.transform(Vector3f(0.4f, 0.6f, 0.8f).mul(-0.5f))
            assertVector(expected, transform.translation, tolerance = 1e-6f)
        }
        val pixel = DisplayOptions(type = DisplayOptions.Type.PIXEL, billboard = Display.Billboard.FIXED)
        val pixelFrame = frame(effect, EffectOptions(display = pixel))
        for (i in 0 until 12) {
            val transform = pixelFrame.display.transform(i)
            val edge = pixel.size.x.toFloat()
            assertEquals(DisplayEntityPool.pixelTransformation(edge, edge, transform.leftRotation), transform)
        }
    }

    @Test
    fun `a reused buffer never carries orientations into another frame`() {
        val buffer = ParticleBuffer(center)
        frame(ring(DisplayOrientation.Normal("cos(t)", "0", "sin(t)")), buffer = buffer)
        assertNotNull(buffer.orientations())
        buffer.release()
        assertNull(buffer.orientations())
        frame(ring(null), buffer = buffer)
        assertNull(buffer.orientations())
        // more points than before grow the storage, appended points carry no orientation
        val dense = frame(points("100", mapOf("x" to "index", "y" to "0", "z" to "0", "rotZ" to "index")), buffer = buffer)
        assertEquals(100, dense.buffer.list().size)
        buffer.add(0.0, 0.0, 0.0)
        assertTrue(buffer.orientations()!![100 * ParticleBuffer.QUATERNION].isNaN())
    }

    @Test
    fun `concurrent plays with other parameters or displays do not share orientations`() {
        val effect = points("8", mapOf("x" to "index", "y" to "0", "z" to "0", "rotZ" to "index * k"), mapOf("k" to 1.0))
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 64).map { run ->
                Callable {
                    val k = (run % 4).toDouble()
                    val particles = run % 3 == 0
                    val opts = EffectOptions(parameters = mapOf("k" to k), display = if (particles) null else text)
                    val buffer = ParticleBuffer(center)
                    effect.calculateInto(buffer, center, opts, run.toDouble())
                    if (particles) {
                        assertNull(buffer.orientations())
                    } else {
                        val frame = DisplayFrame(buffer.list(), opts, text, 0.0, null, null, null, buffer.orientations())
                        for (i in 0 until 8) assertRotation(Quaternionf().rotateZ((i * k).toFloat()), frame.transform(i).leftRotation)
                    }
                }
            }
            pool.invokeAll(tasks).forEach { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    private fun angle(expected: Vector3f, actual: Vector3f): Double =
        acos((Vector3f(expected).normalize().dot(Vector3f(actual).normalize())).toDouble().coerceIn(-1.0, 1.0))

    private fun assertVector(expected: Vector3f, actual: Vector3f, tolerance: Float = 1e-4f) {
        val d = Vector3f(expected).sub(actual)
        assertTrue(sqrt(d.lengthSquared()) < tolerance, "expected $expected, got $actual")
    }

    private fun assertRotation(expected: Quaternionf, actual: Quaternionf) {
        assertTrue(abs(expected.dot(actual)) > 1f - 1e-5f, "expected $expected, got $actual")
    }
}
