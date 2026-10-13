package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.engine.FXEngine
import org.bukkit.Location

// renders the frames of one running effect on the server thread
class FrameRenderer {
    private var displays: DisplayEntityPool? = null

    fun render(positions: List<Location>, opts: EffectOptions, time: Double) {
        val display = opts.display
        if (display == null) {
            displays?.clear()
            spawnParticles(positions, opts)
            return
        }
        val pool = displays ?: DisplayEntityPool(FXEngine.scheduler()).also { displays = it }
        pool.sync(positions, opts, display, time)
        if (display.withParticles) {
            spawnParticles(positions, opts)
        }
    }

    // removes any display entities spawned by this run
    fun dispose() {
        displays?.clear()
        displays = null
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
