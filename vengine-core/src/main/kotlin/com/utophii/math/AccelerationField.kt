package com.utophii.math

import org.bukkit.util.Vector

// time-dependent acceleration field for a second-order particle ODE
// RK4Integrator for the ODE system p' = v, v' = a(t, p, v)
fun interface AccelerationField {
    // evaluates acceleration a(t, p, v) for the supplied particle state
    // implementations must be side-effect free because VEngine evaluates them asynchronously
    fun acceleration(state: ParticleState, time: Double): Vector
}
