package com.utophii.math

// explicit integration of an arbitrary finite state vector
class OdeIntegrator(private val dimensions: Int, private val method: Method) {
    enum class Method { EULER, RK4 }

    private val k1 = DoubleArray(dimensions)
    private val k2 = DoubleArray(dimensions)
    private val k3 = DoubleArray(dimensions)
    private val k4 = DoubleArray(dimensions)
    private val stage = DoubleArray(dimensions)

    fun step(state: DoubleArray, time: Double, dt: Double, derivative: (DoubleArray, Double, DoubleArray) -> Unit) {
        derivative(state, time, k1)
        if (method == Method.EULER) {
            for (i in state.indices) state[i] += dt * k1[i]
            return
        }
        for (i in state.indices) stage[i] = state[i] + dt * 0.5 * k1[i]
        derivative(stage, time + dt * 0.5, k2)
        for (i in state.indices) stage[i] = state[i] + dt * 0.5 * k2[i]
        derivative(stage, time + dt * 0.5, k3)
        for (i in state.indices) stage[i] = state[i] + dt * k3[i]
        derivative(stage, time + dt, k4)
        for (i in state.indices) state[i] += dt / 6.0 * (k1[i] + 2.0 * k2[i] + 2.0 * k3[i] + k4[i])
    }
}
