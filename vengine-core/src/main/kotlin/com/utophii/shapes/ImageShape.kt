package com.utophii.shapes

import com.utophii.math.Hash
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt

// one particle per visible pixel of a picture (PNG, JPEG, GIF, BMP), in the XY plane with y up.
// A picture with more visible pixels than the particle budget is shrunk first, averaging the pixels it merges
object ImageShape {

    // alpha: pixels at or below this opacity (0..1) are left out
    // background: pixels within tolerance of this RGB color are left out too
    fun load(file: File, points: Int, alpha: Double, background: Int?, tolerance: Double): PointCloud {
        val image = runCatching { ImageIO.read(file) }.getOrNull()
            ?: throw ShapeException("'${file.name}' is not a readable image (PNG, JPEG, GIF or BMP)")
        val keep = Filter(alpha, background, tolerance)
        val visible = countVisible(image, keep)
        if (visible == 0) {
            throw ShapeException("'${file.name}' has no visible pixels")
        }
        // shrink by f = √(points / visible), so about points pixels stay visible
        val shrink = sqrt(points.toDouble() / visible).coerceAtMost(1.0)
        val width = (image.width * shrink).toInt().coerceAtLeast(1)
        val height = (image.height * shrink).toInt().coerceAtLeast(1)
        val sampled = if (shrink < 1.0) scaled(image, width, height) else image
        return pixels(sampled, keep, points)
    }

    // the visible pixels as particles, the order is shuffled so a growing picture fades in all over at once
    fun pixels(image: BufferedImage, keep: Filter, limit: Int): PointCloud {
        val kept = ArrayList<Int>()
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (keep.test(image.getRGB(x, y))) kept += y * image.width + x
        }
        // a fixed shuffle: same picture, same order
        val order = kept.sortedBy { pixel -> Hash.mix(pixel.toLong()) }.take(limit)
        val points = DoubleArray(order.size * 3)
        val values = DoubleArray(order.size)
        val colors = IntArray(order.size)
        order.forEachIndexed { index, pixel ->
            val x = pixel % image.width
            val y = pixel / image.width
            val argb = image.getRGB(x, y)
            // pixel centers, y flipped so the picture stands upright
            points[index * 3] = x + HALF
            points[index * 3 + 1] = image.height - y - HALF
            colors[index] = argb and RGB_MASK
            values[index] = luminance(argb)
        }
        return PointCloud(points, values, colors)
    }

    private fun countVisible(image: BufferedImage, keep: Filter): Int {
        var count = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (keep.test(image.getRGB(x, y))) count++
        }
        return count
    }

    private fun scaled(image: BufferedImage, width: Int, height: Int): BufferedImage {
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        try {
            graphics.drawImage(image.getScaledInstance(width, height, Image.SCALE_AREA_AVERAGING), 0, 0, null)
        } finally {
            graphics.dispose()
        }
        return result
    }

    // Rec. 709 relative luminance of the gamma-encoded channels, 0..1
    private fun luminance(argb: Int): Double {
        val r = (argb shr 16 and 0xFF) / CHANNEL
        val g = (argb shr 8 and 0xFF) / CHANNEL
        val b = (argb and 0xFF) / CHANNEL
        return LUMA_R * r + LUMA_G * g + LUMA_B * b
    }

    class Filter(alpha: Double, private val background: Int?, tolerance: Double) {
        private val minAlpha = (alpha.coerceIn(0.0, 1.0) * CHANNEL).toInt()

        // largest per-channel difference that still counts as background
        private val maxDifference = (tolerance.coerceIn(0.0, 1.0) * CHANNEL).toInt()

        fun test(argb: Int): Boolean {
            // fully opaque pixels always pass, even with alpha: 1
            val opacity = argb ushr 24
            if (opacity < OPAQUE && opacity <= minAlpha) return false
            val key = background ?: return true
            return abs((argb shr 16 and 0xFF) - (key shr 16 and 0xFF)) > maxDifference ||
                abs((argb shr 8 and 0xFF) - (key shr 8 and 0xFF)) > maxDifference ||
                abs((argb and 0xFF) - (key and 0xFF)) > maxDifference
        }
    }

    private const val HALF = 0.5
    private const val CHANNEL = 255.0
    private const val OPAQUE = 255
    private const val RGB_MASK = 0xFFFFFF
    private const val LUMA_R = 0.2126
    private const val LUMA_G = 0.7152
    private const val LUMA_B = 0.0722
}
