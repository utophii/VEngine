package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.AdvancedMathUtils
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// parametric 3D effect of Gielis supershapes
// https://en.wikipedia.org/wiki/Superformula
// r1(theta) = superformula(theta, m1, a1, b1, n11, n12, n13)
// r2(phi) = superformula(phi, m2, a2, b2, n21, n22, n23)
// x = radius * r1(theta) * cos(theta) * r2(phi) * cos(phi)
// y = radius * r2(phi) * sin(phi)
// z = radius * r1(theta) * sin(theta) * r2(phi) * cos(phi)
class SupershapeEffect : AbstractParticleEffect("supershape") {

    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> {
        val radius = param(opts, PARAM_RADIUS, DEFAULT_RADIUS)
        val m1 = param(opts, PARAM_M1, DEFAULT_M1)
        val a1 = param(opts, PARAM_A1, DEFAULT_A1)
        val b1 = param(opts, PARAM_B1, DEFAULT_B1)
        val n11 = param(opts, PARAM_N11, DEFAULT_N11)
        val n12 = param(opts, PARAM_N12, DEFAULT_N12)
        val n13 = param(opts, PARAM_N13, DEFAULT_N13)

        val m2 = param(opts, PARAM_M2, DEFAULT_M2)
        val a2 = param(opts, PARAM_A2, DEFAULT_A2)
        val b2 = param(opts, PARAM_B2, DEFAULT_B2)
        val n21 = param(opts, PARAM_N21, DEFAULT_N21)
        val n22 = param(opts, PARAM_N22, DEFAULT_N22)
        val n23 = param(opts, PARAM_N23, DEFAULT_N23)

        val thetaSteps = param(opts, PARAM_THETA_STEPS, DEFAULT_THETA_STEPS).toInt().coerceAtLeast(MIN_STEPS)
        val phiSteps = param(opts, PARAM_PHI_STEPS, DEFAULT_PHI_STEPS).toInt().coerceAtLeast(MIN_STEPS)
        val phase = time * param(opts, PARAM_ANGULAR_SPEED, DEFAULT_ANGULAR_SPEED)

        val positions = ArrayList<Location>(thetaSteps * phiSteps)

        for (i in 0 until thetaSteps) {
            // theta = -pi + (i / thetaSteps) * 2pi: longitudinal angle of circumvallation [-pi, pi]
            val theta = -PI + (i.toDouble() / thetaSteps.toDouble()) * MathUtils.TAU + phase
            // r1 = superformula(theta): longitudinal super-radius
            val r1 = AdvancedMathUtils.superformula(theta, m1, a1, b1, n11, n12, n13)

            for (j in 0 until phiSteps) {
                // phi = -pi/2 + (j / (phiSteps - 1)) * pi: latitudinal angle of bypass [-pi/2, pi/2]
                val phi = -HALF_PI + (j.toDouble() / (phiSteps - 1.0)) * PI
                // r2 = superformula(phi): м
                val r2 = AdvancedMathUtils.superformula(phi, m2, a2, b2, n21, n22, n23)

                // x = r * r1 * cos(theta) * r2 * cos(phi): horizontal projection X
                val x = radius * r1 * cos(theta) * r2 * cos(phi)
                // y = r * r2 * sin(phi): vertical coordinate Y
                val y = radius * r2 * sin(phi)
                // z = r * r1 * sin(theta) * r2 * cos(phi): Z depth projection
                val z = radius * r1 * sin(theta) * r2 * cos(phi)

                val local = Vector(x, y, z)
                positions += applyModifiers(
                    MathUtils.transform(local, center, opts.scale, opts.rotationYaw, opts.tiltAxis, opts.tiltAngle),
                    opts,
                    time,
                    center
                )
            }
        }

        return positions
    }

    companion object {
        private const val PARAM_RADIUS = "radius"
        private const val PARAM_M1 = "m1"
        private const val PARAM_A1 = "a1"
        private const val PARAM_B1 = "b1"
        private const val PARAM_N11 = "n11"
        private const val PARAM_N12 = "n12"
        private const val PARAM_N13 = "n13"
        private const val PARAM_M2 = "m2"
        private const val PARAM_A2 = "a2"
        private const val PARAM_B2 = "b2"
        private const val PARAM_N21 = "n21"
        private const val PARAM_N22 = "n22"
        private const val PARAM_N23 = "n23"
        private const val PARAM_THETA_STEPS = "thetaSteps"
        private const val PARAM_PHI_STEPS = "phiSteps"
        private const val PARAM_ANGULAR_SPEED = "angularSpeed"

        // Значения по умолчанию для 5-конечной 3D супер-звезды
        private const val DEFAULT_RADIUS = 1.6
        private const val DEFAULT_M1 = 5.0
        private const val DEFAULT_A1 = 1.0
        private const val DEFAULT_B1 = 1.0
        private const val DEFAULT_N11 = 1.0
        private const val DEFAULT_N12 = 1.0
        private const val DEFAULT_N13 = 1.0
        private const val DEFAULT_M2 = 5.0
        private const val DEFAULT_A2 = 1.0
        private const val DEFAULT_B2 = 1.0
        private const val DEFAULT_N21 = 1.0
        private const val DEFAULT_N22 = 1.0
        private const val DEFAULT_N23 = 1.0
        private const val DEFAULT_THETA_STEPS = 40.0
        private const val DEFAULT_PHI_STEPS = 20.0
        private const val DEFAULT_ANGULAR_SPEED = 0.04
        private const val HALF_PI = PI * 0.5
        private const val MIN_STEPS = 4
    }
}