package com.utophii.modifiers

import com.utophii.api.ContextualEffectModifier
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector
import kotlin.math.exp

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

        // rel = loc - center: particle offset from the vortex center
        val relX = loc.x - center.x
        val relY = loc.y - center.y
        val relZ = loc.z - center.z
        // distSq = |rel|^2: squared radial distance from the vortex core
        val distSq = relX * relX + relY * relY + relZ * relZ
        if (distSq <= MIN_AXIS_SQ) return loc

        // exponent = -(r^2) / (2 * coreRadius^2): Gaussian decay of angular velocity
        val exponent = -distSq / (2.0 * coreRadius * coreRadius)
        // angularVelocity = strength * exp(exponent): angular velocity of rotation at a given radius
        val angularVelocity = vortexStrength * exp(exponent)
        // theta = angularVelocity * time: rotation angle for the current time
        val theta = angularVelocity * time

        // p' = center + Rodrigues(rel, axis, theta): rotation of the vector around the vortex axis (in place)
        MathUtils.rotateAndTranslateInto(loc, center.x, center.y, center.z, relX, relY, relZ, axis, theta)
        return loc
    }

    companion object {
        val DEFAULT_AXIS = Vector(0.0, 1.0, 0.0)
        const val DEFAULT_CORE_RADIUS = 1.2
        const val DEFAULT_STRENGTH = 0.15
        private const val MIN_AXIS_SQ = 1.0E-10
    }
}