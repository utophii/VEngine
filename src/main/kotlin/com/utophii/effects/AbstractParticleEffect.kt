package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.ParticleEffect
import com.utophii.engine.FXEngine
import org.bukkit.Location
import org.bukkit.Particle

// shared async calculation and sync rendering for parametric effects
abstract class AbstractParticleEffect(final override val name: String) : ParticleEffect {
    // schedules this effect for its configured duration
    override fun play(center: Location, opts: EffectOptions) {
        val stableCenter = center.clone()
        FXEngine.scheduler().scheduleFrames(
            durationTicks = opts.duration.coerceAtLeast(MIN_DURATION_TICKS),
            calculate = { time -> calculate(stableCenter, opts, time) },
            render = { positions -> render(positions, opts) },
        )
    }

    // applies modifiers in registration order
    protected fun applyModifiers(location: Location, options: EffectOptions, time: Double): Location {
        return options.modifiers.fold(location) { current, modifier -> modifier.modify(current, time) }
    }

    // reads a double option parameter or returns a default value
    protected fun param(options: EffectOptions, key: String, defaultValue: Double): Double {
        return options.parameters[key] ?: defaultValue
    }

    private fun render(positions: List<Location>, opts: EffectOptions) {
        val data = dustData(opts)
        positions.forEach { location ->
            FXEngine.scheduler().spawnParticle(location, opts.particle, data, opts.receivers)
        }
    }

    private fun dustData(opts: EffectOptions): Any? {
        val color = opts.color ?: return null
        return when (opts.particle) {
            Particle.DUST -> Particle.DustOptions(color, DUST_SIZE)
            else -> null
        }
    }

    companion object {
        private const val MIN_DURATION_TICKS = 1L
        private const val DUST_SIZE = 1.0F
    }
}
