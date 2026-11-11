package com.utophii.effects

import com.utophii.math.OdeIntegrator
import org.bukkit.util.Vector

// integrates up to a contact, changes velocity there and integrates the unused part of the step.
// Straight ray segments approximate the curve, substeps refine that approximation
internal class OdeBlockStepper(
    private val options: OdeCollision,
    private val transform: OdeCollisionFrame,
    private val indices: List<Int>,
    private val integrator: OdeIntegrator,
    private val derivative: (DoubleArray, Double, DoubleArray) -> Unit,
) {
    var terminated = false
        private set
    private var stopped = false

    fun step(state: DoubleArray, time: Double, dt: Double): Double {
        if (stopped || terminated) return dt
        val next = state.copyOf()
        val interval = dt / options.substeps
        var elapsed = 0.0
        var bounces = 0
        repeat(options.substeps) {
            var remaining = interval
            while (remaining > 0.0) {
                state.copyInto(next)
                integrator.step(next, time + elapsed, remaining, derivative)
                if (next.any { !it.isFinite() }) {
                    next.copyInto(state)
                    return dt
                }
                val hit = transform.trace(read(state, 0), read(next, 0))
                if (hit == null) {
                    next.copyInto(state)
                    elapsed += remaining
                    break
                }
                val consumed = remaining * hit.fraction
                if (consumed > 0.0) integrator.step(state, time + elapsed, consumed, derivative)
                elapsed += consumed
                remaining -= consumed
                write(state, 0, transform.localPosition(hit.position))
                when (options.response) {
                    OdeCollision.Response.TERMINATE -> {
                        terminated = true
                        return elapsed
                    }
                    OdeCollision.Response.STOP -> {
                        stop(state)
                        return dt
                    }
                    OdeCollision.Response.BOUNCE -> {
                        val velocity = transform.direction(read(state, 3))
                        val inward = velocity.dot(hit.normal)
                        if (inward < 0.0) velocity.subtract(hit.normal.clone().multiply((1.0 + options.restitution) * inward))
                        write(state, 3, transform.localDirection(velocity))
                        // keep the next trace outside the surface to avoid re-hitting it at zero distance
                        write(state, 0, transform.localPosition(hit.position.clone().add(hit.normal.clone().multiply(1.0e-6))))
                        bounces++
                        if (bounces >= options.maxBounces) {
                            stop(state)
                            return dt
                        }
                    }
                    OdeCollision.Response.NONE -> error("Disabled collisions must use the ordinary integrator")
                }
            }
        }
        return dt
    }

    private fun stop(state: DoubleArray) {
        write(state, 3, Vector())
        stopped = true
    }

    private fun read(state: DoubleArray, offset: Int): Vector =
        Vector(state[indices[offset]], state[indices[offset + 1]], state[indices[offset + 2]])

    private fun write(state: DoubleArray, offset: Int, value: Vector) {
        state[indices[offset]] = value.x
        state[indices[offset + 1]] = value.y
        state[indices[offset + 2]] = value.z
    }
}