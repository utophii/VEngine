package com.utophii.placeholders

import com.utophii.VEngine
import com.utophii.engine.PlaceholderResolver
import com.utophii.engine.Placeholders
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.event.server.PluginEnableEvent

// hooks PlaceholderAPI
class PlaceholderService(
    private val plugin: VEngine,
) : Listener {
    private var expansion: VEngineExpansion? = null

    fun hook() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        val placeholderApi = plugin.server.pluginManager.getPlugin(PLUGIN_NAME)
        when {
            placeholderApi == null ->
                plugin.logger.info("PlaceholderAPI is not installed; %vengine_*% placeholders are disabled.")
            placeholderApi.isEnabled -> register()
            else ->
                plugin.logger.info("PlaceholderAPI is installed and will be hooked when it enables.")
        }
    }

    fun shutdown() {
        Placeholders.resolver = null
        val current = expansion ?: return
        expansion = null
        if (!plugin.server.pluginManager.isPluginEnabled(PLUGIN_NAME)) {
            return
        }
        runCatching {
            if (current.isRegistered) {
                current.unregister()
            }
        }
    }

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name.equals(PLUGIN_NAME, ignoreCase = true)) {
            register()
        }
    }

    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin.name.equals(PLUGIN_NAME, ignoreCase = true)) {
            Placeholders.resolver = null
        }
    }

    private fun register() {
        if (!plugin.server.pluginManager.isPluginEnabled(PLUGIN_NAME)) {
            return
        }
        // effects read %placeholders% through PlaceholderAPI as well
        Placeholders.resolver = PapiResolver
        val current = expansion
        if (current != null && runCatching { current.isRegistered }.getOrDefault(false)) {
            return
        }
        val created = current ?: VEngineExpansion(plugin)
        if (created.register()) {
            expansion = created
            plugin.logger.info("Registered PlaceholderAPI expansion '${VEnginePlaceholders.IDENTIFIER}'.")
        } else {
            plugin.logger.warning("PlaceholderAPI is present, but the VEngine expansion failed to register.")
        }
    }

    private companion object {
        const val PLUGIN_NAME = "PlaceholderAPI"
    }
}

// only touched once PlaceholderAPI is enabled
private object PapiResolver : PlaceholderResolver {
    override fun resolve(player: Player?, placeholder: String): String = PlaceholderAPI.setPlaceholders(player, placeholder)
}
