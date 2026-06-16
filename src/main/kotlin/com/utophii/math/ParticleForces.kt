package com.utophii.math

import org.bukkit.util.Vector

// factory methods for physically-based acceleration fields used by the RK4 integrator
// RK4Integrator for integrating the composed field a_total = a_gravity + a_drag + a_curl
object ParticleForces {
    // creates a constant gravity acceleration field
    fun gravity(gravity: Vector): AccelerationField {
        val stableGravity = gravity.clone()
        return AccelerationField { _, _ ->
            // a_g = g: applies the same acceleration independent of position and velocity
            stableGravity.clone()
        }
    }

    // creates a linear velocity drag field
    fun linearDrag(coefficient: Double): AccelerationField {
        val stableCoefficient = coefficient.coerceAtLeast(MIN_DRAG_COEFFICIENT)
        return AccelerationField { state, _ ->
            // a_drag = -c * v: damps velocity proportionally to current speed
            MathUtils.linearDrag(state.velocityVector(), stableCoefficient)
        }
    }

    // creates a divergence-free curl-noise acceleration field
    fun curlNoise(
        strength: Double,
        frequency: Double,
        octaves: Int,
        epsilon: Double,
    ): AccelerationField {
        val stableFrequency = frequency.coerceAtLeast(MathUtils.MIN_CURL_FREQUENCY)
        val stableOctaves = octaves.coerceAtLeast(MIN_CURL_OCTAVES)
        val stableEpsilon = epsilon.coerceAtLeast(MathUtils.MIN_CURL_EPSILON)
        return AccelerationField { state, time ->
            // curl = ∇ × F: samples a divergence-free swirling acceleration from an fBm vector potential
            val curl = MathUtils.curlNoise(state.x, state.y, state.z, time, stableFrequency, stableOctaves, stableEpsilon)
            // a_curl = strength * curl: scales the curl field contribution
            curl.multiply(strength)
        }
    }

    // sums multiple acceleration fields into a single field
    fun combine(vararg fields: AccelerationField): AccelerationField {
        val stableFields = fields.toList()
        return AccelerationField { state, time ->
            stableFields.fold(Vector(0.0, 0.0, 0.0)) { total, field ->
                // a_total = a_total + a_i(t, y): accumulates gravity, drag, curl, and any custom fields
                total.add(field.acceleration(state, time))
            }
        }
    }

    private const val MIN_DRAG_COEFFICIENT = 0.0
    private const val MIN_CURL_OCTAVES = 0
}
