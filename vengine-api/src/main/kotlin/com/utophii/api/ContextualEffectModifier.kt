package com.utophii.api

import org.bukkit.Location

// optional modifier contract for transforms that need the effect center as a pivot or frame of reference
// EffectModifier for the baseline formula loc' = modify(loc, time)
interface ContextualEffectModifier : EffectModifier {
    // applies a position transform using the current effect center
    // use this method for formulas such as rotation around an axis through center + pivotOffset
    fun modify(loc: Location, center: Location, time: Double): Location

    // applies this contextual modifier without a supplied center
    // falls back to using loc itself as the context center to preserve the original [EffectModifier] contract
    override fun modify(loc: Location, time: Double): Location {
        // center = loc: preserves compatibility when a contextual modifier is invoked through the legacy two-argument API
        return modify(loc, loc, time)
    }
}
