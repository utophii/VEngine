package com.utophii.shapes

import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.Path2D
import java.io.File

// glyph outlines of a text, y down like any AWT shape. Lines are split on "\n" and centered on each other
class TextShape(private val font: Font) {

    fun outline(text: String): Path2D.Double {
        val path = Path2D.Double(Path2D.WIND_NON_ZERO)
        val context = FontRenderContext(null, true, true)
        val lines = text.split('\n')
        val lineHeight = font.getLineMetrics(text, context).height.toDouble()
        val widths = lines.map { line -> font.getStringBounds(line, context).width }
        val widest = widths.maxOrNull() ?: 0.0
        lines.forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            val glyphs = font.createGlyphVector(context, line)
            val x = (widest - widths[index]) / 2
            path.append(glyphs.getOutline(x.toFloat(), (index * lineHeight).toFloat()), false)
        }
        return path
    }

    companion object {
        // a font file next to the shapes (.ttf / .otf), else an installed or logical font family ("Serif", "Monospaced")
        fun font(spec: String?, style: String?, directory: File): Font {
            val awtStyle = when (style?.lowercase()) {
                "bold" -> Font.BOLD
                "italic" -> Font.ITALIC
                "bold_italic", "bolditalic" -> Font.BOLD or Font.ITALIC
                else -> Font.PLAIN
            }
            val name = spec?.takeIf(String::isNotBlank) ?: DEFAULT_FAMILY
            val base = if (name.endsWith(".ttf", ignoreCase = true) || name.endsWith(".otf", ignoreCase = true)) {
                val file = ShapeFiles.resolve(directory, name)
                runCatching { Font.createFont(Font.TRUETYPE_FONT, file) }.getOrElse { error ->
                    throw ShapeException("'$name' is not a readable font file (${error.message})", error)
                }
            } else {
                Font(name, Font.PLAIN, 1)
            }
            // glyphs are built at a large size so the curve flattening is fine, the fit scales them down later
            return base.deriveFont(awtStyle, GLYPH_SIZE)
        }

        private const val DEFAULT_FAMILY = Font.SANS_SERIF
        private const val GLYPH_SIZE = 100f
    }
}
