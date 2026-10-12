package com.utophii.math

import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// VEngine's Advanced Mathematical Toolkit
object AdvancedMathUtils {

    const val EASE_IN_OUT_CUBIC_THRESHOLD = 0.5
    const val EASE_IN_OUT_CUBIC_COEFFICIENT = 4.0
    const val EASE_IN_OUT_CUBIC_OFFSET = 2.0
    const val EASE_IN_OUT_CUBIC_SCALER = -2.0
    const val ELASTIC_PERIOD_DIVISOR = 3.0
    const val ELASTIC_PERIOD_FACTOR = (PI * 2.0) / ELASTIC_PERIOD_DIVISOR
    const val ELASTIC_EXPONENT_SCALE = 10.0
    const val ELASTIC_PHASE_OFFSET = 0.75
    const val BOUNCE_N1 = 7.5625
    const val BOUNCE_D1 = 2.75
    const val BOUNCE_T1 = 1.0 / BOUNCE_D1
    const val BOUNCE_T2 = 2.0 / BOUNCE_D1
    const val BOUNCE_T3 = 2.5 / BOUNCE_D1
    const val BOUNCE_OFFSET1 = 1.5 / BOUNCE_D1
    const val BOUNCE_OFFSET2 = 2.25 / BOUNCE_D1
    const val BOUNCE_OFFSET3 = 2.625 / BOUNCE_D1
    const val BOUNCE_SCALER2 = 0.75
    const val BOUNCE_SCALER3 = 0.9375
    const val BOUNCE_SCALER4 = 0.984375
    const val HALF = 0.5
    const val DOUBLE_SCALE = 2.0

    // calculates the toroidal node point T(p, q)
    // https://en.wikipedia.org/wiki/Torus_knot
    // r_tube = R + r * cos(q * t)
    // x = r_tube * cos(p * t)
    // y = -r * sin(q * t)
    // z = r_tube * sin(p * t)
    fun torusKnot(majorRadius: Double, minorRadius: Double, p: Double, q: Double, t: Double): Vector {
        // r_tube = R + r * cos(q * t): modulated radius taking into account transverse turns
        val tubeRadius = majorRadius + minorRadius * cos(q * t)
        // x = (R + r * cos(q * t)) * cos(p * t): X coordinate taking into account longitudinal turns
        val x = tubeRadius * cos(p * t)
        // y = -r * sin(q * t): vertical displacement of the nodal tube
        val y = -minorRadius * sin(q * t)
        // z = (R + r * cos(q * t)) * sin(p * t): Z coordinate taking into account longitudinal turns
        val z = tubeRadius * sin(p * t)
        return Vector(x, y, z)
    }

    // calculates one step of the differential equations of the Lorenz attractor
    // https://en.wikipedia.org/wiki/Lorenz_system ODU system:
    // dx/dt = σ(y - x)
    // dy/dt = x(ρ - z) - y
    // dz/dt = xy - βz
    fun lorenzStep(current: Vector, sigma: Double, rho: Double, beta: Double, dt: Double): Vector {
        // dx = sigma * (y - x) * dt: change in X according to the Lorenz equation
        val dx = sigma * (current.y - current.x) * dt
        // dy = (x * (rho - z) - y) * dt: change in Y according to the Lorenz equation
        val dy = (current.x * (rho - current.z) - current.y) * dt
        // dz = (x * y - beta * z) * dt: change in Z according to the Lorenz equation
        val dz = (current.x * current.y - beta * current.z) * dt
        return Vector(current.x + dx, current.y + dy, current.z + dz)
    }

