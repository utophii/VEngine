package com.utophii.placeholders

import com.utophii.VEngine
import com.utophii.engine.FXEngine
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.logging.Level

// internal PlaceholderAPI expansion
class VEngineExpansion(
    private val plugin: VEngine,
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = VEnginePlaceholders.IDENTIFIER

    override fun getAuthor(): String {
        val authors = plugin.pluginMeta.authors
        return if (authors.isEmpty()) DEFAULT_AUTHOR else authors.joinToString(", ")
    }

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun getPlaceholders(): List<String> = VEnginePlaceholders.documented

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        return try {
            VEnginePlaceholders.resolve(params, snapshot(), viewerOf(player))
        } catch (failure: Throwable) {
            plugin.logger.log(Level.WARNING, "Failed to resolve %${identifier}_$params%", failure)
            null
        }
    }

    private fun snapshot(): VEnginePlaceholders.Snapshot {
        return VEnginePlaceholders.Snapshot(
            effectNames = FXEngine.effectNames(),
            running = FXEngine.runningEffects().map { effect ->
                VEnginePlaceholders.Running(
                    id = effect.id,
                    effectName = effect.effectName,
                    worldName = effect.worldName,
                    x = effect.x,
                    y = effect.y,
                    z = effect.z,
                )
            },
            viewDistance = FXEngine.viewDistance(),
        )
    }

    // location reads are main-thread only. async scoreboard plugins still get the global tokens
    private fun viewerOf(player: OfflinePlayer?): VEnginePlaceholders.Viewer? {
        val online = player?.player ?: return null
        if (!online.isOnline || !Bukkit.isPrimaryThread()) {
            return null
        }
        val location = online.location
        val worldName = location.world?.name ?: return null
        return VEnginePlaceholders.Viewer(worldName, location.x, location.y, location.z)
    }

    private companion object {
        const val DEFAULT_AUTHOR = "utophii"
    }
}
