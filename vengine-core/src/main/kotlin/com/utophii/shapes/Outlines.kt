package com.utophii.shapes

import java.awt.Shape
import java.awt.geom.Path2D
import java.awt.geom.PathIterator
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sqrt

// turns 2D vector outlines into particles.
// AWT shapes have y pointing down; the contours here have y pointing up, so the drawing stands upright in the XY plane
object Outlines {
    // flattened shape: one (x, y) polyline per subpath, a closed subpath repeats its first point at the end
    fun contours(shape: Shape): List<DoubleArray> {
        val bounds = shape.bounds2D
        // curves are cut into segments that stray at most this far from the true curve
        val flatness = maxOf(bounds.width, bounds.height).coerceAtLeast(MIN_EXTENT) * FLATNESS
        val iterator = shape.getPathIterator(null, flatness)
        val contours = ArrayList<DoubleArray>()
        val current = DoubleList()
        val coords = DoubleArray(COORDS)
        var startX = 0.0
        var startY = 0.0
        while (!iterator.isDone) {
            when (iterator.currentSegment(coords)) {
                PathIterator.SEG_MOVETO -> {
                    current.flushInto(contours)
                    startX = coords[0]
                    startY = -coords[1]
                    current.add(startX, startY)
                }
                PathIterator.SEG_LINETO -> current.add(coords[0], -coords[1])
                PathIterator.SEG_CLOSE -> {
                    current.add(startX, startY)
                    current.flushInto(contours)
                    // a path may go on drawing from the closed subpath's start
                    current.add(startX, startY)
                }
            }
            iterator.next()
        }
        current.flushInto(contours)
        return contours
    }

    // scales the contours so their largest extent is size blocks, centered on the origin
    // or with their lowest point on it
    fun fitted(contours: List<DoubleArray>, size: Double, anchorBase: Boolean): List<DoubleArray> {
        val all = contours.flatMap(DoubleArray::asIterable).toDoubleArray()
        val bounds = Bounds.of(all, stride = 2)
        val fit = size / bounds.extent
        val anchorY = if (anchorBase) bounds.minY else bounds.centerY
        return contours.map { contour ->
            DoubleArray(contour.size) { index ->
                if (index % 2 == 0) (contour[index] - bounds.centerX) * fit else (contour[index] - anchorY) * fit
            }
        }
    }

    // points evenly spaced along all contours, in drawing order, the gradient value is the arc position
    fun outline(contours: List<DoubleArray>, points: Int): PointCloud {
        val total = contours.sumOf(::length)
        if (total <= 0.0 || points <= 0) {
            return PointCloud(DoubleArray(0), DoubleArray(0))
        }
        val spacing = total / points
        val positions = DoubleList()
        val values = ArrayList<Double>(points)
        // distance from the start of the first contour
        var walked = 0.0
        // distance to the next sample along the current segment
        var next = 0.0
        for (contour in contours) {
            for (offset in 0 until contour.size - 2 step 2) {
                val ax = contour[offset]
                val ay = contour[offset + 1]
                val bx = contour[offset + 2]
                val by = contour[offset + 3]
                val segment = hypot(bx - ax, by - ay)
                while (next <= segment && values.size < points) {
                    // p = a + (b - a) d / |b - a|
                    val t = if (segment > 0.0) next / segment else 0.0
                    positions.add(ax + (bx - ax) * t, ay + (by - ay) * t)
                    values += (walked + next) / total
                    next += spacing
                }
                next -= segment
                walked += segment
            }
            // every contour starts with a particle on its first point
            next = 0.0
        }
        return flatCloud(positions, values.toDoubleArray())
    }

    // points on a square grid inside the shape,
    // ordered top to bottom so the fill grows downwards, the gradient value is the height
    fun fill(shape: Shape, points: Int): PointCloud {
        val bounds = shape.bounds2D
        if (bounds.isEmpty || points <= 0) {
            return PointCloud(DoubleArray(0), DoubleArray(0))
        }
        // estimate the inside area on a coarse grid, then pick the grid step that gives about points points
        var inside = 0
        for (row in 0 until PROBE) for (column in 0 until PROBE) {
            val x = bounds.minX + (column + HALF) * bounds.width / PROBE
            val y = bounds.minY + (row + HALF) * bounds.height / PROBE
            if (shape.contains(x, y)) inside++
        }
        val area = bounds.width * bounds.height * inside.coerceAtLeast(1) / (PROBE * PROBE)
        var step = sqrt(area / points)
        while (true) {
            val cloud = grid(shape, step, points)
            if (cloud != null) return cloud
            // the estimate was low: a coarser grid
            step *= GRID_GROWTH
        }
    }

    // inside grid points, or null when there are more than limit
    private fun grid(shape: Shape, step: Double, limit: Int): PointCloud? {
        val bounds = shape.bounds2D
        val columns = ceil(bounds.width / step).toInt().coerceAtLeast(1)
        val rows = ceil(bounds.height / step).toInt().coerceAtLeast(1)
        val positions = DoubleList()
        var count = 0
        for (row in 0 until rows) for (column in 0 until columns) {
            val x = bounds.minX + (column + HALF) * step
            val y = bounds.minY + (row + HALF) * step
            if (shape.contains(x, y)) {
                if (++count > limit) return null
                positions.add(x, -y)
            }
        }
        val values = DoubleArray(count) { index ->
            // values from 0 at the bottom to 1 at the top
            val y = positions[index * 2 + 1]
            if (bounds.height > 0.0) (y + bounds.maxY) / bounds.height else 0.0
        }
        return flatCloud(positions, values)
    }

    // the y-down shape as a path with the given winding rule
    fun path(shape: Shape, evenOdd: Boolean): Path2D.Double =
        Path2D.Double(shape).apply { windingRule = if (evenOdd) Path2D.WIND_EVEN_ODD else Path2D.WIND_NON_ZERO }

    fun length(contour: DoubleArray): Double {
        var total = 0.0
        for (offset in 0 until contour.size - 2 step 2) {
            total += hypot(contour[offset + 2] - contour[offset], contour[offset + 3] - contour[offset + 1])
        }
        return total
    }

    // (x, y) pairs into a z = 0 cloud
    private fun flatCloud(positions: DoubleList, values: DoubleArray): PointCloud {
        val points = DoubleArray(values.size * 3)
        for (index in values.indices) {
            points[index * 3] = positions[index * 2]
            points[index * 3 + 1] = positions[index * 2 + 1]
        }
        return PointCloud(points, values)
    }

    // growable list of doubles without boxing
    class DoubleList {
        private var data = DoubleArray(INITIAL_CAPACITY)
        var size = 0
            private set

        fun add(x: Double, y: Double) {
            if (size + 2 > data.size) data = data.copyOf(data.size * 2)
            data[size++] = x
            data[size++] = y
        }

        operator fun get(index: Int): Double = data[index]

        fun toArray(): DoubleArray = data.copyOf(size)

        // moves a polyline of at least two points into contours and clears the list
        fun flushInto(contours: MutableList<DoubleArray>) {
            if (size >= 4) contours += toArray()
            size = 0
        }
    }

    private const val INITIAL_CAPACITY = 64
    private const val COORDS = 6
    private const val HALF = 0.5
    private const val MIN_EXTENT = 1e-9

    // flatness as a fraction of the larger side of the shape
    private const val FLATNESS = 1e-3

    // samples per side of the area probe
    private const val PROBE = 64
    private const val GRID_GROWTH = 1.05
}
