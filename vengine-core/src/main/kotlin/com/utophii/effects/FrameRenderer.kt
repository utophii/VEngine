package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.engine.FXEngine
import com.utophii.engine.FollowAnchor
import org.bukkit.Location

// renders the frames of one running effect on the server thread
class FrameRenderer {
    private var displays: DisplayEntityPool? = null
    private var riding: RidingDisplays? = null

    // anchor is set for followed effects
    fun render(positions: List<Location>, opts: EffectOptions, time: Double, anchor: FollowAnchor? = null) {
        val display = opts.display
        if (display == null) {
            clearDisplays()
            spawnParticles(positions, opts)
            return
        }
        // followed models ride the entity on the client when PacketEvents is available
        val ridingPool = if (anchor != null) riding ?: PacketDisplays.create()?.also { riding = it } else null
        if (anchor != null && ridingPool != null) {
            displays?.clear()
            ridingPool.sync(positions, opts, display, time, anchor)
        } else {
            val pool = displays ?: DisplayEntityPool(FXEngine.scheduler()).also { displays = it }
            pool.sync(positions, opts, display, time, anchor?.frameRotation())
        }
        if (display.withParticles) {
            spawnParticles(positions, opts)
        }
    }

    // removes any display entities spawned by this run
    fun dispose() {
        clearDisplays()
        displays = null
        riding = null
    }

    private fun clearDisplays() {
        displays?.clear()
        riding?.clear()
    }

    private fun spawnParticles(positions: List<Location>, opts: EffectOptions) {
        val data = AbstractParticleEffect.resolveParticleData(opts)
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
}
