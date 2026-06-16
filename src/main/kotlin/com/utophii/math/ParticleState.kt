package com.utophii.math

import org.bukkit.util.Vector

// immutable six-dimensional ODE particle state y = (p, v)
// y(t + h) = y(t) + h/6(k1 + 2k2 + 2k3 + k4)
data class ParticleState(
    val x: Double,
    val y: Double,
    val z: Double,
    val vx: Double,
    val vy: Double,
    val vz: Double,
) {
    // converts the position part `p = (x, y, z)` to a mutable Bukkit vector
    fun positionVector(): Vector {
        // p = (x, y, z): extracts the local position component from the ODE state
        return Vector(x, y, z)
    }

    // converts the velocity part `v = (vx, vy, vz)` to a mutable Bukkit vector
    fun velocityVector(): Vector {
        // v = (vx, vy, vz): extracts the local velocity component from the ODE state
        return Vector(vx, vy, vz)
    }

    companion object {
        // builds an immutable ODE particle state from mutable Bukkit vectors
        fun of(position: Vector, velocity: Vector): ParticleState {
            // y = (px, py, pz, vx, vy, vz): packs mutable vectors into immutable scalar state
            return ParticleState(position.x, position.y, position.z, velocity.x, velocity.y, velocity.z)
        }
    }
}
