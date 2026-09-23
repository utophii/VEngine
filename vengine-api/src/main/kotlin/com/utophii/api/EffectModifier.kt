package com.utophii.api

import org.bukkit.Location

// post-processes a particle position before it is rendered
fun interface EffectModifier {
    /**
     * applies a position transform at the supplied animation time
     *
     * @param loc mutable particle position
     * @param time normalized or tick-based effect time, depending on the caller
     * @return transformed location
     * @implNote implementations should avoid Bukkit world mutations; particle spawning is handled separately
     */
    fun modify(loc: Location, time: Double): Location
}
