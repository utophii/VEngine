package com.utophii

import com.utophii.commands.VEngineCommand
import com.utophii.config.YamlEffectLoader
import com.utophii.engine.FXEngine
import org.bukkit.plugin.java.JavaPlugin

class VEngine : JavaPlugin() {
    private lateinit var yamlEffectLoader: YamlEffectLoader

    override fun onEnable() {
        FXEngine.initialize(this)
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
        val loaded = yamlEffectLoader.loadAll()
        logger.info("VEngine effects reloaded: ${loaded.size} scripted effects available.")
    }

    private fun saveBundledEffectExamples() {
        saveIfMissing("effects/torus.yml")
        saveIfMissing("effects/cosmic_gate.yml")
    }

    private fun saveIfMissing(path: String) {
        val target = dataFolder.resolve(path)
        if (!target.exists()) {
            saveResource(path, false)
        }
    }
}
