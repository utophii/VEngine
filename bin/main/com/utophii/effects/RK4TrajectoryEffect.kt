package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.AccelerationField
import com.utophii.math.ParticleForces
import com.utophii.math.ParticleState
import com.utophii.math.RK4Integrator
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.ceil

// physically-based trajectory rendered from an RK4-integrated particle ODE
class RK4TrajectoryEffect : AbstractParticleEffect("rk4_trajectory") {
    // calculates an animated trajectory prefix by solving p' = v, v' = g - c v + strength * curl(F)
    // uses RK4: y(t+h) = y(t) + h/6(k1 + 2k2 + 2k3 + k4)
    override fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val totalPoints = param(opts, STEPS_KEY, DEFAULT_STEPS).toInt().coerceIn(MIN_POINTS, MAX_POINTS)
        val timeStep = param(opts, TIME_STEP_KEY, DEFAULT_TIME_STEP).coerceAtLeast(MIN_TIME_STEP)
        val initialState = initialState(opts)
        val acceleration = accelerationField(opts)
        val duration = opts.duration.coerceAtLeast(MIN_DURATION_TICKS).toDouble()
        // progress = (time + 1) / duration: reveals more of the trajectory as the effect advances
        val progress = ((time + FRAME_PROGRESS_OFFSET) / duration).coerceIn(MIN_PROGRESS, MAX_PROGRESS)
        // visiblePoints = ceil(totalPoints * progress): converts normalized animation progress into rendered samples
        val visiblePoints = ceil(totalPoints * progress).toInt().coerceIn(MIN_POINTS, totalPoints)
        // integrationSteps = visiblePoints - 1: RK4 returns the initial state plus one state per integration step
        val integrationSteps = visiblePoints - INITIAL_STATE_COUNT
        val states = RK4Integrator.integrate(
            initialState = initialState,
            startTime = START_TIME,
            deltaTime = timeStep,
            steps = integrationSteps,
            acceleration = acceleration,
        )

        // states.size == visiblePoints: writes each integrated state directly with no intermediate Vector
        buffer.acquire(states.size)
        states.forEachIndexed { index, state ->
            writeParticle(buffer, index, state.x, state.y, state.z, center, opts, time)
        }
    }

    private fun initialState(opts: EffectOptions): ParticleState {
        val position = Vector(
            param(opts, INITIAL_X_KEY, DEFAULT_INITIAL_POSITION_X),
            param(opts, INITIAL_Y_KEY, DEFAULT_INITIAL_POSITION_Y),
            param(opts, INITIAL_Z_KEY, DEFAULT_INITIAL_POSITION_Z),
        )
        val velocity = Vector(
            param(opts, INITIAL_VELOCITY_X_KEY, DEFAULT_INITIAL_VELOCITY_X),
            param(opts, INITIAL_VELOCITY_Y_KEY, DEFAULT_INITIAL_VELOCITY_Y),
            param(opts, INITIAL_VELOCITY_Z_KEY, DEFAULT_INITIAL_VELOCITY_Z),
        )
        return ParticleState.of(position, velocity)
    }

    private fun accelerationField(opts: EffectOptions): AccelerationField {
        val gravity = Vector(
            param(opts, GRAVITY_X_KEY, DEFAULT_GRAVITY_X),
            param(opts, GRAVITY_Y_KEY, DEFAULT_GRAVITY_Y),
            param(opts, GRAVITY_Z_KEY, DEFAULT_GRAVITY_Z),
        )
        val drag = param(opts, DRAG_KEY, DEFAULT_DRAG).coerceAtLeast(MIN_DRAG)
        val curlStrength = param(opts, CURL_STRENGTH_KEY, DEFAULT_CURL_STRENGTH)
        val curlFrequency = param(opts, CURL_FREQUENCY_KEY, DEFAULT_CURL_FREQUENCY)
        val curlOctaves = param(opts, CURL_OCTAVES_KEY, DEFAULT_CURL_OCTAVES).toInt().coerceAtLeast(MIN_CURL_OCTAVES)
        val curlEpsilon = param(opts, CURL_EPSILON_KEY, DEFAULT_CURL_EPSILON).coerceAtLeast(MathUtils.MIN_CURL_EPSILON)
        val fields = mutableListOf(
            ParticleForces.gravity(gravity),
            ParticleForces.linearDrag(drag),
        )
        if (curlStrength != DISABLED_CURL_STRENGTH && curlOctaves > MIN_CURL_OCTAVES) {
            fields += ParticleForces.curlNoise(
                strength = curlStrength,
                frequency = curlFrequency,
                octaves = curlOctaves,
                epsilon = curlEpsilon,
            )
        }
        return ParticleForces.combine(*fields.toTypedArray())
    }

    companion object {
        private const val STEPS_KEY = "steps"
        private const val TIME_STEP_KEY = "timeStep"
        private const val INITIAL_X_KEY = "initialX"
        private const val INITIAL_Y_KEY = "initialY"
        private const val INITIAL_Z_KEY = "initialZ"
        private const val INITIAL_VELOCITY_X_KEY = "initialVelocityX"
        private const val INITIAL_VELOCITY_Y_KEY = "initialVelocityY"
        private const val INITIAL_VELOCITY_Z_KEY = "initialVelocityZ"
        private const val GRAVITY_X_KEY = "gravityX"
        private const val GRAVITY_Y_KEY = "gravityY"
        private const val GRAVITY_Z_KEY = "gravityZ"
        private const val DRAG_KEY = "drag"
        private const val CURL_STRENGTH_KEY = "curlStrength"
        private const val CURL_FREQUENCY_KEY = "curlFrequency"
        private const val CURL_OCTAVES_KEY = "curlOctaves"
        private const val CURL_EPSILON_KEY = "curlEpsilon"

        private const val DEFAULT_STEPS = 120.0
        private const val DEFAULT_TIME_STEP = 0.08
        private const val DEFAULT_INITIAL_POSITION_X = 0.0
        private const val DEFAULT_INITIAL_POSITION_Y = 0.0
        private const val DEFAULT_INITIAL_POSITION_Z = 0.0
        private const val DEFAULT_INITIAL_VELOCITY_X = 0.46
        private const val DEFAULT_INITIAL_VELOCITY_Y = 0.66
        private const val DEFAULT_INITIAL_VELOCITY_Z = 0.0
        private const val DEFAULT_GRAVITY_X = 0.0
        private const val DEFAULT_GRAVITY_Y = -0.20
        private const val DEFAULT_GRAVITY_Z = 0.0
        private const val DEFAULT_DRAG = 0.06
        private const val DEFAULT_CURL_STRENGTH = 0.18
        private const val DEFAULT_CURL_FREQUENCY = 0.85
        private const val DEFAULT_CURL_OCTAVES = 3.0
        private const val DEFAULT_CURL_EPSILON = 0.04

        private const val START_TIME = 0.0
        private const val FRAME_PROGRESS_OFFSET = 1.0
        private const val MIN_PROGRESS = 0.0
        private const val MAX_PROGRESS = 1.0
        private const val DISABLED_CURL_STRENGTH = 0.0
        private const val MIN_CURL_OCTAVES = 0
        private const val MIN_DRAG = 0.0
        private const val MIN_TIME_STEP = 1.0E-4
        private const val MIN_POINTS = 1
        private const val MAX_POINTS = 512
        private const val MIN_DURATION_TICKS = 1L
        private const val INITIAL_STATE_COUNT = 1
    }
}
