package com.utophii.api

import org.bukkit.Location

// public contract implemented by every VEngine particle effect
interface ParticleEffect {
    val name: String

    /**
     * renders this effect around a center point
     *
     * @param center center of the effect in world coordinates
     * @param opts runtime rendering and transform options
     * @implNote implementations must keep Bukkit particle spawning on the sync thread
     */
    fun play(center: Location, opts: EffectOptions)

    /**
     * calculates particle positions for a point in effect time
     *
     * @param center center of the effect in world coordinates
     * @param opts runtime rendering and transform options
     * @param time animation time in ticks
     * @return mutable Bukkit locations that will be rendered synchronously
     */
    fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location>
}
