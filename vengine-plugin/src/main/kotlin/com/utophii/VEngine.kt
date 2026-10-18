package com.utophii

import com.utophii.commands.VEngineCommand
import com.utophii.config.YamlEffectLoader
import com.utophii.effects.packet.PacketDisplaysBootstrap
import com.utophii.engine.EffectScheduler
import com.utophii.engine.FXEngine
import com.utophii.mythic.MythicHook
import com.utophii.placeholders.PlaceholderService
import com.utophii.triggers.TriggerListener
import com.utophii.triggers.TriggerLoader
import com.utophii.triggers.TriggerService
import dev.faststats.ErrorTracker
import dev.faststats.bukkit.BukkitContext
import dev.faststats.data.Metric
import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.Yaml

class VEngine : JavaPlugin() {
    private lateinit var yamlEffectLoader: YamlEffectLoader
    private val placeholders = PlaceholderService(this)
    private val triggers = TriggerService(this)
    private var mythic: MythicHook? = null

    private val context = BukkitContext.Factory(this, "a98bb29a39e57b6d335a6b67b7402ff6")
        .errorTrackerService(ErrorTracker.contextAware())
        .metrics { factory ->
            factory
                .addMetric(Metric.number("worlds") { server.worlds.size })
                .addMetric(Metric.number("plugins") { server.pluginManager.plugins.size })
                .addMetric(Metric.number("players_online") { server.onlinePlayers.size })
                .create()
        }
        .create()

    override fun onEnable() {
        FXEngine.initialize(this)
        if (server.pluginManager.isPluginEnabled(PACKETEVENTS_PLUGIN)) {
            PacketDisplaysBootstrap.enable()
            logger.info("PacketEvents found: display models are sent to clients as packets.")
        }
        hookMythicMobs()
        applyConfig()
        yamlEffectLoader = YamlEffectLoader(this)
        yamlEffectLoader.ensureDirectories()
        saveBundledEffectExamples()
        context.ready()

        val loaded = yamlEffectLoader.loadAll()
        loadTriggers()
        server.pluginManager.registerEvents(TriggerListener(triggers), this)

        val cmd = getCommand("vengine")
            ?: error("Command 'vengine' missing from plugin.yml")
        val handler = VEngineCommand(this)
        cmd.setExecutor(handler)
        cmd.tabCompleter = handler
        placeholders.hook()

        logger.info("VEngine enabled with ${loaded.size} YAML effects and ${FXEngine.effectNames().size} total registry entries.")
    }

    override fun onDisable() {
        placeholders.shutdown()
        mythic?.shutdown()
        FXEngine.shutdown()
        if (server.pluginManager.isPluginEnabled(PACKETEVENTS_PLUGIN)) {
            PacketDisplaysBootstrap.disable()
        }
        context.shutdown()
    }

    fun reload() {
        applyConfig()
        val loaded = yamlEffectLoader.loadAll()
        logger.info("VEngine effects reloaded: ${loaded.size} YAML effects available.")
        loadTriggers()
    }

    // reads triggers.yml after the effects
    private fun loadTriggers() {
        saveIfMissing(TRIGGERS_FILE)
        val root = runCatching {
            dataFolder.resolve(TRIGGERS_FILE).inputStream().use { stream -> Yaml().load<Map<*, *>>(stream) }
        }.getOrElse { error ->
            logger.warning("Could not read $TRIGGERS_FILE, triggers are disabled: ${error.message}")
            null
        }
        val definitions = TriggerLoader(logger, mythicInstalled = mythic != null).parse(root.orEmpty())
        triggers.replace(definitions)
        logger.info("VEngine triggers loaded: ${definitions.size}.")
    }

    // VEngine starts before MythicMobs
    private fun hookMythicMobs() {
        if (server.pluginManager.getPlugin(MythicHook.PLUGIN_NAME) == null) {
            return
        }
        val hook = MythicHook(this)
        server.pluginManager.registerEvents(hook, this)
        triggers.mythic = hook
        mythic = hook
        logger.info("MythicMobs found: vengine and vengine-stop mechanics are available.")
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
        FXEngine.maxEmittedEffects = config.getInt(MAX_EMITTED_EFFECTS_CONFIG_KEY, FXEngine.DEFAULT_MAX_EMITTED_EFFECTS)
    }

    companion object {
        private const val VIEW_DISTANCE_CONFIG_KEY = "view-distance"
        private const val MAX_EMITTED_EFFECTS_CONFIG_KEY = "max-emitted-effects"
        private const val TRIGGERS_FILE = "triggers.yml"
        private const val PACKETEVENTS_PLUGIN = "packetevents"
    }

    private fun saveBundledEffectExamples() {
        saveIfMissing("effects/torus.yml")
        saveIfMissing("effects/cosmic_gate.yml")
        saveIfMissing("effects/rk4_comet.yml")
        saveIfMissing("effects/lissajous.yml")
        saveIfMissing("effects/rosette.yml")
        saveIfMissing("effects/torus_knot.yml")
        saveIfMissing("effects/helix.yml")
        saveIfMissing("effects/supershape.yml")
        saveIfMissing("effects/beam.yml")
        saveIfMissing("effects/spline_beam.yml")
        saveIfMissing("effects/crystal_ward.yml")
        saveIfMissing("effects/parametric_rose.yml")
        saveIfMissing("effects/parametric_torus.yml")
        saveIfMissing("effects/traveling_comet.yml")
    }

    private fun saveIfMissing(path: String) {
        val target = dataFolder.resolve(path)
        if (!target.exists()) {
            saveResource(path, false)
        }
    }
}
