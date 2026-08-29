package com.utophii

import com.utophii.commands.VEngineCommand
import com.utophii.config.YamlEffectLoader
import com.utophii.engine.EffectScheduler
import com.utophii.engine.FXEngine
import org.bukkit.plugin.java.JavaPlugin

class VEngine : JavaPlugin() {
    private lateinit var yamlEffectLoader: YamlEffectLoader

    override fun onEnable() {
        FXEngine.initialize(this)
        applyConfig()
        yamlEffectLoader = YamlEffectLoader(this)
        yamlEffectLoader.ensureDirectories()
        saveBundledEffectExamples()
        val loaded = yamlEffectLoader.loadAll()

        val cmd = getCommand("vengine")
            ?: error("Command 'vengine' missing from plugin.yml")
        val handler = VEngineCommand(this)
        cmd.setExecutor(handler)
        cmd.tabCompleter = handler

        logger.info("VEngine enabled with ${loaded.size} scripted effects and ${FXEngine.effectNames().size} total registry entries.")
    }

    override fun onDisable() {
        FXEngine.shutdown()
    }

    fun reload() {
        applyConfig()
        val loaded = yamlEffectLoader.loadAll()
        logger.info("VEngine effects reloaded: ${loaded.size} scripted effects available.")
    }

    // reads plugin configuration and applies view-distance culling to the active scheduler
    private fun applyConfig() {
        saveDefaultConfig()
        val viewDistance = config.getDouble(
            VIEW_DISTANCE_CONFIG_KEY,
            EffectScheduler.DEFAULT_VIEW_DISTANCE_BLOCKS,
        )
        FXEngine.scheduler().viewDistance = viewDistance
        logger.info("VEngine particle view distance set to ${FXEngine.scheduler().viewDistance} blocks.")
    }

    companion object {
        private const val VIEW_DISTANCE_CONFIG_KEY = "view-distance"
    }

    private fun saveBundledEffectExamples() {
        saveIfMissing("effects/torus.yml")
        saveIfMissing("effects/cosmic_gate.yml")
        saveIfMissing("effects/rk4_comet.yml")
    }

    private fun saveIfMissing(path: String) {
        val target = dataFolder.resolve(path)
        if (!target.exists()) {
            saveResource(path, false)
        }
    }
}
