package com.utophii.util

// converts the small MiniMessage-like tag subset used in command feedback into §-coded legacy text
// deliberately avoids shading Adventure: Paper 26.x ships Adventure 5 while older servers ship 4.x,
// so a shaded copy would collide with the server-provided classes at the API boundary
object LegacyFormat {

    // formats [input] supporting: named colors with nesting (</color> restores the previous color),
    // <bold></bold>, <reset>, and two-stop <gradient:#RRGGBB:#RRGGBB>...</gradient>
    fun format(input: String): String {
        val out = StringBuilder(input.length + LEGACY_OVERHEAD_RESERVE)
        val colorStack = ArrayDeque<String>()
        var currentColor = ""
        var bold = false
        var gradientStart = 0
        var gradientEnd = 0
        var gradientTotal = 0
        var gradientIndex = 0
        var gradientActive = false

        // appends one visible character, applying per-character gradient color when active
        fun emitChar(c: Char) {
            if (!gradientActive) {
                out.append(c)
                return
            }
            val t = if (gradientTotal <= 1) 0.0 else gradientIndex.toDouble() / (gradientTotal - 1).toDouble()
            out.append(hexSection(lerpRgb(gradientStart, gradientEnd, t)))
            if (bold) {
                // a legacy color code wipes formatting, so bold must be re-applied per character
                out.append(SECTION).append(BOLD_CODE)
            }
            out.append(c)
            gradientIndex++
        }

        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c != '<') {
                emitChar(c)
                i++
                continue
            }
            val close = input.indexOf('>', i)
            if (close < 0) {
                emitChar(c)
                i++
                continue
            }
            val tag = input.substring(i + 1, close).trim()
            when {
                // named color open: push, close restores the previous color
                NAMED_COLORS.containsKey(tag) -> {
                    colorStack.addLast(currentColor)
                    currentColor = "$SECTION${NAMED_COLORS.getValue(tag)}"
                    out.append(currentColor)
                }
                tag == "bold" || tag == "b" -> {
                    bold = true
                    out.append(SECTION).append(BOLD_CODE)
                }
                tag == "/bold" || tag == "/b" -> {
                    bold = false
                    // legacy cannot unset bold alone: reset and restore the active color
                    out.append(SECTION).append(RESET_CODE).append(currentColor)
                }
                tag == "reset" || tag == "/reset" -> {
                    bold = false
                    currentColor = ""
                    colorStack.clear()
                    out.append(SECTION).append(RESET_CODE)
                }
                tag.startsWith("gradient:") -> {
                    val stops = HEX_STOP_REGEX.findAll(tag).map { it.groupValues[1].toInt(HEX_RADIX) }.toList()
                    if (stops.size >= 2) {
                        gradientStart = stops.first()
                        gradientEnd = stops.last()
                        val bodyEnd = input.indexOf(GRADIENT_CLOSE_TAG, close, ignoreCase = true)
                        gradientTotal = if (bodyEnd < 0) 1 else visibleLength(input, close + 1, bodyEnd)
                        gradientIndex = 0
                        gradientActive = true
                    }
                }
                tag.equals("/gradient", ignoreCase = true) -> {
                    gradientActive = false
                }
                tag.startsWith("/") -> {
                    // closing tag of a named color: restore the enclosing color
                    if (tag.drop(1) in NAMED_COLORS) {
                        currentColor = if (colorStack.isEmpty()) "" else colorStack.removeLast()
                        out.append(currentColor.ifEmpty { "$SECTION$RESET_CODE" })
                    }
                }
                // unknown tags are dropped silently: command output must never leak raw markup
            }
            i = close + 1
        }
        return out.toString()
    }

    // counts characters outside of any tag in [fromInclusive, toExclusive) of [text]
    private fun visibleLength(text: String, fromInclusive: Int, toExclusive: Int): Int {
        var length = 0
        var i = fromInclusive
        while (i < toExclusive) {
            if (text[i] == '<') {
                val close = text.indexOf('>', i)
                if (close < 0 || close >= toExclusive) {
                    length++
                    i++
                } else {
                    i = close + 1
                }
            } else {
                length++
                i++
            }
        }
        return length.coerceAtLeast(1)
    }

    // linearly interpolates between two packed RGB colors
    private fun lerpRgb(from: Int, to: Int, t: Double): Int {
        val r = (channel(from, RED_SHIFT) + (channel(to, RED_SHIFT) - channel(from, RED_SHIFT)) * t).toInt()
        val g = (channel(from, GREEN_SHIFT) + (channel(to, GREEN_SHIFT) - channel(from, GREEN_SHIFT)) * t).toInt()
        val b = (channel(from, BLUE_SHIFT) + (channel(to, BLUE_SHIFT) - channel(from, BLUE_SHIFT)) * t).toInt()
        return (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or (b shl BLUE_SHIFT)
    }

    private fun channel(rgb: Int, shift: Int): Int = (rgb shr shift) and CHANNEL_MASK

    // renders a packed RGB color as the legacy hex sequence §x§R§R§G§G§B§B
    private fun hexSection(rgb: Int): String {
        val hex = HEX_FORMAT.format(rgb)
        val builder = StringBuilder(HEX_SEQUENCE_LENGTH)
        builder.append(SECTION).append(HEX_MARKER)
        hex.forEach { digit -> builder.append(SECTION).append(digit) }
        return builder.toString()
    }

    private const val SECTION = '§'
    private const val BOLD_CODE = 'l'
    private const val RESET_CODE = 'r'
    private const val HEX_MARKER = 'x'
    private const val HEX_RADIX = 16
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BLUE_SHIFT = 0
    private const val CHANNEL_MASK = 0xFF
    private const val HEX_SEQUENCE_LENGTH = 14
    private const val LEGACY_OVERHEAD_RESERVE = 64
    private const val GRADIENT_CLOSE_TAG = "</gradient>"
    private val HEX_FORMAT = "%06X"
    private val HEX_STOP_REGEX = Regex("#([0-9A-Fa-f]{6})")

    private val NAMED_COLORS = mapOf(
        "black" to '0', "dark_blue" to '1', "dark_green" to '2', "dark_aqua" to '3',
        "dark_red" to '4', "dark_purple" to '5', "gold" to '6', "gray" to '7',
        "dark_gray" to '8', "blue" to '9', "green" to 'a', "aqua" to 'b',
        "red" to 'c', "light_purple" to 'd', "yellow" to 'e', "white" to 'f',
    )
}
