package com.utophii.effects

import com.utophii.api.DisplayOptions
import org.bukkit.entity.Display

// turns the display model at each point of a curve, surface or points block on its own.
// Formulas see the same variables as the coordinates of that point
sealed interface DisplayOrientation {
    // faces the model's local +Z along this direction in geometry coordinates
    data class Normal(val x: String, val y: String, val z: String) : DisplayOrientation

    // radians composed like display.rotation, so z turns the model within its own plane
    data class Angles(val x: String = "0", val y: String = "0", val z: String = "0") : DisplayOrientation

    // faces the model along normalize(dP/du x dP/dv) of a two-variable surface, or the opposite side
    data class AlignToNormal(val side: Side = Side.OUTWARD) : DisplayOrientation

    enum class Side { OUTWARD, INWARD }

    companion object {
        val NORMAL_KEYS = listOf("normalX", "normalY", "normalZ")
        val ANGLE_KEYS = listOf("rotX", "rotY", "rotZ")
        val FORMULA_KEYS = NORMAL_KEYS + ANGLE_KEYS
        const val ALIGN_KEY = "alignToNormal"

        // the explicit orientation written as formula fields, prefix names the block in messages
        fun fromFields(fields: Map<String, String>, prefix: String): DisplayOrientation? {
            val normals = NORMAL_KEYS.filter(fields::containsKey)
            val angles = ANGLE_KEYS.filter(fields::containsKey)
            require(normals.isEmpty() || angles.isEmpty()) { conflict(prefix, normals + angles) }
            if (normals.isNotEmpty()) {
                val missing = NORMAL_KEYS - normals.toSet()
                require(missing.isEmpty()) {
                    "${missing.joinToString { "$prefix.$it" }} missing. A normal needs normalX, normalY and normalZ, " +
                        "write 0 for a zero component"
                }
                return Normal(fields.getValue("normalX"), fields.getValue("normalY"), fields.getValue("normalZ"))
            }
            if (angles.isNotEmpty()) {
                return Angles(fields["rotX"] ?: "0", fields["rotY"] ?: "0", fields["rotZ"] ?: "0")
            }
            return null
        }

        fun conflict(prefix: String, keys: List<String>): String =
            "$prefix sets more than one orientation: ${keys.joinToString()}. " +
                "Use normalX/normalY/normalZ, rotX/rotY/rotZ or alignToNormal, and display.rotation for a shared correction"
    }
}

// per-point orientation only applies to fixed point models, ribbons orient segments by display.normal
internal val DisplayOptions?.acceptsPointOrientation: Boolean
    get() = this != null && type != DisplayOptions.Type.RIBBON && resolvedBillboard == Display.Billboard.FIXED