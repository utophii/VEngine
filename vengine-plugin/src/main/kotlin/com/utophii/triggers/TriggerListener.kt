package com.utophii.triggers

import com.utophii.triggers.TriggerService.Placement
import com.utophii.triggers.TriggerService.TriggerContext
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerTeleportEvent

// translates Bukkit events into trigger contexts. MONITOR so effects only follow outcomes other plugins kept.
// Permission and cooldown belong to the actor: the attacker or killer when it is a player, otherwise the victim
class TriggerListener(private val service: TriggerService) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHit(event: EntityDamageByEntityEvent) {
        if (!service.handles(TriggerEvent.HIT)) return
        val victim = event.entity as? LivingEntity ?: return
        // projectiles and other indirect damage count as their shooter
        val attacker = event.damageSource.causingEntity ?: event.damager
        val actor = attacker as? Player ?: victim
        service.fire(
            TriggerEvent.HIT,
            TriggerContext(
                actor = actor.uniqueId,
                player = actor as? Player,
                entityType = victim.type,
                attackerType = attacker.type,
                cause = event.cause.name,
                variables = mapOf(
                    DAMAGE to event.finalDamage,
                    HEALTH to victim.health,
                    MAX_HEALTH to maxHealth(victim),
                ),
            ) { target ->
                when (target) {
                    TriggerTarget.ATTACKER -> Placement(attacker.location, attacker)
                    else -> Placement(victim.location, victim)
                }
            },
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        if (!service.handles(TriggerEvent.DEATH)) return
        val victim = event.entity
        val killer = event.damageSource.causingEntity
        val actor = killer as? Player ?: victim
        service.fire(
            TriggerEvent.DEATH,
            TriggerContext(
                actor = actor.uniqueId,
                player = actor as? Player,
                entityType = victim.type,
                attackerType = killer?.type,
                cause = victim.lastDamageCause?.cause?.name,
                variables = mapOf(MAX_HEALTH to maxHealth(victim)),
            ) { target ->
                when (target) {
                    TriggerTarget.KILLER -> killer?.let { Placement(it.location, it) }
                    // the body is gone, so the effect stays where it died
                    else -> Placement(victim.location)
                }
            },
        )
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        if (!service.handles(TriggerEvent.JOIN)) return
        val player = event.player
        service.fire(
            TriggerEvent.JOIN,
            TriggerContext(actor = player.uniqueId, player = player, entityType = player.type) {
                Placement(player.location, player)
            },
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        if (!service.handles(TriggerEvent.TELEPORT)) return
        val player = event.player
        val from = event.from.clone()
        val to = event.to.clone()
        val distance = if (from.world == to.world) from.distance(to) else 0.0
        service.fire(
            TriggerEvent.TELEPORT,
            TriggerContext(
                actor = player.uniqueId,
                player = player,
                entityType = player.type,
                cause = event.cause.name,
                variables = mapOf(DISTANCE to distance),
            ) { target ->
                when (target) {
                    TriggerTarget.FROM -> Placement(from)
                    else -> Placement(to, player)
                }
            },
        )
    }

    // right-click use only, air clicks arrive pre-cancelled
    @EventHandler(priority = EventPriority.MONITOR)
    fun onItemUse(event: PlayerInteractEvent) {
        if (!service.handles(TriggerEvent.ITEM_USE)) return
        if (event.action != Action.RIGHT_CLICK_AIR && event.action != Action.RIGHT_CLICK_BLOCK) return
        if (event.useItemInHand() == Event.Result.DENY) return
        val item = event.item ?: return
        val player = event.player
        val block = event.clickedBlock
        service.fire(
            TriggerEvent.ITEM_USE,
            TriggerContext(actor = player.uniqueId, player = player, entityType = player.type, item = item.type) { target ->
                when (target) {
                    // on top of the clicked block
                    TriggerTarget.BLOCK -> block?.let { Placement(it.location.add(0.5, 1.0, 0.5)) }
                    else -> Placement(player.location, player)
                }
            },
        )
    }

    private fun maxHealth(entity: LivingEntity): Double =
        entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: entity.health

    companion object {
        private const val DAMAGE = "damage"
        private const val HEALTH = "health"
        private const val MAX_HEALTH = "max_health"
        private const val DISTANCE = "distance"
    }
}
