package com.utophii.effects

import com.utophii.api.ContextualEffectModifier
import com.utophii.api.EffectOptions
import com.utophii.api.ParticleEffect
import com.utophii.engine.FXEngine
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.inventory.ItemStack

// shared async calculation and sync rendering for parametric effects
abstract class AbstractParticleEffect(final override val name: String) : ParticleEffect {

    override fun play(center: Location, opts: EffectOptions) {
        val stableCenter = center.clone()
        FXEngine.scheduler().scheduleFrames(
            durationTicks = opts.duration.coerceAtLeast(MIN_DURATION_TICKS),
            calculate = { time -> calculate(stableCenter, opts, time) },
            render = { _, positions -> render(positions, opts) },
        )
    }

    protected fun applyModifiers(location: Location, options: EffectOptions, time: Double, center: Location = location): Location {
        return options.modifiers.fold(location) { current, modifier ->
            when (modifier) {
                is ContextualEffectModifier -> modifier.modify(current, center, time)
                else -> modifier.modify(current, time)
            }
        }
    }

    protected fun param(options: EffectOptions, key: String, defaultValue: Double): Double {
        return options.parameters[key] ?: defaultValue
    }

    private fun render(positions: List<Location>, opts: EffectOptions) {
        val data = resolveParticleData(opts)
        positions.forEach { location ->
            FXEngine.scheduler().spawnParticle(
                location = location,
                particle = opts.particle,
                data = data,
                receivers = opts.receivers,
                count = opts.count,
                offsetX = opts.offsetX,
                offsetY = opts.offsetY,
                offsetZ = opts.offsetZ,
                speed = opts.speed,
            )
        }
    }

    companion object {
        private const val MIN_DURATION_TICKS = 1L

        // creates appropriate Bukkit particle payload based on particle type
        fun resolveParticleData(opts: EffectOptions): Any? {
            return when (opts.particle) {
                Particle.DUST -> {
                    val color = opts.color ?: Color.RED
                    Particle.DustOptions(color, opts.dustSize)
                }
                Particle.DUST_COLOR_TRANSITION -> {
                    val fromColor = opts.color ?: Color.RED
                    val toColor = opts.toColor ?: Color.BLUE
                    Particle.DustTransition(fromColor, toColor, opts.dustSize)
                }
                Particle.BLOCK, Particle.FALLING_DUST, Particle.BLOCK_MARKER, Particle.DUST_PILLAR -> {
                    val material = opts.material ?: Material.STONE
                    if (material.isBlock) material.createBlockData() else Material.STONE.createBlockData()
                }
                Particle.ITEM -> {
                    val material = opts.material ?: Material.STONE
                    ItemStack(material)
                }
                else -> null
            }
        }
    }
}