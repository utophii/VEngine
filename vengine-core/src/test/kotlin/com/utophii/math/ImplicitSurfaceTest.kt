package com.utophii.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class ImplicitSurfaceTest {

    private val sphere = ScalarField { x, y, z -> sqrt(x * x + y * y + z * z) - 1.0 }

    @Test
    fun `projects a point onto the unit sphere along its ray`() {
        val point = doubleArrayOf(0.3, -0.4, 1.2)
        val residual = ImplicitSurface.project(sphere, point, iterations = 50, maxStep = 1.0)
        assertTrue(residual < 1e-6)
        val length = sqrt(0.3 * 0.3 + 0.4 * 0.4 + 1.2 * 1.2)
        assertEquals(0.3 / length, point[0], 1e-6)
        assertEquals(1.2 / length, point[2], 1e-6)
    }

    @Test
    fun `max step limits how far one iteration moves`() {
        val point = doubleArrayOf(5.0, 0.0, 0.0)
        ImplicitSurface.project(sphere, point, iterations = 1, maxStep = 0.5)
        assertEquals(4.5, point[0], 1e-6)
    }

    @Test
    fun `planar projection keeps z`() {
        val point = doubleArrayOf(2.0, 0.0, 0.5)
        ImplicitSurface.project(sphere, point, iterations = 50, maxStep = 1.0, planar = true)
        assertEquals(0.5, point[2], 0.0)
        assertEquals(sqrt(1.0 - 0.25), point[0], 1e-6)
    }
}
