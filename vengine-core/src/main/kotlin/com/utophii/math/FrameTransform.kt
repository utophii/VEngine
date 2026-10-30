package com.utophii.math

import org.bukkit.Location
import org.bukkit.util.Vector

// scale, euler rotation and tilt of one frame folded into a 3x3 matrix.
// The matrix is rebuilt only when an input changes; one instance belongs to one frame buffer
class FrameTransform {
    private var m00 = 1.0
    private var m01 = 0.0
    private var m02 = 0.0
    private var m10 = 0.0
    private var m11 = 1.0
    private var m12 = 0.0
    private var m20 = 0.0
    private var m21 = 0.0
    private var m22 = 1.0

    // inputs the matrix was built from, NaN never equals anything
    private var scale = Double.NaN
    private var yaw = Double.NaN
    private var pitch = Double.NaN
    private var roll = Double.NaN
    private var tiltAngle = Double.NaN
    private var tiltX = Double.NaN
    private var tiltY = Double.NaN
    private var tiltZ = Double.NaN
    private var tilted = false

    private val basis = Location(null, 0.0, 0.0, 0.0)

    // makes the matrix match these inputs, rebuilding it only when one of them changed
    fun prepare(scale: Double, rotationYaw: Double, tiltAxis: Vector?, tiltAngle: Double, rotationPitch: Double = 0.0, rotationRoll: Double = 0.0) {
        if (scale == this.scale && rotationYaw == yaw && rotationPitch == pitch && rotationRoll == roll &&
            tiltAngle == this.tiltAngle && sameAxis(tiltAxis)
        ) {
            return
        }
        this.scale = scale
        yaw = rotationYaw
        pitch = rotationPitch
        roll = rotationRoll
        this.tiltAngle = tiltAngle
        tilted = tiltAxis != null
        tiltX = tiltAxis?.x ?: 0.0
        tiltY = tiltAxis?.y ?: 0.0
        tiltZ = tiltAxis?.z ?: 0.0

        // each column is a unit axis sent through the reference transform
        column(1.0, 0.0, 0.0, tiltAxis)
        m00 = basis.x; m10 = basis.y; m20 = basis.z
        column(0.0, 1.0, 0.0, tiltAxis)
        m01 = basis.x; m11 = basis.y; m21 = basis.z
        column(0.0, 0.0, 1.0, tiltAxis)
        m02 = basis.x; m12 = basis.y; m22 = basis.z
    }

    // p = center + M * local
    fun applyInto(output: Location, x: Double, y: Double, z: Double, center: Location) {
        output.x = center.x + m00 * x + m01 * y + m02 * z
        output.y = center.y + m10 * x + m11 * y + m12 * z
        output.z = center.z + m20 * x + m21 * y + m22 * z
    }

    private fun column(x: Double, y: Double, z: Double, tiltAxis: Vector?) {
        MathUtils.transformInto(basis, x, y, z, ORIGIN, scale, yaw, tiltAxis, tiltAngle, pitch, roll)
    }

    private fun sameAxis(axis: Vector?): Boolean {
        if (axis == null) {
            return !tilted
        }
        return tilted && axis.x == tiltX && axis.y == tiltY && axis.z == tiltZ
    }

    private companion object {
        val ORIGIN = Location(null, 0.0, 0.0, 0.0)
    }
}
