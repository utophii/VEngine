package com.utophii.modifiers

import com.utophii.api.EffectModifier
import com.utophii.math.MathUtils
import org.bukkit.Location

// adds fBm turbulence to particle positions
class TurbulenceModifier(
    private val strength: Double = DEFAULT_STRENGTH,
    private val frequency: Double = DEFAULT_FREQUENCY,
    private val octaves: Int = DEFAULT_OCTAVES,
) : EffectModifier {
    
    // offsets each coordinate with deterministic fractional Brownian motion
    // val += amp * noise(position * freq); amp *= 0.5; freq *= 2.0
    // mutates loc in place so the pooled buffer slot is reused without allocation
    override fun modify(loc: Location, time: Double): Location {
        val x = loc.x * frequency
        val y = loc.y * frequency
        val z = loc.z * frequency
        // dx = fbm(x + time, y, z) * strength: turbulent X displacement
        loc.x += MathUtils.fbm(x + time, y, z, octaves) * strength
        // dy = fbm(x, y + time, z) * strength: turbulent Y displacement
        loc.y += MathUtils.fbm(x, y + time, z, octaves) * strength
        // dz = fbm(x, y, z + time) * strength: turbulent Z displacement
        loc.z += MathUtils.fbm(x, y, z + time, octaves) * strength
        return loc
    }

    companion object {
        private const val DEFAULT_STRENGTH = 0.12
        private const val DEFAULT_FREQUENCY = 0.35
        private const val DEFAULT_OCTAVES = 4
    }
}
