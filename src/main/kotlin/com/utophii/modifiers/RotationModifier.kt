package com.utophii.modifiers

import com.utophii.api.EffectModifier
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector

// rotates positions around an origin by Rodrigues' formula
class RotationModifier(
    private val origin: Location,
    private val axis: Vector,
    private val angularSpeed: Double,
) : EffectModifier {
    // rotates a location around [axis] by angularSpeed * time
    // uses v cos(theta) + (k x v) sin(theta) + k(k dot v)(1 - cos(theta))
    override fun modify(loc: Location, time: Double): Location {
        val relative = loc.toVector().subtract(origin.toVector())
        // theta = angularSpeed * time: advances the rotation angle over animation time
        val theta = angularSpeed * time
        val rotated = MathUtils.rotateRodrigues(relative, axis, theta)
        return origin.clone().add(rotated)
    }
}
