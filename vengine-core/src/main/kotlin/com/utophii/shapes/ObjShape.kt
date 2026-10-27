package com.utophii.shapes

import com.utophii.math.Hash
import java.io.File
import kotlin.math.sqrt

// a Wavefront OBJ model: v (vertices), f (polygon faces) and l (polylines), everything else is ignored.
// https://en.wikipedia.org/wiki/Wavefront_.obj_file
// OBJ models are y-up like Minecraft, so they stand as modeled. The gradient value is the height
class ObjShape private constructor(
    // x, y, z per vertex
    private val vertices: DoubleArray,
    // three vertex indices per triangle
    private val triangles: IntArray,
    // two vertex indices per edge, every edge once
    private val edges: IntArray,
) {
    enum class Mode { VERTICES, EDGES, SURFACE }

    fun cloud(mode: Mode, points: Int): PointCloud {
        val positions = when (mode) {
            Mode.VERTICES -> vertexPoints(points)
            Mode.EDGES -> if (edges.isEmpty()) vertexPoints(points) else edgePoints(points)
            Mode.SURFACE -> if (triangles.isEmpty()) edgePoints(points) else surfacePoints(points)
        }
        val bounds = Bounds.of(positions)
        val height = bounds.maxY - bounds.minY
        val values = DoubleArray(positions.size / 3) { index ->
            if (height > 0.0) (positions[index * 3 + 1] - bounds.minY) / height else 0.0
        }
        return PointCloud(positions, values)
    }

    // every vertex, or an even subset of them in file order
    private fun vertexPoints(points: Int): DoubleArray {
        val total = vertices.size / 3
        val count = minOf(total, points)
        val result = DoubleArray(count * 3)
        for (index in 0 until count) {
            val source = (index.toLong() * total / count).toInt()
            vertices.copyInto(result, index * 3, source * 3, source * 3 + 3)
        }
        return result
    }

    // points evenly spaced along the edges, edge after edge
    private fun edgePoints(points: Int): DoubleArray {
        val edgeCount = edges.size / 2
        val lengths = DoubleArray(edgeCount) { edge -> distance(edges[edge * 2], edges[edge * 2 + 1]) }
        val spacing = lengths.sum() / points
        // n = round(length / spacing) points per edge, the edge's start included and its end left to its neighbor
        val steps = IntArray(edgeCount) { edge -> (lengths[edge] / spacing + HALF).toInt() }
        val count = minOf(steps.sum(), points)
        val result = DoubleArray(count * 3)
        var produced = 0
        for (edge in 0 until edgeCount) {
            val a = edges[edge * 2] * 3
            val b = edges[edge * 2 + 1] * 3
            for (step in 0 until steps[edge]) {
                if (produced >= count) break
                val t = step.toDouble() / steps[edge]
                for (axis in 0 until 3) {
                    result[produced * 3 + axis] = vertices[a + axis] + (vertices[b + axis] - vertices[a + axis]) * t
                }
                produced++
            }
        }
        return result
    }

    // points spread over the faces by area: point i picks the triangle holding the (i + jitter) / n share of the
    // total area, so every triangle gets its share without clumping, and a uniform point inside it
    private fun surfacePoints(points: Int): DoubleArray {
        val triangleCount = triangles.size / 3
        val cumulative = DoubleArray(triangleCount)
        var total = 0.0
        for (triangle in 0 until triangleCount) {
            total += area(triangle)
            cumulative[triangle] = total
        }
        val result = DoubleArray(points * 3)
        for (index in 0 until points) {
            var hash = Hash.mix(index.toLong())
            val target = (index + Hash.unit(hash)) / points * total
            val found = cumulative.binarySearch(target)
            val triangle = (if (found >= 0) found else -found - 1).coerceAtMost(triangleCount - 1)
            hash = Hash.mix(hash)
            var u = Hash.unit(hash)
            hash = Hash.mix(hash)
            var v = Hash.unit(hash)
            // reflect points of the far half of the unit square back into the triangle
            if (u + v > 1.0) {
                u = 1.0 - u
                v = 1.0 - v
            }
            val a = triangles[triangle * 3] * 3
            val b = triangles[triangle * 3 + 1] * 3
            val c = triangles[triangle * 3 + 2] * 3
            for (axis in 0 until 3) {
                // p = a + u (b - a) + v (c - a)
                result[index * 3 + axis] = vertices[a + axis] + u * (vertices[b + axis] - vertices[a + axis]) +
                    v * (vertices[c + axis] - vertices[a + axis])
            }
        }
        return result
    }

    private fun distance(first: Int, second: Int): Double {
        val dx = vertices[second * 3] - vertices[first * 3]
        val dy = vertices[second * 3 + 1] - vertices[first * 3 + 1]
        val dz = vertices[second * 3 + 2] - vertices[first * 3 + 2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    // |(b - a) × (c - a)| / 2
    private fun area(triangle: Int): Double {
        val a = triangles[triangle * 3] * 3
        val b = triangles[triangle * 3 + 1] * 3
        val c = triangles[triangle * 3 + 2] * 3
        val abX = vertices[b] - vertices[a]
        val abY = vertices[b + 1] - vertices[a + 1]
        val abZ = vertices[b + 2] - vertices[a + 2]
        val acX = vertices[c] - vertices[a]
        val acY = vertices[c + 1] - vertices[a + 1]
        val acZ = vertices[c + 2] - vertices[a + 2]
        val crossX = abY * acZ - abZ * acY
        val crossY = abZ * acX - abX * acZ
        val crossZ = abX * acY - abY * acX
        return sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ) * HALF
    }

    companion object {
        private const val HALF = 0.5

        fun load(file: File): ObjShape = runCatching { parse(file.readLines()) }.getOrElse { error ->
            if (error is ShapeException) throw ShapeException("'${file.name}': ${error.message}", error)
            throw ShapeException("'${file.name}' is not a readable OBJ file (${error.message})", error)
        }

        fun parse(lines: List<String>): ObjShape {
            val vertices = ArrayList<Double>()
            val triangles = ArrayList<Int>()
            val edges = LinkedHashSet<Long>()
            lines.forEachIndexed { lineIndex, raw ->
                val parts = raw.substringBefore('#').trim().split(WHITESPACE)
                when (parts[0]) {
                    "v" -> {
                        if (parts.size < 4) throw ShapeException("line ${lineIndex + 1}: a vertex needs x y z")
                        for (axis in 1..3) vertices += parts[axis].toDouble()
                    }
                    "f", "l" -> {
                        val count = vertices.size / 3
                        // "7", "7/1", "7//3", "7/1/3", negative indices count back from the last vertex
                        val indices = parts.drop(1).map { token ->
                            val number = token.substringBefore('/').toInt()
                            val index = if (number < 0) count + number else number - 1
                            if (index !in 0 until count) throw ShapeException("line ${lineIndex + 1}: vertex $number does not exist")
                            index
                        }
                        val closed = parts[0] == "f"
                        if (closed && indices.size >= 3) {
                            // fan triangulation, exact for convex faces
                            for (corner in 1 until indices.size - 1) {
                                triangles += indices[0]
                                triangles += indices[corner]
                                triangles += indices[corner + 1]
                            }
                        }
                        val last = if (closed) indices.size else indices.size - 1
                        for (corner in 0 until last) {
                            val a = indices[corner]
                            val b = indices[(corner + 1) % indices.size]
                            // an undirected edge key, so faces sharing an edge add it once
                            if (a != b) edges += minOf(a, b).toLong() shl 32 or maxOf(a, b).toLong()
                        }
                    }
                }
            }
            if (vertices.isEmpty()) throw ShapeException("no vertices")
            val edgeArray = IntArray(edges.size * 2)
            edges.forEachIndexed { index, key ->
                edgeArray[index * 2] = (key ushr 32).toInt()
                edgeArray[index * 2 + 1] = key.toInt()
            }
            return ObjShape(vertices.toDoubleArray(), triangles.toIntArray(), edgeArray)
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
