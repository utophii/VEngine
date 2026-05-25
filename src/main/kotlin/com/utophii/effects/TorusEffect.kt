package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.MathUtils
import org.bukkit.Location
import kotlin.math.PI

// parametric torus with optional gravity-like density near the bottom
class TorusEffect : AbstractParticleEffect("torus") {
    // calculates torus particle positions with variable phi density
    // uses x = (R + r cos(phi)) cos(theta), y = r sin(phi), z = (R + r cos(phi)) sin(theta)
    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> {
        val majorRadius = param(opts, "majorRadius", DEFAULT_MAJOR_RADIUS)
        val minorRadius = param(opts, "minorRadius", DEFAULT_MINOR_RADIUS)
        val thetaSteps = param(opts, "thetaSteps", DEFAULT_THETA_STEPS).toInt().coerceAtLeast(MIN_STEPS)
        val phiSteps = param(opts, "phiSteps", DEFAULT_PHI_STEPS).toInt().coerceAtLeast(MIN_STEPS)
        val gravityDensity = param(opts, "gravityDensity", DEFAULT_GRAVITY_DENSITY)
        val phase = time * param(opts, "angularSpeed", DEFAULT_ANGULAR_SPEED)
        val positions = mutableListOf<Location>()

        for (thetaIndex in 0 until thetaSteps) {
            // theta = i / thetaSteps * tau + phase: rotates around the central Y axis
            val theta = thetaIndex / thetaSteps.toDouble() * MathUtils.TAU + phase
            for (phiIndex in 0 until phiSteps) {
                // phi = j / phiSteps * tau: moves around the minor tube circle
                val phi = phiIndex / phiSteps.toDouble() * MathUtils.TAU
                // density = 1 + gravity * gaussian(phi, 1.5*pi): biases particles toward the lower tube arc
                val density = 1.0 + gravityDensity * MathUtils.gaussian(phi, BOTTOM_PHI, DENSITY_SIGMA)
                val repeats = density.toInt().coerceAtLeast(1)
                repeat(repeats) {
                    val local = MathUtils.torus(majorRadius, minorRadius, theta, phi)
                    positions += applyModifiers(MathUtils.transform(local, center, opts.scale, opts.rotationYaw, opts.tiltAxis, opts.tiltAngle), opts, time)
                }
            }
        }

        return positions
    }

    companion object {
        private const val DEFAULT_MAJOR_RADIUS = 1.4
        private const val DEFAULT_MINOR_RADIUS = 0.45
        private const val DEFAULT_THETA_STEPS = 48.0
        private const val DEFAULT_PHI_STEPS = 18.0
        private const val DEFAULT_GRAVITY_DENSITY = 1.0
        private const val DEFAULT_ANGULAR_SPEED = 0.05
        private const val MIN_STEPS = 3
        private const val DENSITY_SIGMA = 0.7
        private const val BOTTOM_PHI = PI * 1.5
    }
}
