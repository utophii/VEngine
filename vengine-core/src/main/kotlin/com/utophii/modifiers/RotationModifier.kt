package com.utophii.modifiers

import com.utophii.api.ContextualEffectModifier
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector

// modifier that spins particle positions around an arbitrary axis with configurable angular velocity
class RotationModifier private constructor(
    private val fixedOrigin: Location?,
    private val axis: Vector,
    private val angularVelocity: Double,
    private val initialAngle: Double,
    private val pivotOffset: Vector,
) : ContextualEffectModifier {

    // creates a center-relative rotation modifier
    // axis line formula: origin = center + pivotOffset, θ = initialAngle + angularVelocity * time
    constructor(
        axis: Vector = DEFAULT_AXIS,
        angularVelocity: Double = DEFAULT_ANGULAR_VELOCITY,
        initialAngle: Double = DEFAULT_INITIAL_ANGLE,
        pivotOffset: Vector = DEFAULT_PIVOT_OFFSET,
    ) : this(
        fixedOrigin = null,
        axis = axis.clone(),
        angularVelocity = angularVelocity,
        initialAngle = initialAngle,
        pivotOffset = pivotOffset.clone(),
    )

    // creates a fixed-origin rotation modifier for programmatic API usage
    // preserves the original API shape while using θ = angularSpeed * time
    constructor(
        origin: Location,
        axis: Vector,
        angularSpeed: Double,
    ) : this(
        fixedOrigin = origin.clone(),
        axis = axis.clone(),
        angularVelocity = angularSpeed,
        initialAngle = DEFAULT_INITIAL_ANGLE,
        pivotOffset = DEFAULT_PIVOT_OFFSET.clone(),
    )

    // rotates a particle around an axis through center + pivotOffset
    // mutates loc in place (no Location/Vector allocation) so the pooled buffer slot is reused
    override fun modify(loc: Location, center: Location, time: Double): Location {
        if (axis.lengthSquared() <= MIN_AXIS_LENGTH_SQUARED) {
            return loc
        }
        // origin = fixedOrigin ?: center + pivotOffset: rotation axis anchor expressed as plain components
        val originX = fixedOrigin?.x ?: (center.x + pivotOffset.x)
        val originY = fixedOrigin?.y ?: (center.y + pivotOffset.y)
        val originZ = fixedOrigin?.z ?: (center.z + pivotOffset.z)
        // relative = loc - origin: particle vector measured from the rotation pivot
        val relX = loc.x - originX
        val relY = loc.y - originY
        val relZ = loc.z - originZ
        // theta = initialAngle + angularVelocity * time: advances spin angle at a configurable angular velocity
        val theta = initialAngle + angularVelocity * time
        // p' = origin + Rodrigues(relative, axis, theta): rotates and translates back into world coordinates
        MathUtils.rotateAndTranslateInto(loc, originX, originY, originZ, relX, relY, relZ, axis, theta)
        return loc
    }

    companion object {
        val DEFAULT_AXIS = Vector(0.0, 1.0, 0.0)
        val DEFAULT_PIVOT_OFFSET = Vector(0.0, 0.0, 0.0)
        const val DEFAULT_ANGULAR_VELOCITY = 0.05
        const val DEFAULT_INITIAL_ANGLE = 0.0
        private const val MIN_AXIS_LENGTH_SQUARED = 1.0E-12
    }
}
