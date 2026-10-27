package com.utophii.shapes

// particles of an imported shape: positions in blocks around the effect position, a gradient value per point
// and optionally an RGB color per point. The order of the points is the order they appear in while the shape grows
class PointCloud(
    // x, y, z per point
    val points: DoubleArray,
    // gradient value in 0..1 per point
    val values: DoubleArray,
    // 0xRRGGBB per point, or null when the source has no colors
    val colors: IntArray? = null,
) {
    val count: Int
        get() = values.size

    // scales the cloud so its largest extent is size blocks, centered on the origin
    // or with its lowest point on it
    fun fitted(size: Double, anchorBase: Boolean): PointCloud {
        if (count == 0) {
            return this
        }
        val bounds = Bounds.of(points)
        val fit = size / bounds.extent
        val fittedPoints = DoubleArray(points.size)
        val anchorY = if (anchorBase) bounds.minY else bounds.centerY
        for (index in 0 until count) {
            val offset = index * 3
            fittedPoints[offset] = (points[offset] - bounds.centerX) * fit
            fittedPoints[offset + 1] = (points[offset + 1] - anchorY) * fit
            fittedPoints[offset + 2] = (points[offset + 2] - bounds.centerZ) * fit
        }
        return PointCloud(fittedPoints, values, colors)
    }
}

// axis-aligned bounding box of packed xyz points
class Bounds(
    val minX: Double,
    val minY: Double,
    val minZ: Double,
    val maxX: Double,
    val maxY: Double,
    val maxZ: Double,
) {
    val centerX: Double get() = (minX + maxX) * HALF
    val centerY: Double get() = (minY + maxY) * HALF
    val centerZ: Double get() = (minZ + maxZ) * HALF

    // largest side, never zero so it can divide
    val extent: Double get() = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(MIN_EXTENT)

    companion object {
        private const val HALF = 0.5
        private const val MIN_EXTENT = 1e-9

        fun of(points: DoubleArray, stride: Int = 3): Bounds {
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var minZ = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            var maxZ = Double.NEGATIVE_INFINITY
            var offset = 0
            while (offset < points.size) {
                val x = points[offset]
                val y = points[offset + 1]
                val z = if (stride > 2) points[offset + 2] else 0.0
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
                minZ = minOf(minZ, z); maxZ = maxOf(maxZ, z)
                offset += stride
            }
            if (minX > maxX) {
                return Bounds(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
            }
            return Bounds(minX, minY, minZ, maxX, maxY, maxZ)
        }
    }
}

// thrown for a shape file that cannot be read, the message is shown to the server owner
class ShapeException(message: String, cause: Throwable? = null) : Exception(message, cause)
