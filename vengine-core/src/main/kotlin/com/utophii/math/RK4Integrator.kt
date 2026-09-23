package com.utophii.math

// fourth-order Runge-Kutta integrator for physically-based particle trajectories
// https://en.wikipedia.org/wiki/Runge%E2%80%93Kutta_methods Runge-Kutta methods
object RK4Integrator {
    // advances one second-order ODE state by one RK4 step
    // formula: y(t + h) = y(t) + h/6 * (k1 + 2k2 + 2k3 + k4) with p' = v and v' = a(t, p, v)
    fun step(
        state: ParticleState,
        time: Double,
        deltaTime: Double,
        acceleration: AccelerationField,
    ): ParticleState {
        require(deltaTime.isFinite()) { "deltaTime must be finite" }
        if (deltaTime == 0.0) {
            return state
        }

        // h = deltaTime: names the integration interval used by the RK4 weighted average
        val h = deltaTime
        // h/2: half-step interval used for the midpoint derivative estimates k2 and k3
        val halfH = h * HALF
        // k1 = f(t, y): derivative at the beginning of the interval
        val k1 = derivative(state, time, acceleration)
        // k2 = f(t + h/2, y + h*k1/2): derivative at the first midpoint estimate
        val k2 = derivative(advance(state, k1, halfH), time + halfH, acceleration)
        // k3 = f(t + h/2, y + h*k2/2): derivative at the second midpoint estimate
        val k3 = derivative(advance(state, k2, halfH), time + halfH, acceleration)
        // k4 = f(t + h, y + h*k3): derivative at the end of the interval
        val k4 = derivative(advance(state, k3, h), time + h, acceleration)

        // x_next = x + h/6 * (k1.x + 2*k2.x + 2*k3.x + k4.x): RK4 position X update
        val nextX = state.x + SIXTH * h * (k1.dx + DOUBLE_WEIGHT * k2.dx + DOUBLE_WEIGHT * k3.dx + k4.dx)
        // y_next = y + h/6 * (k1.y + 2*k2.y + 2*k3.y + k4.y): RK4 position Y update
        val nextY = state.y + SIXTH * h * (k1.dy + DOUBLE_WEIGHT * k2.dy + DOUBLE_WEIGHT * k3.dy + k4.dy)
        // z_next = z + h/6 * (k1.z + 2*k2.z + 2*k3.z + k4.z): RK4 position Z update
        val nextZ = state.z + SIXTH * h * (k1.dz + DOUBLE_WEIGHT * k2.dz + DOUBLE_WEIGHT * k3.dz + k4.dz)
        // vx_next = vx + h/6 * (k1.vx + 2*k2.vx + 2*k3.vx + k4.vx): RK4 velocity X update
        val nextVx = state.vx + SIXTH * h * (k1.dvx + DOUBLE_WEIGHT * k2.dvx + DOUBLE_WEIGHT * k3.dvx + k4.dvx)
        // vy_next = vy + h/6 * (k1.vy + 2*k2.vy + 2*k3.vy + k4.vy): RK4 velocity Y update
        val nextVy = state.vy + SIXTH * h * (k1.dvy + DOUBLE_WEIGHT * k2.dvy + DOUBLE_WEIGHT * k3.dvy + k4.dvy)
        // vz_next = vz + h/6 * (k1.vz + 2*k2.vz + 2*k3.vz + k4.vz): RK4 velocity Z update
        val nextVz = state.vz + SIXTH * h * (k1.dvz + DOUBLE_WEIGHT * k2.dvz + DOUBLE_WEIGHT * k3.dvz + k4.dvz)
        return ParticleState(nextX, nextY, nextZ, nextVx, nextVy, nextVz)
    }

    // integrates a complete trajectory and returns every state, including the initial state
    // repeatedly applies y_n+1 = RK4(y_n, t_n, h) and t_n+1 = t_n + h
    fun integrate(
        initialState: ParticleState,
        startTime: Double,
        deltaTime: Double,
        steps: Int,
        acceleration: AccelerationField,
    ): List<ParticleState> {
        val clampedSteps = steps.coerceAtLeast(MIN_STEPS)
        val states = ArrayList<ParticleState>(clampedSteps + INITIAL_STATE_COUNT)
        var state = initialState
        var time = startTime
        states += state
        repeat(clampedSteps) {
            // y_n+1 = RK4(y_n, t_n, h): advances the trajectory by one fourth-order step
            state = step(state, time, deltaTime, acceleration)
            // t_n+1 = t_n + h: advances simulation time alongside the state
            time += deltaTime
            states += state
        }
        return states
    }

    private fun derivative(
        state: ParticleState,
        time: Double,
        acceleration: AccelerationField,
    ): StateDerivative {
        val a = acceleration.acceleration(state, time)
        // p' = v: the derivative of position is velocity
        val dx = state.vx
        // p' = v: the derivative of position is velocity
        val dy = state.vy
        // p' = v: the derivative of position is velocity
        val dz = state.vz
        // v' = a(t, p, v): the derivative of velocity is acceleration on X
        val dvx = a.x
        // v' = a(t, p, v): the derivative of velocity is acceleration on Y
        val dvy = a.y
        // v' = a(t, p, v): the derivative of velocity is acceleration on Z
        val dvz = a.z
        return StateDerivative(dx, dy, dz, dvx, dvy, dvz)
    }

    private fun advance(state: ParticleState, derivative: StateDerivative, scale: Double): ParticleState {
        // x_estimate = x + derivative.x * scale: estimates intermediate RK4 position X
        val x = state.x + derivative.dx * scale
        // y_estimate = y + derivative.y * scale: estimates intermediate RK4 position Y
        val y = state.y + derivative.dy * scale
        // z_estimate = z + derivative.z * scale: estimates intermediate RK4 position Z
        val z = state.z + derivative.dz * scale
        // vx_estimate = vx + derivative.vx * scale: estimates intermediate RK4 velocity X
        val vx = state.vx + derivative.dvx * scale
        // vy_estimate = vy + derivative.vy * scale: estimates intermediate RK4 velocity Y
        val vy = state.vy + derivative.dvy * scale
        // vz_estimate = vz + derivative.vz * scale: estimates intermediate RK4 velocity Z
        val vz = state.vz + derivative.dvz * scale
        return ParticleState(x, y, z, vx, vy, vz)
    }

    private data class StateDerivative(
        val dx: Double,
        val dy: Double,
        val dz: Double,
        val dvx: Double,
        val dvy: Double,
        val dvz: Double,
    )

    private const val HALF = 0.5
    private const val SIXTH = 1.0 / 6.0
    private const val DOUBLE_WEIGHT = 2.0
    private const val MIN_STEPS = 0
    private const val INITIAL_STATE_COUNT = 1
}
