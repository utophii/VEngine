package com.utophii.triggers

import com.utophii.api.EffectOptions
import com.utophii.api.FollowOptions
import com.utophii.effects.ScriptedEffect
import com.utophii.engine.FXEngine
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

// plays the effects of triggers.yml when their events happen
class TriggerService(private val plugin: JavaPlugin) {
    private var byEvent: Map<TriggerEvent, List<TriggerDefinition>> = emptyMap()

    // "<trigger id>:<entity uuid>" -> server tick when the cooldown ends
    private val cooldowns = HashMap<String, Int>()

    fun replace(definitions: List<TriggerDefinition>) {
        byEvent = definitions.groupBy(TriggerDefinition::event)
        cooldowns.clear()
    }

    fun handles(event: TriggerEvent): Boolean = byEvent[event].orEmpty().isNotEmpty()

    // runs on the server thread for one Bukkit event
    fun fire(event: TriggerEvent, context: TriggerContext) {
        byEvent[event]?.forEach { definition ->
            if (!matches(definition, context)) {
                return@forEach
            }
            val placement = context.place(definition.target) ?: return@forEach
            if (definition.delayTicks > 0L) {
                plugin.server.scheduler.runTaskLater(plugin, Runnable { play(definition, placement, context.variables) }, definition.delayTicks)
            } else {
                play(definition, placement, context.variables)
            }
        }
    }

    // cheap filters first; cooldown last, so a filtered-out event never starts one
    private fun matches(definition: TriggerDefinition, context: TriggerContext): Boolean {
        if (definition.entities.isNotEmpty() && context.entityType !in definition.entities) return false
        if (definition.attackers.isNotEmpty() && context.attackerType !in definition.attackers) return false
        if (definition.causes.isNotEmpty() && context.cause !in definition.causes) return false
        if (definition.items.isNotEmpty() && context.item !in definition.items) return false
        if (definition.permission != null && context.player?.hasPermission(definition.permission) != true) return false
        if (definition.chance < 1.0 && ThreadLocalRandom.current().nextDouble() >= definition.chance) return false
        return definition.cooldownTicks <= 0L || acquireCooldown(definition, context.actor)
    }

    private fun acquireCooldown(definition: TriggerDefinition, actor: UUID): Boolean {
        val now = plugin.server.currentTick
        val key = "${definition.id}:$actor"
        val readyAt = cooldowns[key]
        if (readyAt != null && now < readyAt) {
            return false
        }
        if (cooldowns.size >= COOLDOWN_SWEEP_SIZE) {
            cooldowns.values.removeIf { it <= now }
        }
        cooldowns[key] = now + definition.cooldownTicks.toInt()
        return true
    }

    private fun play(definition: TriggerDefinition, placement: Placement, variables: Map<String, Double>) {
        val effect = FXEngine.effect(definition.effect) ?: return
        val entity = placement.entity
        if (entity != null && !entity.isValid) {
            return
        }
        val values = variables + (RANDOM_VARIABLE to ThreadLocalRandom.current().nextDouble())
        val builder = EffectOptions.builder()
        definition.scale?.evaluate(values)?.takeIf { it > 0.0 }?.let(builder::scale)
        // formula atoms last a single tick by default
        val duration = definition.duration?.evaluate(values)?.toLong()
            ?: DEFAULT_ATOM_DURATION_TICKS.takeIf { effect !is ScriptedEffect }
        duration?.let { builder.duration(it.coerceAtLeast(1L)) }
        if (definition.parameters.isNotEmpty()) {
            builder.parameters(definition.parameters.mapValues { (_, expression) -> expression.evaluate(values) })
        }

        // like /ve play: an effect with a follow: block sticks to the entity it was triggered on
        if (entity != null && effect.followDefaults != null) {
            effect.play(entity.location, builder.follow(FollowOptions(entity)).build())
        } else {
            effect.play(placement.location, builder.build())
        }
    }

    // where an effect plays, entity is null when the place must stay fixed
    data class Placement(val location: Location, val entity: Entity? = null)

    // one Bukkit event as triggers see it
    class TriggerContext(
        val actor: UUID,
        val player: Player?,
        val entityType: EntityType? = null,
        val attackerType: EntityType? = null,
        val cause: String? = null,
        val item: Material? = null,
        val variables: Map<String, Double> = emptyMap(),
        val place: (TriggerTarget) -> Placement?,
    )

    companion object {
        private const val RANDOM_VARIABLE = "random"
        private const val DEFAULT_ATOM_DURATION_TICKS = 60L
        private const val COOLDOWN_SWEEP_SIZE = 4096
    }
}
