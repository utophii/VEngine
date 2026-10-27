package com.utophii.shapes

import com.utophii.api.EffectOptions
import com.utophii.effects.FourierEffect
import com.utophii.effects.GradientColors
import com.utophii.effects.ParticleBuffer
import com.utophii.effects.ShapeEffect
import com.utophii.engine.PlaceholderResolver
import com.utophii.engine.Placeholders
import org.bukkit.Location
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Font
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// Verifies the shape importers against outlines and models whose geometry is known exactly.
class ShapesTest {

    @AfterEach
    fun resetResolver() {
        Placeholders.resolver = null
    }

    private fun center(): Location = Location(null, 0.0, 0.0, 0.0)

    private fun frame(effect: com.utophii.effects.AbstractParticleEffect, opts: EffectOptions, time: Double = 0.0): ParticleBuffer {
        val buffer = ParticleBuffer(center())
        effect.calculateInto(buffer, center(), opts, time)
        return buffer
    }

    @Test
    fun `svg arcs trace a circle`() {
        // two half arcs of radius 5 around (10, 10)
        val contours = Outlines.contours(SvgShape.parsePath("M5 10 A5 5 0 0 1 15 10 A5 5 0 0 1 5 10 Z"))
        assertEquals(1, contours.size)
        val contour = contours[0]
        for (offset in contour.indices step 2) {
            assertEquals(5.0, hypot(contour[offset] - 10.0, contour[offset + 1] + 10.0), LOOSE_EPS)
        }
    }

    @Test
    fun `svg path numbers may run together and repeat their command`() {
        // relative lineto pairs after m, "-5-0" and ".5.5" run together
        val bounds = SvgShape.parsePath("m0 0 10 0 0 10-10-0z M20,20h.5.5v1").bounds2D
        assertEquals(0.0, bounds.minX, EPS)
        assertEquals(21.0, bounds.maxX, EPS)
        assertEquals(21.0, bounds.maxY, EPS)
    }

    @Test
    fun `svg arc flags need no separators`() {
        val compact = SvgShape.parsePath("M0 0a5 5 0 0110 0").bounds2D
        val spaced = SvgShape.parsePath("M0 0 a 5 5 0 0 1 10 0").bounds2D
        assertEquals(spaced, compact)
        assertEquals(-5.0, compact.minY, LOOSE_EPS)
    }

    @Test
    fun `svg transforms apply right to left`() {
        // translate(10) rotate(90) maps (1, 0) to (10, 1)
        val point = SvgShape.parseTransform("translate(10 0) rotate(90)").transform(java.awt.geom.Point2D.Double(1.0, 0.0), null)
        assertEquals(10.0, point.x, EPS)
        assertEquals(1.0, point.y, EPS)
    }

    @Test
    fun `outline points lie on the square and run along it`() {
        val cloud = Outlines.outline(Outlines.contours(Rectangle2D.Double(0.0, 0.0, 2.0, 2.0)), 80)
        assertEquals(80, cloud.count)
        for (index in 0 until cloud.count) {
            val x = cloud.points[index * 3]
            val y = -cloud.points[index * 3 + 1]
            val onEdge = abs(x) < EPS || abs(x - 2.0) < EPS || abs(y) < EPS || abs(y - 2.0) < EPS
            assertTrue(onEdge, "($x, $y) is off the square")
            if (index > 0) assertTrue(cloud.values[index] > cloud.values[index - 1])
        }
    }

    @Test
    fun `fill points stay inside and within the budget`() {
        val square = Rectangle2D.Double(0.0, 0.0, 4.0, 4.0)
        val cloud = Outlines.fill(square, 400)
        assertTrue(cloud.count in 300..400, "${cloud.count} points")
        for (index in 0 until cloud.count) {
            assertTrue(square.contains(cloud.points[index * 3], -cloud.points[index * 3 + 1]))
        }
    }

    @Test
    fun `fitting scales the largest side and anchors the base`() {
        val cloud = Outlines.outline(Outlines.contours(Rectangle2D.Double(0.0, 0.0, 4.0, 2.0)), 60).fitted(2.0, anchorBase = true)
        val bounds = Bounds.of(cloud.points)
        assertEquals(2.0, bounds.maxX - bounds.minX, EPS)
        assertEquals(0.0, bounds.minY, EPS)
        assertEquals(0.0, bounds.centerX, EPS)
    }

