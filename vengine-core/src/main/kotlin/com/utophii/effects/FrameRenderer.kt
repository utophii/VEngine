package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.engine.FXEngine
import com.utophii.engine.FollowAnchor
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle

// renders the frames of one running effect on the server thread.
// Origin is the effect's spawn point, the reference for client-side display models
class FrameRenderer(private val origin: Location) {
    private var displays: DisplayEntityPool? = null
    private var client: ClientDisplays? = null
    private val gradient = GradientColors()

    // particle payload (DustOptions, BlockData, ItemStack) is rebuilt only when the options that shape it change.
    // Building it per frame allocated an object on every rendered frame of every running effect
    private val payload = PayloadCache()

    // anchor is set for followed effects
    fun render(buffer: ParticleBuffer, opts: EffectOptions, time: Double, anchor: FollowAnchor? = null) {
        val positions = buffer.list()
        val colored = gradient.resolve(buffer, opts)
        val display = opts.display
        if (display == null) {
            clearDisplays()
            spawnParticles(positions, opts, colored)
            return
        }
        val colors = if (colored) gradient.argb() else null
        // with PacketEvents the models exist only on the clients
        val clientPool = client ?: PacketDisplays.create(origin)?.also { client = it }
        if (clientPool != null) {
            displays?.clear()
            clientPool.sync(positions, opts, display, time, anchor, colors)
        } else {
            val pool = displays ?: DisplayEntityPool(FXEngine.scheduler()).also { displays = it }
            pool.sync(positions, opts, display, time, anchor?.frameRotation(), colors)
        }
        if (display.withParticles) {
            spawnParticles(positions, opts, colored)
        }
    }

    // removes any display entities spawned by this run
    fun dispose() {
        clearDisplays()
        displays = null
        client = null
    }

    private fun clearDisplays() {
        displays?.clear()
        client?.clear()
    }

    private fun spawnParticles(positions: List<Location>, opts: EffectOptions, colored: Boolean) {
        val data = payload.resolve(opts)
        FXEngine.scheduler().spawnParticles(
            locations = positions,
            particle = opts.particle,
            data = data,
            receivers = opts.receivers,
            count = opts.count,
            offsetX = opts.offsetX,
            offsetY = opts.offsetY,
            offsetZ = opts.offsetZ,
            speed = opts.speed,
            dataAt = if (colored && gradient.colorsParticles()) gradient::dataAt else null,
            budgeted = !opts.budgetExempt,
            lod = opts.lod,
        )
    }

    // caches the particle payload of one renderer, keyed by the options that determine it
    private class PayloadCache {
        private var particle: Particle? = null
        private var color: Color? = null
        private var toColor: Color? = null
        private var dustSize: Float = -1.0f
        private var material: Material? = null
        private var value: Any? = null

        fun resolve(opts: EffectOptions): Any? {
            val p = opts.particle
            val c = opts.color
            val tc = opts.toColor
            val ds = opts.dustSize
            val m = opts.material

            if (p != particle || c != color || tc != toColor || ds != dustSize || m != material || value == null) {
                particle = p
                color = c
                toColor = tc
                dustSize = ds
                material = m
                value = AbstractParticleEffect.resolveParticleData(opts)
            }
            return value
        }
    }
}
