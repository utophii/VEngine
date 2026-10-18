package com.utophii.triggers

import com.utophii.engine.PlaceholderExpression
import org.bukkit.Material
import org.bukkit.entity.EntityType

// game events a trigger can react to, the first target is the default at:
enum class TriggerEvent(val key: String, val targets: List<TriggerTarget>) {
    HIT("hit", listOf(TriggerTarget.VICTIM, TriggerTarget.ATTACKER)),
    DEATH("death", listOf(TriggerTarget.VICTIM, TriggerTarget.KILLER)),
    JOIN("join", listOf(TriggerTarget.PLAYER)),
    TELEPORT("teleport", listOf(TriggerTarget.TO, TriggerTarget.FROM)),
    ITEM_USE("item_use", listOf(TriggerTarget.PLAYER, TriggerTarget.BLOCK));

    companion object {
        fun fromKey(key: String): TriggerEvent? = entries.firstOrNull { it.key.equals(key.trim().replace('-', '_'), ignoreCase = true) }
    }
}

// where a triggered effect plays
enum class TriggerTarget {
    VICTIM,
    ATTACKER,
    KILLER,
    PLAYER,
    FROM,
    TO,
    BLOCK,
}

// one entry of triggers.yml: plays effect and casts the MythicMobs skill whenever event happens and every filter matches
data class TriggerDefinition(
    val id: String,
    val event: TriggerEvent,
    val effect: String?,
    val target: TriggerTarget,
    val permission: String? = null,
    val cooldownTicks: Long = 0L,
    val chance: Double = 1.0,
    val delayTicks: Long = 0L,
    val entities: Set<EntityType> = emptySet(),
    val attackers: Set<EntityType> = emptySet(),
    val causes: Set<String> = emptySet(),
    val items: Set<Material> = emptySet(),
    // lowercase MythicMobs mob types of the victim/player and of the attacker/killer
    val mythic: Set<String> = emptySet(),
    val mythicAttackers: Set<String> = emptySet(),
    val skill: String? = null,
    val scale: PlaceholderExpression? = null,
    val duration: PlaceholderExpression? = null,
    val parameters: Map<String, PlaceholderExpression> = emptyMap(),
)