    // calculates one step of the differential equations of the Aizawa Strange Attractor
    // https://en.wikipedia.org/wiki/Aizawa_system
    // dx/dt = x(cos β + sin α) - z sin(α - β)
    // dy/dt = y(cos β - sin α) + z(cos(α - β) + 1)
    // dz/dt = z sin(γ) + (x² + y²)(α - cos β)
    fun aizawaStep(current: Vector, alpha: Double, beta: Double, gamma: Double, dt: Double): Vector {
        // sinAlpha/sinBeta/cosAlpha/cosBeta: pre-trig values reused across all three components
        val sinAlpha = sin(alpha)
        val cosAlpha = cos(alpha)
        val sinBeta = sin(beta)
        val cosBeta = cos(beta)
        val sinGamma = sin(gamma)
        val sinAlphaMinusBeta = sin(alpha - beta)
        val cosAlphaMinusBeta = cos(alpha - beta)
        // dx = (x(cos β + sin α) - z sin(α - β)) * dt: change in X per the Aizawa equation
        val dx = (current.x * (cosBeta + sinAlpha) - current.z * sinAlphaMinusBeta) * dt
        // dy = (y(cos β - sin α) + z(cos(α - β) + 1)) * dt: change in Y per the Aizawa equation
        val dy = (current.y * (cosBeta - sinAlpha) + current.z * (cosAlphaMinusBeta + 1.0)) * dt
        // dz = (z sin γ + (x² + y²)(α - cos β)) * dt: change in Z per the Aizawa equation
        val dz = (current.z * sinGamma + (current.x * current.x + current.y * current.y) * (alpha - cosBeta)) * dt
        return Vector(current.x + dx, current.y + dy, current.z + dz)
    }

    // calculates one step of the differential equations of the Thomas Prehydretica Attractor
    // https://en.wikipedia.org/wiki/Thomas%27_cyclically_symmetric_attractor
    // dx/dt = sin y - b x
    // dy/dt = sin z - b y
    // dz/dt = sin x - b z
    fun thomasStep(current: Vector, b: Double, dt: Double): Vector {
        // dx = (sin(y) - b * x) * dt: change in X coupling to the sine of Y with linear damping
        val dx = (sin(current.y) - b * current.x) * dt
        // dy = (sin(z) - b * y) * dt: change in Y coupling to the sine of Z with linear damping
        val dy = (sin(current.z) - b * current.y) * dt
        // dz = (sin(x) - b * z) * dt: change in Z coupling to the sine of X with linear damping
        val dz = (sin(current.x) - b * current.z) * dt
        return Vector(current.x + dx, current.y + dy, current.z + dz)
    }

    // calculates one step of the differential equations of the Rössler Attractor
    // https://en.wikipedia.org/wiki/R%C3%B6ssler_system
    // dx/dt = -(y + z)
    // dy/dt = x + a*y
    // dz/dt = b + z*(x - c)
    fun rosslerStep(current: Vector, a: Double, b: Double, c: Double, dt: Double): Vector {
        // dx = -(y + z) * dt: change in X driven by the sum of Y and Z
        val dx = -(current.y + current.z) * dt
        // dy = (x + a * y) * dt: change in Y driven by X with linear coupling
        val dy = (current.x + a * current.y) * dt
        // dz = (b + z * (x - c)) * dt: change in Z with constant offset and nonlinear feedback
        val dz = (b + current.z * (current.x - c)) * dt
        return Vector(current.x + dx, current.y + dy, current.z + dz)
    }

    // calculates one step of the differential equations of the Halvorsen Attractor
    // https://en.wikipedia.org/wiki/Halvorsen_system
    // dx/dt = -a x - y - z + a z
    // dy/dt = x - a y + z
    // dz/dt = x + b (y - x)
    fun halvorsenStep(current: Vector, a: Double, dt: Double): Vector {
        // dx = (-a * x - y - z + a * z) * dt: change in X with self-damping and cross-axis coupling
        val dx = (-a * current.x - current.y - current.z + a * current.z) * dt
        // dy = (x - a * y + z) * dt: change in Y driven by X and Z with linear damping
        val dy = (current.x - a * current.y + current.z) * dt
        // dz = (x + a * (y - x)) * dt: change in Z driven by X and the difference of Y and X
        val dz = (current.x + a * (current.y - current.x)) * dt
        return Vector(current.x + dx, current.y + dy, current.z + dz)
    }

    // interpolates a single coordinate of a cubic Catmull-Rom spline from four control coordinates
    // P(t) = 0.5 * ((2 * P1) + (-P0 + P2) * t + (2*P0 - 5*P1 + 4*P2 - P3) * t^2 + (-P0 + 3*P1 - 3*P2 + P3) * t^3)
    fun catmullRomComponent(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
        // t2 = t * t: square of the interpolation parameter
        val t2 = t * t
        // t3 = t2 * t: cube of the interpolation parameter
        val t3 = t2 * t
        // a0 = -0.5 * t3 + t2 - 0.5 * t: weighting factor for p0
        val a0 = -0.5 * t3 + t2 - 0.5 * t
        // a1 = 1.5 * t3 - 2.5 * t2 + 1.0: weighting factor for p1
        val a1 = 1.5 * t3 - 2.5 * t2 + 1.0
        // a2 = -1.5 * t3 + 2.0 * t2 + 0.5 * t: weighting factor for p2
        val a2 = -1.5 * t3 + 2.0 * t2 + 0.5 * t
        // a3 = 0.5 * t3 - 0.5 * t2: weighting factor for p3
        val a3 = 0.5 * t3 - 0.5 * t2
        return a0 * p0 + a1 * p1 + a2 * p2 + a3 * p3
    }

