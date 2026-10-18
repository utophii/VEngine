package com.utophii.mythic

import org.bukkit.Location
import org.bukkit.entity.Entity

// the part of MythicMobs that triggers use
interface MythicBridge {
    // internal name of a MythicMobs mob
    fun mobType(entity: Entity): String?

    // casts a MythicMobs skill
    fun castSkill(
        skill: String,
        caster: Entity,
        trigger: Entity?,
        origin: Location,
        targets: Collection<Entity>,
        locations: Collection<Location>,
    ): Boolean
}
