package com.utophii.placeholders

import com.utophii.VEngine
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
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

    private fun register() {
        if (!plugin.server.pluginManager.isPluginEnabled(PLUGIN_NAME)) {
            return
        }
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
