package com.utophii.effects

import com.utophii.api.ContextualEffectModifier
import com.utophii.api.EffectOptions
import com.utophii.api.EffectHandle
import com.utophii.api.ParticleEffect
import com.utophii.engine.FXEngine
import com.utophii.math.MathUtils
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector

// shared async calculation and sync rendering for parametric effects
abstract class AbstractParticleEffect(final override val name: String) : ParticleEffect {

    // plays the effect through the pooled render buffer, writing positions per frame without new allocations
    override fun play(center: Location, opts: EffectOptions): EffectHandle {
        val stableCenter = center.clone()
        return FXEngine.scheduler().scheduleFrames(
            effectName = name,
            durationTicks = opts.duration.coerceAtLeast(MIN_DURATION_TICKS),
            calculate = { time ->
                val buffer = ParticleBufferPool.obtain(stableCenter)
                try {
                    calculateInto(buffer, stableCenter, opts, time)
                    buffer
                } catch (t: Throwable) {
                    ParticleBufferPool.release(buffer)
                    throw t
                }
            },
            render = { _, buffer -> render(buffer.list(), opts) },
            release = { buffer -> ParticleBufferPool.release(buffer) },
        )
    }

    // one-off public calculation for external callers; the hot play() path reuses the pooled buffer
    // copies into independent locations so callers may retain the result without pooling side effects
    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> {
        val stableCenter = center.clone()
        val buffer = ParticleBufferPool.obtain(stableCenter)
        try {
            calculateInto(buffer, stableCenter, opts, time)
            return buffer.list().map { location -> location.clone() }
        } finally {
            ParticleBufferPool.release(buffer)
        }
    }

    // writes the effect's particle positions for `time` into the pooled buffer
    // subclasses override this to avoid allocating a fresh location per particle per frame
    // public so composite/scripted effects can render their primitive layers through the buffer
    open fun calculateInto(buffer: ParticleBuffer, center: Location, opts: EffectOptions, time: Double) {
        val positions = calculate(center, opts, time)
        buffer.acquire(positions.size)
        positions.forEachIndexed { index, location ->
            val target = buffer.location(index)
            target.x = location.x
            target.y = location.y
            target.z = location.z
        }
    }

    // applies the modifier chain in place, mutating the reusable location and avoiding allocation
    protected fun applyModifiersInto(target: Location, options: EffectOptions, time: Double, context: Location = target): Location {
        options.modifiers.forEach { modifier ->
            when (modifier) {
                is ContextualEffectModifier -> modifier.modify(target, context, time)
                else -> modifier.modify(target, time)
            }
        }
        return target
    }

    // writes one transformed particle into the pooled buffer slot at `index` from an offset vector
    protected fun writeParticle(
        buffer: ParticleBuffer,
        index: Int,
        local: Vector,
        center: Location,
        opts: EffectOptions,
        time: Double,
        context: Location = center,
    ) {
        writeParticle(buffer, index, local.x, local.y, local.z, center, opts, time, context)
    }

    // writes one transformed particle into the pooled buffer slot at `index` from raw offset components
    // `yaw` defaults to opts.rotationYaw but may be overridden (e.g. an effect that adds its own spin phase)
    protected fun writeParticle(
        buffer: ParticleBuffer,
        index: Int,
        x: Double,
        y: Double,
        z: Double,
        center: Location,
        opts: EffectOptions,
        time: Double,
        context: Location = center,
        yaw: Double = opts.rotationYaw,
    ) {
        val out = buffer.location(index)
        MathUtils.transformInto(out, x, y, z, center, opts.scale, yaw, opts.tiltAxis, opts.tiltAngle)
        applyModifiersInto(out, opts, time, context)
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