    // calculates a point on a cubic Catmull-Rom spline
    // https://en.wikipedia.org/wiki/Centripetal_Catmull%E2%80%93Rom_spline P(t) = 0.5 * ((2 * P1) + (-P0 + P2) * t + (2*P0 - 5*P1 + 4*P2 - P3) * t^2 + (-P0 + 3*P1 - 3*P2 + P3) * t^3)
    fun catmullRom(p0: Location, p1: Location, p2: Location, p3: Location, t: Double): Location {
        // x = catmullRomComponent(...): interpolates each axis independently with the shared weights
        val x = catmullRomComponent(p0.x, p1.x, p2.x, p3.x, t)
        val y = catmullRomComponent(p0.y, p1.y, p2.y, p3.y, t)
        val z = catmullRomComponent(p0.z, p1.z, p2.z, p3.z, t)
        return Location(p1.world, x, y, z)
    }

    // calculates the Gielis superformula for parametric 2D/3D superforms
    // https://en.wikipedia.org/wiki/Superformula r(phi) = ( |cos(m*phi / 4) / a|^n2 + |sin(m*phi / 4) / b|^n3 )^(-1 / n1)
    fun superformula(phi: Double, m: Double, a: Double, b: Double, n1: Double, n2: Double, n3: Double): Double {
        // angleComponent = m * phi / 4: normalized angular step of symmetry
        val angleComponent = m * phi * 0.25
        // term1 = |cos(m*phi/4) / a|^n2: cosine basis of deformation
        val term1 = (abs(cos(angleComponent)) / a).pow(n2)
        // term2 = |sin(m*phi/4) / b|^n3: sinusoidal basis of deformation
        val term2 = (abs(sin(angleComponent)) / b).pow(n3)
        // sum = term1 + term2: sum of basic terms
        val sum = term1 + term2
        if (sum == 0.0) return 0.0
        // r = sum^(-1 / n1): inverse power-law radius contraction
        return sum.pow(-1.0 / n1)
    }

    // calculates quadratic Ease-In
    fun easeInQuad(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        // f(t) = t^2: accelerates from zero velocity
        return clampedT * clampedT
    }

    // calculates quadratic Ease-Out
    fun easeOutQuad(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        val u = 1.0 - clampedT
        // f(t) = 1 - (1 - t)^2: decelerates to zero velocity
        return 1.0 - u * u
    }

    // calculates quadratic Ease-In-Out
    fun easeInOutQuad(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        return if (clampedT < HALF) {
            // f(t) = 2 * t^2: accelerates on the first half
            DOUBLE_SCALE * clampedT * clampedT
        } else {
            val factor = EASE_IN_OUT_CUBIC_SCALER * clampedT + EASE_IN_OUT_CUBIC_OFFSET
            // f(t) = 1 - (-2*t + 2)^2 / 2: decelerates on the second half
            1.0 - (factor * factor) * HALF
        }
    }

    // calculates cubic Ease-In
    fun easeInCubic(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        // f(t) = t^3: cubic acceleration from zero velocity
        return clampedT * clampedT * clampedT
    }

    // calculates cubic Ease-Out
    fun easeOutCubic(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        val u = 1.0 - clampedT
        // f(t) = 1 - (1 - t)^3: cubic deceleration to zero velocity
        return 1.0 - u * u * u
    }

    // calculates cubic Ease-In-Out
    fun easeInOutCubic(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        return if (clampedT < EASE_IN_OUT_CUBIC_THRESHOLD) {
            // f(t) = 4 * t^3: accelerates on the first half
            EASE_IN_OUT_CUBIC_COEFFICIENT * clampedT * clampedT * clampedT
        } else {
            val factor = EASE_IN_OUT_CUBIC_SCALER * clampedT + EASE_IN_OUT_CUBIC_OFFSET
            // f(t) = 1 - (-2*t + 2)^3 / 2: decelerates on the second half
            1.0 - (factor * factor * factor) * HALF
        }
    }

