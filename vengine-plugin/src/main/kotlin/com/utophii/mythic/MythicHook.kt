package com.utophii.mythic

import io.lumine.mythic.bukkit.MythicBukkit
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

// MythicMobs asks for mechanics every time it loads skills
class MythicHook(private val plugin: JavaPlugin) : Listener, MythicBridge {
    private val tracker = MythicEffectTracker()

    @EventHandler
    fun onMechanicLoad(event: MythicMechanicLoadEvent) {
        val name = event.mechanicName.lowercase()
        val container = event.container
        val line = event.config.line
        when (name) {
            in PLAY_NAMES -> event.register(VEnginePlayMechanic(container.manager, container.file, line, event.config, tracker))
            in STOP_NAMES -> event.register(VEngineStopMechanic(container.manager, container.file, line, event.config, tracker))
        }
    }

    override fun mobType(entity: Entity): String? {
        if (!plugin.server.pluginManager.isPluginEnabled(PLUGIN_NAME)) {
            return null
        }
        return MythicBukkit.inst().mobManager.getActiveMob(entity.uniqueId).orElse(null)?.mobType
    }

    override fun castSkill(
        skill: String,
        caster: Entity,
        trigger: Entity?,
        origin: Location,
        targets: Collection<Entity>,
        locations: Collection<Location>,
    ): Boolean {
        if (!plugin.server.pluginManager.isPluginEnabled(PLUGIN_NAME)) {
            return false
        }
        return MythicBukkit.inst().apiHelper.castSkill(caster, skill, trigger, origin, targets, locations, 1.0f)
    }

    fun shutdown() {
        tracker.clear()
    }

    companion object {
        const val PLUGIN_NAME = "MythicMobs"

        private val PLAY_NAMES = setOf("vengine", "vfx")
        private val STOP_NAMES = setOf("vengine-stop", "venginestop", "vfx-stop")
    }
}