    @Test
    fun `obj surface points lie on the cube faces`() {
        val cube = ObjShape.parse(CUBE)
        val cloud = cube.cloud(ObjShape.Mode.SURFACE, 600)
        assertEquals(600, cloud.count)
        var top = 0
        for (index in 0 until cloud.count) {
            val coords = (0 until 3).map { cloud.points[index * 3 + it] }
            assertTrue(coords.all { it in -EPS..1.0 + EPS })
            assertTrue(coords.any { abs(it) < EPS || abs(it - 1.0) < EPS }, "$coords is inside the cube")
            if (abs(coords[1] - 1.0) < EPS) top++
        }
        // every face holds about a sixth of the points
        assertTrue(top in 80..120, "$top points on the top face")
    }

    @Test
    fun `obj edges are shared once and sampled along their length`() {
        val cube = ObjShape.parse(CUBE)
        val cloud = cube.cloud(ObjShape.Mode.EDGES, 120)
        // 12 edges of length 1: 10 points each
        assertEquals(120, cloud.count)
        for (index in 0 until cloud.count) {
            val onBorder = (0 until 3).count { axis -> cloud.points[index * 3 + axis].let { abs(it) < EPS || abs(it - 1.0) < EPS } }
            assertTrue(onBorder >= 2, "point $index is not on an edge")
        }
    }

    @Test
    fun `obj negative indices count back and bad indices are reported`() {
        val triangle = ObjShape.parse(listOf("v 0 0 0", "v 1 0 0", "v 0 1 0", "f -3 -2/5 -1//2"))
        assertEquals(3, triangle.cloud(ObjShape.Mode.VERTICES, 10).count)
        assertThrows(ShapeException::class.java) { ObjShape.parse(listOf("v 0 0 0", "f 1 2 3")) }
    }

    @Test
    fun `fourier series of a circle is one epicycle`() {
        val circle = DoubleArray(202) { index ->
            val angle = 2 * PI * (index / 2) / 100
            if (index % 2 == 0) 2.0 * cos(angle) else 2.0 * sin(angle)
        }
        val series = FourierSeries.of(listOf(circle), 128)
        assertEquals(2.0, series.radius(1), 1e-2)
        assertEquals(1, series.frequencies[1])
        assertTrue(series.radius(2) < 1e-2)
        assertTrue(series.penDown.all { it })
    }