    // calculates sinusoidal Ease-In
    fun easeInSine(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        // f(t) = 1 - cos((t * pi) / 2): smooth sinusoidal acceleration
        return 1.0 - cos((clampedT * PI) * HALF)
    }

    // calculates sinusoidal Ease-Out
    fun easeOutSine(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        // f(t) = sin((t * pi) / 2): smooth sinusoidal deceleration
        return sin((clampedT * PI) * HALF)
    }

    // calculates sinusoidal Ease-In-Out
    fun easeInOutSine(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        // f(t) = -(cos(pi * t) - 1) / 2: symmetrical sinusoidal transition
        return -(cos(PI * clampedT) - 1.0) * HALF
    }

    // calculates elastic Ease-In
    fun easeInElastic(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        if (clampedT == 0.0) return 0.0
        if (clampedT == 1.0) return 1.0
        val u = clampedT - 1.0
        // growth = 2^(10 * (t - 1)): exponential amplitude buildup
        val growth = 2.0.pow(ELASTIC_EXPONENT_SCALE * u)
        // oscillation = sin((u * 10 - 0.75) * (2*pi)/3): elastic spring wave
        val oscillation = sin((u * ELASTIC_EXPONENT_SCALE - ELASTIC_PHASE_OFFSET) * ELASTIC_PERIOD_FACTOR)
        return -growth * oscillation
    }

    // calculates elastic Ease-Out
    fun easeOutElastic(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        if (clampedT == 0.0) return 0.0
        if (clampedT == 1.0) return 1.0
        // decay = 2^(-10 * t): exponential dampening factor
        val decay = 2.0.pow(-ELASTIC_EXPONENT_SCALE * clampedT)
        // oscillation = sin((t * 10 - 0.75) * (2*pi)/3): spring vibration
        val oscillation = sin((clampedT * ELASTIC_EXPONENT_SCALE - ELASTIC_PHASE_OFFSET) * ELASTIC_PERIOD_FACTOR)
        return decay * oscillation + 1.0
    }

    // calculates bounce Ease-Out
    fun easeOutBounce(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        return when {
            clampedT < BOUNCE_T1 -> {
                // f(t) = 7.5625 * t^2: initial bounce
                BOUNCE_N1 * clampedT * clampedT
            }
            clampedT < BOUNCE_T2 -> {
                val tSub = clampedT - BOUNCE_OFFSET1
                // f(t) = 7.5625 * (t - 1.5/2.75)^2 + 0.75: second bounce
                BOUNCE_N1 * tSub * tSub + BOUNCE_SCALER2
            }
            clampedT < BOUNCE_T3 -> {
                val tSub = clampedT - BOUNCE_OFFSET2
                // f(t) = 7.5625 * (t - 2.25/2.75)^2 + 0.9375: third bounce
                BOUNCE_N1 * tSub * tSub + BOUNCE_SCALER3
            }
            else -> {
                val tSub = clampedT - BOUNCE_OFFSET3
                // f(t) = 7.5625 * (t - 2.625/2.75)^2 + 0.984375: final settle
                BOUNCE_N1 * tSub * tSub + BOUNCE_SCALER4
            }
        }
    }

    // calculates bounce Ease-In
    fun easeInBounce(t: Double): Double {
        // f(t) = 1 - easeOutBounce(1 - t): inverse bounce curve
        return 1.0 - easeOutBounce(1.0 - t.coerceIn(0.0, 1.0))
    }

    // calculates bounce Ease-In-Out
    fun easeInOutBounce(t: Double): Double {
        val clampedT = t.coerceIn(0.0, 1.0)
        return if (clampedT < HALF) {
            // f(t) = (1 - easeOutBounce(1 - 2*t)) / 2: bounce-in on first half
            (1.0 - easeOutBounce(1.0 - DOUBLE_SCALE * clampedT)) * HALF
        } else {
            // f(t) = (1 + easeOutBounce(2*t - 1)) / 2: bounce-out on second half
            (1.0 + easeOutBounce(DOUBLE_SCALE * clampedT - 1.0)) * HALF
        }
    }
}