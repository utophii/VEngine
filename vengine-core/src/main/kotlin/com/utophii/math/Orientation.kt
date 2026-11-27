package com.utophii.math

import org.bukkit.util.Vector
import org.joml.Quaternionf
import kotlin.math.IEEErem
import kotlin.math.abs
import kotlin.math.sqrt

// rotations of display models in the local frame of their geometry.
// A model faces local +Z with its top along +Y, in a right-handed basis
object Orientation {
    // at or above this |N . Y| the top is taken from +Z instead of +Y
    const val POLE_THRESHOLD = 0.9999

    // tangents whose cross product is at most this fraction of |Tu| |Tv| are collinear
    const val COLLINEAR_TOLERANCE = 1e-8

    // turns +Z onto the normal with the top being +Y projected onto the normal plane.
    // Returns false and leaves out unchanged for a zero or non-finite normal
    fun facing(nx: Double, ny: Double, nz: Double, out: Quaternionf): Boolean {
        val length = sqrt(nx * nx + ny * ny + nz * nz)
        if (!length.isFinite() || length == 0.0) {
            return false
        }
        val fx = nx / length
        val fy = ny / length
        val fz = nz / length
        // reference top: +Y, or +Z near the poles where +Y has no usable projection
        val pole = abs(fy) >= POLE_THRESHOLD
        val refY = if (pole) 0.0 else 1.0
        val refZ = if (pole) 1.0 else 0.0
        // U = normalize(ref - (ref . F) F)
        val dot = refY * fy + refZ * fz
        var ux = -dot * fx
        var uy = refY - dot * fy
        var uz = refZ - dot * fz
        val upLength = sqrt(ux * ux + uy * uy + uz * uz)
        ux /= upLength
        uy /= upLength
        uz /= upLength
        // R = U x F completes the right-handed basis (R, U, F)
        val rx = uy * fz - uz * fy
        val ry = uz * fx - ux * fz
        val rz = ux * fy - uy * fx
        fromBasis(rx, ry, rz, ux, uy, uz, fx, fy, fz, out)
        return true
    }

    // rotateXYZ composition of display.rotation. Returns false and leaves out unchanged for non-finite angles
    fun angles(x: Double, y: Double, z: Double, out: Quaternionf): Boolean {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
            return false
        }
        out.rotationXYZ(wrap(x), wrap(y), wrap(z))
        return true
    }

    // rotation part of the transform that places points: tilt * Ry(-yaw) * Rx(pitch) * Rz(roll) * Ry(-spin).
    // The engine's positive yaw and spin turn x toward z, which is a negative rotation about +Y.
    // Scale, including its sign, is left out
    fun geometry(
        yaw: Double,
        pitch: Double,
        roll: Double,
        tiltAxis: Vector?,
        tiltAngle: Double,
        out: Quaternionf,
        spin: Double = 0.0,
    ): Quaternionf {
        out.identity()
        if (tiltAxis != null && tiltAngle != 0.0 && tiltAxis.lengthSquared() > 0.0) {
            val length = tiltAxis.length()
            out.rotationAxis(wrap(tiltAngle), (tiltAxis.x / length).toFloat(), (tiltAxis.y / length).toFloat(),
                (tiltAxis.z / length).toFloat())
        }
        return out.rotateY(-wrap(yaw)).rotateX(wrap(pitch)).rotateZ(wrap(roll)).rotateY(-wrap(spin))
    }

    // float angles lose precision far from zero, accumulated time can make them large
    private fun wrap(angle: Double): Float = angle.IEEErem(MathUtils.TAU).toFloat()

    // quaternion of the rotation matrix with columns (a, b, c), Shepperd's method
    private fun fromBasis(
        ax: Double, ay: Double, az: Double,
        bx: Double, by: Double, bz: Double,
        cx: Double, cy: Double, cz: Double,
        out: Quaternionf,
    ) {
        val trace = ax + by + cz
        when {
            trace > 0.0 -> {
                val s = sqrt(trace + 1.0) * 2.0
                out.set(((bz - cy) / s).toFloat(), ((cx - az) / s).toFloat(), ((ay - bx) / s).toFloat(), (0.25 * s).toFloat())
            }
            ax > by && ax > cz -> {
                val s = sqrt(1.0 + ax - by - cz) * 2.0
                out.set((0.25 * s).toFloat(), ((bx + ay) / s).toFloat(), ((cx + az) / s).toFloat(), ((bz - cy) / s).toFloat())
            }
            by > cz -> {
                val s = sqrt(1.0 + by - ax - cz) * 2.0
                out.set(((bx + ay) / s).toFloat(), (0.25 * s).toFloat(), ((cy + bz) / s).toFloat(), ((cx - az) / s).toFloat())
            }
            else -> {
                val s = sqrt(1.0 + cz - ax - by) * 2.0
                out.set(((cx + az) / s).toFloat(), ((cy + bz) / s).toFloat(), (0.25 * s).toFloat(), ((ay - bx) / s).toFloat())
            }
        }
        out.normalize()
    }
}