    @Test
    fun `fourier series with every term passes through the samples and lifts the pen on jumps`() {
        val first = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 0.0)
        val second = doubleArrayOf(5.0, 0.0, 6.0, 0.0, 6.0, 1.0, 5.0, 0.0)
        val series = FourierSeries.of(listOf(first, second), 256)
        assertTrue(series.penDown.any { !it } && series.penDown.any { it })
        val curve = series.curve(series.maxTerms)
        // sample 0 is the start of the first contour
        assertEquals(0.0, curve[0], 1e-9)
        assertEquals(0.0, curve[1], 1e-9)
    }

    @Test
    fun `fourier trace grows with the pen and parts can be hidden`() {
        val square = Outlines.fitted(Outlines.contours(Rectangle2D.Double(0.0, 0.0, 1.0, 1.0)), 2.0, anchorBase = false)
        val effect = FourierEffect(
            "test_fourier", ShapeVariants.fixed(FourierSeries.of(square, 128)),
            defaultEpicycles = 20, periodTicks = 100.0, trail = 1.0, spacing = 0.1, angularSpeed = 0.0,
        )
        val traceOnly = EffectOptions(parameters = mapOf("arms" to 0.0, "circles" to 0.0))
        assertEquals(65, frame(effect, traceOnly, time = 50.0).list().size)
        assertEquals(128, frame(effect, traceOnly, time = 250.0).list().size)
        val machinery = frame(effect, EffectOptions(parameters = mapOf("trace" to 0.0)), time = 50.0)
        assertTrue(machinery.list().isNotEmpty())
        // a trail of a quarter keeps a quarter of the loop
        assertEquals(32, frame(effect, EffectOptions(parameters = mapOf("arms" to 0.0, "circles" to 0.0, "trail" to 0.25)), 250.0).list().size)
    }

    @Test
    fun `image keeps visible pixels with their colors`() {
        val image = BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFFFF0000.toInt())
        image.setRGB(3, 1, 0xFF00FF00.toInt())
        image.setRGB(1, 1, 0x20FFFFFF)
        val cloud = ImageShape.pixels(image, ImageShape.Filter(0.5, background = null, tolerance = 0.1), 100)
        assertEquals(2, cloud.count)
        val colors = cloud.colors!!.toSet()
        assertEquals(setOf(0xFF0000, 0x00FF00), colors)
        val red = cloud.colors!!.indexOf(0xFF0000)
        // the top left pixel sits at the top of the upright picture
        assertEquals(0.5, cloud.points[red * 3], EPS)
        assertEquals(1.5, cloud.points[red * 3 + 1], EPS)
    }

    @Test
    fun `image background color is left out`() {
        val image = BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, 0xFAFAFA)
        image.setRGB(1, 0, 0x101010)
        val cloud = ImageShape.pixels(image, ImageShape.Filter(0.5, background = 0xFFFFFF, tolerance = 0.1), 10)
        assertEquals(listOf(0x101010), cloud.colors!!.toList())
    }

    @Test
    fun `shape effect grows its points and passes the source colors`() {
        val cloud = PointCloud(DoubleArray(30), DoubleArray(10) { it / 9.0 }, IntArray(10) { 0x123456 })
        val effect = ShapeEffect("test_shape", ShapeVariants.fixed(cloud), growthTicks = 20.0, angularSpeed = 0.0, sourceColors = true)
        val half = frame(effect, EffectOptions(), time = 10.0)
        assertEquals(5, half.list().size)
        assertTrue(half.hasColors())
        assertEquals(0x123456, half.colorAt(4))
        assertEquals(10, frame(effect, EffectOptions(), time = 40.0).list().size)
    }

    @Test
    fun `own particle colors win over the gradient`() {
        val buffer = ParticleBuffer(center())
        buffer.acquire(2)
        buffer.color(0, 0xFF8800)
        buffer.value(1, 1.0)
        val colors = GradientColors()
        val opts = EffectOptions(particle = org.bukkit.Particle.DUST, gradient = listOf(org.bukkit.Color.BLACK, org.bukkit.Color.WHITE))
        assertTrue(colors.resolve(buffer, opts))
        assertEquals(0xFFFF8800.toInt(), colors.argb()[0])
        assertEquals(0xFFFFFFFF.toInt(), colors.argb()[1])
        assertTrue(colors.colorsParticles())
        // the same color reuses one payload
        buffer.color(1, 0xFF8800)
        colors.resolve(buffer, opts)
        assertSame(colors.dataAt(0), colors.dataAt(1))
    }

    @Test
    fun `text outlines draw glyphs`() {
        val outline = TextShape(Font(Font.SANS_SERIF, Font.PLAIN, 100)).outline("Hi\nthere")
        val contours = Outlines.contours(outline)
        assertTrue(contours.size >= 3, "${contours.size} contours")
    }

    @Test
    fun `placeholder texts get their own geometry per play`() {
        val variants = ShapeVariants.text("hi %player_name%") { text -> text.length }
        assertTrue(variants.playParameters(emptyMap(), null).isEmpty(), "no PlaceholderAPI, no variant")
        Placeholders.resolver = PlaceholderResolver { _, _ -> "Alexander" }
        val parameters = variants.playParameters(emptyMap(), null)
        assertFalse(parameters.isEmpty())
        val resolved = variants.resolve(EffectOptions(parameters = parameters))
        assertEquals("hi Alexander".length, resolved)
        assertNotEquals(variants.base, resolved)
        assertEquals(variants.base, variants.resolve(EffectOptions()))
    }

    companion object {
        private const val EPS = 1e-9
        private const val LOOSE_EPS = 1e-2

        // unit cube, quads
        private val CUBE = listOf(
            "v 0 0 0", "v 1 0 0", "v 1 1 0", "v 0 1 0", "v 0 0 1", "v 1 0 1", "v 1 1 1", "v 0 1 1",
            "f 1 2 3 4", "f 5 8 7 6", "f 1 5 6 2", "f 2 6 7 3", "f 3 7 8 4", "f 5 1 4 8",
        )
    }
}
