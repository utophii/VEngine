package com.utophii.modifiers

import com.utophii.api.ContextualEffectModifier
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.exp
import kotlin.math.sqrt

// Hydrodynamic vortex modifier (Rankine/Gaussian Vortex)
// spins particles around the axis of rotation with decreasing angular velocity as they move away from the center
// omega(r) = vortexStrength * exp(-(r^2) / (2 * coreRadius^2))
// theta(r, t) = omega(r) * time
// p' = origin + Rodrigues(p - origin, axis, theta(r, t))
class VortexModifier(
    private val axis: Vector = DEFAULT_AXIS,
    private val coreRadius: Double = DEFAULT_CORE_RADIUS,
    private val vortexStrength: Double = DEFAULT_STRENGTH,
) : ContextualEffectModifier {

    override fun modify(loc: Location, center: Location, time: Double): Location {
        if (axis.lengthSquared() <= MIN_AXIS_SQ) return loc

        val rel = loc.toVector().subtract(center.toVector())
        val distSq = rel.lengthSquared()
        if (distSq <= MIN_AXIS_SQ) return loc

        // r = sqrt(distSq): distance from the particle to the center of the vortex
        val r = sqrt(distSq)
        // exponent = -(r^2) / (2 * coreRadius^2): Gaussian decay of angular velocity
        val exponent = -distSq / (2.0 * coreRadius * coreRadius)
        // angularVelocity = strength * exp(exponent): angular velocity of rotation at a given radius
        val angularVelocity = vortexStrength * exp(exponent)
        // theta = angularVelocity * time: rotation angle for the current time
        val theta = angularVelocity * time

        // rotated = Rodrigues(rel, axis, theta): rotation of the vector around the vortex axis
        val rotated = MathUtils.rotateRodrigues(rel, axis, theta)
        return center.clone().add(rotated)
    }

    companion object {
        private val DEFAULT_AXIS = Vector(0.0, 1.0, 0.0)
        private const val DEFAULT_CORE_RADIUS = 1.2
        private const val DEFAULT_STRENGTH = 0.15
        private const val MIN_AXIS_SQ = 1.0E-10
    }
}