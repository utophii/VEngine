package com.utophii.math

import kotlin.math.abs
import kotlin.math.sqrt

// scalar field f(x, y, z) whose zero set is the shape
fun interface ScalarField {
    fun value(x: Double, y: Double, z: Double): Double
}

// pulls points onto the zero set of a scalar field.
// Sampling a grid and keeping the cells where f changes sign costs n³ evaluations for a surface,
// projecting n seed points with Newton steps costs a few evaluations each
object ImplicitSurface {

    // projects point (x, y, z) in place with p -= f(p) ∇f(p) / |∇f(p)|², the gradient taken by central differences.
    // planar = true keeps z fixed for 2D fields. Steps longer than maxStep are shortened, so a point near a
    // flat spot of f does not jump across the shape. Returns |f| at the final position
    fun project(
        field: ScalarField,
        point: DoubleArray,
        iterations: Int,
        maxStep: Double,
        planar: Boolean = false,
        epsilon: Double = DEFAULT_EPSILON,
        tolerance: Double = DEFAULT_TOLERANCE,
    ): Double {
        var x = point[0]
        var y = point[1]
        var z = point[2]
        var f = field.value(x, y, z)
        for (iteration in 0 until iterations) {
            if (abs(f) <= tolerance) {
                break
            }
            // ∂f/∂a ≈ (f(a + ε) - f(a - ε)) / 2ε
            val gx = (field.value(x + epsilon, y, z) - field.value(x - epsilon, y, z)) / (2.0 * epsilon)
            val gy = (field.value(x, y + epsilon, z) - field.value(x, y - epsilon, z)) / (2.0 * epsilon)
            val gz = if (planar) 0.0 else (field.value(x, y, z + epsilon) - field.value(x, y, z - epsilon)) / (2.0 * epsilon)
            val gradientSquared = gx * gx + gy * gy + gz * gz
            if (gradientSquared < MIN_GRADIENT_SQUARED) {
                break
            }
            // Newton step towards f = 0 along the gradient, clamped to maxStep
            var scale = f / gradientSquared
            val length = abs(scale) * sqrt(gradientSquared)
            if (length > maxStep) {
                scale *= maxStep / length
            }
            x -= scale * gx
            y -= scale * gy
            z -= scale * gz
            f = field.value(x, y, z)
        }
        point[0] = x
        point[1] = y
        point[2] = z
        return abs(f)
    }

    private const val DEFAULT_EPSILON = 1e-4
    private const val DEFAULT_TOLERANCE = 1e-6
    private const val MIN_GRADIENT_SQUARED = 1e-18
}
