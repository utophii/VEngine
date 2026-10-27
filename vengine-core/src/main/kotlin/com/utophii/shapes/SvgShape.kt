package com.utophii.shapes

import org.w3c.dom.Element
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

// the geometry of an SVG file as one AWT path in user units.
// https://www.w3.org/TR/SVG11/paths.html
// Reads <path>, <rect>, <circle>, <ellipse>, <line>, <polyline> and <polygon> with their transform attributes
// and those of the enclosing groups. Styles, <use>, <text>, clipping and masks are ignored
object SvgShape {

    fun load(file: File): Path2D.Double {
        val document = runCatching { factory().newDocumentBuilder().parse(file) }.getOrElse { error ->
            throw ShapeException("'${file.name}' is not a readable SVG file (${error.message})", error)
        }
        val path = Path2D.Double()
        collect(document.documentElement, AffineTransform(), path)
        if (path.currentPoint == null) {
            throw ShapeException("'${file.name}' has no paths or basic shapes")
        }
        return path
    }

    // parses one path data string, for tests and inline use
    fun parsePath(data: String): Path2D.Double = Path2D.Double().also { PathData(data, it).parse() }

    // documents may declare a DTD, but nothing outside the file is ever loaded
    private fun factory(): DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isValidating = false
        isExpandEntityReferences = false
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    }

    private fun collect(element: Element, parent: AffineTransform, out: Path2D.Double) {
        val tag = element.tagName.substringAfter(':')
        if (tag in SKIPPED || element.getAttribute("display") == "none") {
            return
        }
        val transform = AffineTransform(parent)
        element.getAttribute("transform").takeIf(String::isNotBlank)?.let { transform.concatenate(parseTransform(it)) }
        val shape = when (tag) {
            "path" -> parsePath(element.getAttribute("d"))
            "rect" -> rect(element)
            "circle" -> {
                val r = element.length("r")
                Ellipse2D.Double(element.length("cx") - r, element.length("cy") - r, 2 * r, 2 * r).takeIf { r > 0.0 }
            }
            "ellipse" -> {
                val rx = element.length("rx")
                val ry = element.length("ry")
                Ellipse2D.Double(element.length("cx") - rx, element.length("cy") - ry, 2 * rx, 2 * ry).takeIf { rx > 0.0 && ry > 0.0 }
            }
            "line" -> Line2D.Double(element.length("x1"), element.length("y1"), element.length("x2"), element.length("y2"))
            "polyline", "polygon" -> polyline(element.getAttribute("points"), closed = tag == "polygon")
            else -> null
        }
        if (shape != null) {
            out.append(transform.createTransformedShape(shape), false)
        }
        val children = element.childNodes
        for (index in 0 until children.length) {
            (children.item(index) as? Element)?.let { collect(it, transform, out) }
        }
    }

    private fun rect(element: Element): java.awt.Shape? {
        val width = element.length("width")
        val height = element.length("height")
        if (width <= 0.0 || height <= 0.0) return null
        // a missing radius takes the other one
        val rx = element.optionalLength("rx") ?: element.optionalLength("ry") ?: 0.0
        val ry = element.optionalLength("ry") ?: rx
        val x = element.length("x")
        val y = element.length("y")
        if (rx <= 0.0 && ry <= 0.0) return Rectangle2D.Double(x, y, width, height)
        return RoundRectangle2D.Double(x, y, width, height, 2 * rx.coerceAtMost(width / 2), 2 * ry.coerceAtMost(height / 2))
    }

    private fun polyline(points: String, closed: Boolean): Path2D.Double? {
        val numbers = NUMBER.findAll(points).map { it.value.toDouble() }.toList()
        if (numbers.size < 4) return null
        val path = Path2D.Double()
        path.moveTo(numbers[0], numbers[1])
        for (index in 2 until numbers.size - 1 step 2) path.lineTo(numbers[index], numbers[index + 1])
        if (closed) path.closePath()
        return path
    }

    // "translate(10 20) rotate(45)": the transforms apply right to left, as matrices multiplied left to right
    fun parseTransform(text: String): AffineTransform {
        val result = AffineTransform()
        TRANSFORM.findAll(text).forEach { match ->
            val args = NUMBER.findAll(match.groupValues[2]).map { it.value.toDouble() }.toList()
            val arg = { index: Int, fallback: Double -> args.getOrElse(index) { fallback } }
            val step = when (match.groupValues[1]) {
                "matrix" -> if (args.size == 6) AffineTransform(args[0], args[1], args[2], args[3], args[4], args[5]) else null
                "translate" -> AffineTransform.getTranslateInstance(arg(0, 0.0), arg(1, 0.0))
                "scale" -> AffineTransform.getScaleInstance(arg(0, 1.0), arg(1, arg(0, 1.0)))
                "rotate" -> AffineTransform.getRotateInstance(Math.toRadians(arg(0, 0.0)), arg(1, 0.0), arg(2, 0.0))
                "skewX" -> AffineTransform.getShearInstance(tan(Math.toRadians(arg(0, 0.0))), 0.0)
                "skewY" -> AffineTransform.getShearInstance(0.0, tan(Math.toRadians(arg(0, 0.0))))
                else -> null
            }
            step?.let(result::concatenate)
        }
        return result
    }

    // a length attribute in user units; units and percentages are not resolved
    private fun Element.length(name: String): Double = optionalLength(name) ?: 0.0

    private fun Element.optionalLength(name: String): Double? =
        getAttribute(name).takeIf(String::isNotBlank)?.let { NUMBER.find(it)?.value?.toDoubleOrNull() }

    // reads the path mini-language: commands, with numbers that may run together ("1.5.5-2" is 1.5, .5, -2)
    private class PathData(private val data: String, private val path: Path2D.Double) {
        private var position = 0

        // current point, subpath start, last control point for S/T reflections
        private var x = 0.0
        private var y = 0.0
        private var startX = 0.0
        private var startY = 0.0
        private var controlX = 0.0
        private var controlY = 0.0
        private var previous = ' '

        fun parse() {
            var command = ' '
            while (true) {
                skipSeparators()
                if (position >= data.length) return
                val char = data[position]
                if (char.isLetter()) {
                    command = char
                    position++
                } else if (command == ' ') {
                    // numbers before the first command
                    return
                }
                if (!run(command)) return
                // after a moveto, extra coordinate pairs are linetos
                if (command == 'M') command = 'L'
                if (command == 'm') command = 'l'
            }
        }

        // runs one command with its arguments, false on malformed data
        private fun run(command: Char): Boolean {
            val relative = command.isLowerCase()
            val baseX = if (relative) x else 0.0
            val baseY = if (relative) y else 0.0
            when (command.uppercaseChar()) {
                'M' -> {
                    val (px, py) = pair() ?: return false
                    x = baseX + px; y = baseY + py
                    startX = x; startY = y
                    path.moveTo(x, y)
                }
                'L' -> {
                    val (px, py) = pair() ?: return false
                    lineTo(baseX + px, baseY + py)
                }
                'H' -> lineTo(baseX + (number() ?: return false), y)
                'V' -> lineTo(x, baseY + (number() ?: return false))
                'C' -> {
                    val (x1, y1) = pair() ?: return false
                    val (x2, y2) = pair() ?: return false
                    val (px, py) = pair() ?: return false
                    cubicTo(baseX + x1, baseY + y1, baseX + x2, baseY + y2, baseX + px, baseY + py)
                }
                'S' -> {
                    val (x2, y2) = pair() ?: return false
                    val (px, py) = pair() ?: return false
                    // the first control point mirrors the previous curve's second one
                    val (x1, y1) = if (previous in "CcSs") 2 * x - controlX to 2 * y - controlY else x to y
                    cubicTo(x1, y1, baseX + x2, baseY + y2, baseX + px, baseY + py)
                }
                'Q' -> {
                    val (x1, y1) = pair() ?: return false
                    val (px, py) = pair() ?: return false
                    quadTo(baseX + x1, baseY + y1, baseX + px, baseY + py)
                }
                'T' -> {
                    val (px, py) = pair() ?: return false
                    val (x1, y1) = if (previous in "QqTt") 2 * x - controlX to 2 * y - controlY else x to y
                    quadTo(x1, y1, baseX + px, baseY + py)
                }
                'A' -> {
                    val rx = number() ?: return false
                    val ry = number() ?: return false
                    val rotation = number() ?: return false
                    val large = flag() ?: return false
                    val sweep = flag() ?: return false
                    val (px, py) = pair() ?: return false
                    arcTo(rx, ry, rotation, large, sweep, baseX + px, baseY + py)
                }
                'Z' -> {
                    path.closePath()
                    x = startX; y = startY
                }
                else -> return false
            }
            previous = command
            return true
        }

        private fun ensureStarted() {
            if (path.currentPoint == null) path.moveTo(x, y)
        }

        private fun lineTo(px: Double, py: Double) {
            ensureStarted()
            path.lineTo(px, py)
            x = px; y = py
        }

        private fun cubicTo(x1: Double, y1: Double, x2: Double, y2: Double, px: Double, py: Double) {
            ensureStarted()
            path.curveTo(x1, y1, x2, y2, px, py)
            controlX = x2; controlY = y2
            x = px; y = py
        }

        private fun quadTo(x1: Double, y1: Double, px: Double, py: Double) {
            ensureStarted()
            path.quadTo(x1, y1, px, py)
            controlX = x1; controlY = y1
            x = px; y = py
        }

        // endpoint arc to center form, then cut into cubic Béziers of at most 90°
        // https://www.w3.org/TR/SVG11/implnote.html#ArcImplementationNotes
        private fun arcTo(radiusX: Double, radiusY: Double, rotationDegrees: Double, large: Boolean, sweep: Boolean, px: Double, py: Double) {
            var rx = abs(radiusX)
            var ry = abs(radiusY)
            if (rx == 0.0 || ry == 0.0 || (px == x && py == y)) {
                lineTo(px, py)
                return
            }
            val phi = Math.toRadians(rotationDegrees)
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)
            // (x1', y1'): the midpoint difference in the ellipse's frame
            val dx = (x - px) / 2
            val dy = (y - py) / 2
            val x1 = cosPhi * dx + sinPhi * dy
            val y1 = -sinPhi * dx + cosPhi * dy
            // radii too small to reach the endpoint grow by √Λ
            val lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
            if (lambda > 1.0) {
                rx *= sqrt(lambda)
                ry *= sqrt(lambda)
            }
            val numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
            val denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1
            val sign = if (large == sweep) -1.0 else 1.0
            val coefficient = sign * sqrt((numerator / denominator).coerceAtLeast(0.0))
            val cx1 = coefficient * rx * y1 / ry
            val cy1 = -coefficient * ry * x1 / rx
            val cx = cosPhi * cx1 - sinPhi * cy1 + (x + px) / 2
            val cy = sinPhi * cx1 + cosPhi * cy1 + (y + py) / 2
            val start = angle(1.0, 0.0, (x1 - cx1) / rx, (y1 - cy1) / ry)
            var delta = angle((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry)
            if (!sweep && delta > 0) delta -= 2 * PI
            if (sweep && delta < 0) delta += 2 * PI

            ensureStarted()
            val pieces = ceil(abs(delta) / (PI / 2)).toInt().coerceAtLeast(1)
            val step = delta / pieces
            // control arm length of a cubic arc of angle step on the unit circle
            val arm = 4.0 / 3.0 * tan(step / 4)
            var theta = start
            for (piece in 0 until pieces) {
                val cos1 = cos(theta); val sin1 = sin(theta)
                val cos2 = cos(theta + step); val sin2 = sin(theta + step)
                // unit-circle points and tangents, mapped by the ellipse: scale (rx, ry), rotate phi, move to the center
                fun mapX(ux: Double, uy: Double) = cx + cosPhi * rx * ux - sinPhi * ry * uy
                fun mapY(ux: Double, uy: Double) = cy + sinPhi * rx * ux + cosPhi * ry * uy
                path.curveTo(
                    mapX(cos1 - arm * sin1, sin1 + arm * cos1), mapY(cos1 - arm * sin1, sin1 + arm * cos1),
                    mapX(cos2 + arm * sin2, sin2 - arm * cos2), mapY(cos2 + arm * sin2, sin2 - arm * cos2),
                    mapX(cos2, sin2), mapY(cos2, sin2),
                )
                theta += step
            }
            x = px; y = py
        }

        // signed angle from vector u to vector v
        private fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val length = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
            val value = acos((dot / length).coerceIn(-1.0, 1.0))
            return if (ux * vy - uy * vx < 0) -value else value
        }

        private fun pair(): Pair<Double, Double>? {
            val first = number() ?: return null
            val second = number() ?: return null
            return first to second
        }

        private fun number(): Double? {
            skipSeparators()
            val match = NUMBER.matchAt(data, position) ?: return null
            position = match.range.last + 1
            return match.value.toDoubleOrNull()
        }

        // arc flags are single digits that need no separator: "a1 1 0 01 5 5"
        private fun flag(): Boolean? {
            skipSeparators()
            val char = data.getOrNull(position) ?: return null
            if (char != '0' && char != '1') return null
            position++
            return char == '1'
        }

        private fun skipSeparators() {
            while (position < data.length && (data[position].isWhitespace() || data[position] == ',')) position++
        }
    }

    private val NUMBER = Regex("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?")
    private val TRANSFORM = Regex("(matrix|translate|scale|rotate|skewX|skewY)\\s*\\(([^)]*)\\)")
    private val SKIPPED = setOf("defs", "clipPath", "mask", "symbol", "marker", "pattern", "style", "title", "desc", "metadata", "text")
}
