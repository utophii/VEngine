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
        yamlEffectLoader.loadAll()

        val cmd = getCommand("vengine")
            ?: error("Command 'vengine' missing from plugin.yml")
        val handler = VEngineCommand(this, yamlEffectLoader)
        cmd.setExecutor(handler)
        cmd.tabCompleter = handler

        logger.info("VEngine enabled with built-in parametric particle effects.")
    }

    override fun onDisable() {
        FXEngine.shutdown()
    }

    fun reload() {
        yamlEffectLoader.loadAll()
        logger.info("VEngine effects reloaded.")
    }

    private fun saveBundledEffectExamples() {
        val sample = dataFolder.resolve("effects/torus.yml")
        if (!sample.exists()) {
            saveResource("effects/torus.yml", false)
        }
    }
}