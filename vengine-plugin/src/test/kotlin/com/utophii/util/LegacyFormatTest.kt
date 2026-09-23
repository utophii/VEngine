package com.utophii.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// verifies the lightweight legacy formatter that replaced the shaded Adventure/MiniMessage dependency
class LegacyFormatTest {

    @Test
    fun `plain text passes through unchanged`() {
        assertEquals("hello world", LegacyFormat.format("hello world"))
    }

    @Test
    fun `named colors become legacy codes and closing resets`() {
        assertEquals("§chi§r", LegacyFormat.format("<red>hi</red>"))
    }

    @Test
    fun `nested colors restore the enclosing color`() {
        assertEquals("§cA§6B§cC§r", LegacyFormat.format("<red>A<gold>B</gold>C</red>"))
    }

    @Test
    fun `bold emits the formatting code and reset`() {
        assertEquals("§lx§ry", LegacyFormat.format("<bold>x</bold>y"))
    }

    @Test
    fun `gradient colors each character between the two stops`() {
        val result = LegacyFormat.format("<gradient:#FF0000:#0000FF>AB</gradient>")

        // two visible characters, each preceded by a §x hex sequence
        assertEquals(hex('F', 'F', '0', '0', '0', '0') + "A" + hex('0', '0', '0', '0', 'F', 'F') + "B", result)
    }

    @Test
    fun `gradient with bold re-applies bold per character`() {
        val result = LegacyFormat.format("<gradient:#FF0000:#0000FF><bold>X</bold></gradient>")
        assertTrue("§lX" in result)
    }

    @Test
    fun `unknown tags are dropped without leaking markup`() {
        assertEquals("hi!", LegacyFormat.format("<rainbow:3>hi!"))
    }

    private fun hex(r1: Char, r2: Char, g1: Char, g2: Char, b1: Char, b2: Char): String =
        "§x§$r1§$r2§$g1§$g2§$b1§$b2"
}
