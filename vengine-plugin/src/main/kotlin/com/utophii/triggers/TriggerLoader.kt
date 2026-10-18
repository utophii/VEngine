package com.utophii.triggers

import com.utophii.engine.FXEngine
import com.utophii.engine.PlaceholderExpression
import org.bukkit.Material
import org.bukkit.entity.EntityType
import java.util.logging.Logger

// parses triggers.yml: every top-level key is a trigger id
class TriggerLoader(private val logger: Logger, private val mythicInstalled: Boolean = false) {

    fun parse(root: Map<*, *>): List<TriggerDefinition> =
        root.mapNotNull { (key, value) ->
            val id = key?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val node = value as? Map<*, *> ?: run {
                logger.warning("Skipping trigger '$id': expected a section.")
                return@mapNotNull null
            }
            parseTrigger(id, node)
        }

    private fun parseTrigger(id: String, node: Map<*, *>): TriggerDefinition? {
        // YAML 1.1 reads a bare on key as boolean true
        val eventName = (node[EVENT_KEY] ?: node[ON_KEY] ?: node[true])?.toString()
        val event = eventName?.let(TriggerEvent::fromKey) ?: run {
            logger.warning(
                "Skipping trigger '$id': unknown or missing 'event: $eventName'; expected one of " +
                    TriggerEvent.entries.joinToString { it.key } + ".",
            )
            return null
        }
        val effect = node[EFFECT_KEY]?.toString()?.trim()?.lowercase()?.takeIf(String::isNotBlank)
        val skill = node[SKILL_KEY]?.toString()?.trim()?.takeIf(String::isNotBlank)
        if (effect == null && skill == null) {
            logger.warning("Skipping trigger '$id': missing 'effect' or 'skill'.")
            return null
        }
        if (effect != null && FXEngine.effect(effect) == null) {
            logger.warning("Trigger '$id' plays unknown effect '$effect'; it will do nothing until that effect exists.")
        }

        val filter = node[FILTER_KEY] as? Map<*, *> ?: emptyMap<Any, Any>()
        val mythic = names(filter[MYTHIC_KEY]).mapTo(linkedSetOf(), String::lowercase)
        val mythicAttackers = names(filter[MYTHIC_ATTACKER_KEY]).mapTo(linkedSetOf(), String::lowercase)
        if (!mythicInstalled && (skill != null || mythic.isNotEmpty() || mythicAttackers.isNotEmpty())) {
            logger.warning("Trigger '$id' uses MythicMobs, which is not installed; its skill and mythic filters do nothing.")
        }
        val options = node[OPTIONS_KEY] as? Map<*, *> ?: emptyMap<Any, Any>()
        return TriggerDefinition(
            id = id,
            event = event,
            effect = effect,
            target = parseTarget(id, event, node[AT_KEY]),
            permission = when (val permission = node[PERMISSION_KEY]) {
                true -> "$PERMISSION_PREFIX${id.lowercase()}"
                null, false -> null
                else -> permission.toString().takeIf(String::isNotBlank)
            },
            cooldownTicks = node[COOLDOWN_KEY].number()?.toLong()?.coerceAtLeast(0L) ?: 0L,
            chance = node[CHANCE_KEY].number()?.coerceIn(0.0, 1.0) ?: 1.0,
            delayTicks = node[DELAY_KEY].number()?.toLong()?.coerceAtLeast(0L) ?: 0L,
            entities = parseEnums(id, filter[ENTITY_KEY], "entity type") { EntityType.valueOf(it) },
            attackers = parseEnums(id, filter[ATTACKER_KEY], "entity type") { EntityType.valueOf(it) },
            causes = names(filter[CAUSE_KEY]).toSet(),
            items = parseEnums(id, filter[ITEM_KEY], "item") { Material.matchMaterial(it) ?: error(it) },
            mythic = mythic,
            mythicAttackers = mythicAttackers,
            skill = skill,
            scale = parseExpression(id, SCALE_KEY, options[SCALE_KEY]),
            duration = parseExpression(id, DURATION_KEY, options[DURATION_KEY]),
            parameters = (options[PARAMETERS_KEY] as? Map<*, *>).orEmpty().mapNotNull { (name, value) ->
                val key = name?.toString()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                parseExpression(id, "$PARAMETERS_KEY.$key", value)?.let { key to it }
            }.toMap(),
        )
    }

    private fun parseTarget(id: String, event: TriggerEvent, value: Any?): TriggerTarget {
        val name = value?.toString()?.trim() ?: return event.targets.first()
        return event.targets.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: run {
            logger.warning(
                "Trigger '$id': 'at: $name' is not valid for '${event.key}'; expected one of " +
                    event.targets.joinToString { it.name.lowercase() } + ". Using '${event.targets.first().name.lowercase()}'.",
            )
            event.targets.first()
        }
    }

    // numbers and formulas share one path: a plain number is a constant expression
    private fun parseExpression(id: String, key: String, value: Any?): PlaceholderExpression? {
        val formula = value?.toString()?.takeIf(String::isNotBlank) ?: return null
        return PlaceholderExpression.compile(formula) ?: run {
            logger.warning("Trigger '$id': invalid expression for '$key': $formula")
            null
        }
    }

    private fun <T> parseEnums(id: String, value: Any?, what: String, parse: (String) -> T): Set<T> =
        names(value).mapNotNullTo(linkedSetOf()) { name ->
            runCatching { parse(name) }.getOrElse {
                logger.warning("Trigger '$id': unknown $what '$name'.")
                null
            }
        }

    // a single value or a list, normalized to enum-style names
    private fun names(value: Any?): List<String> = when (value) {
        null -> emptyList()
        is List<*> -> value.mapNotNull { it?.toString() }
        else -> listOf(value.toString())
    }.map { it.trim().uppercase() }.filter(String::isNotEmpty)

    private fun Any?.number(): Double? = when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }

    companion object {
        const val PERMISSION_PREFIX = "vengine.trigger."

        private const val EVENT_KEY = "event"
        private const val ON_KEY = "on"
        private const val EFFECT_KEY = "effect"
        private const val AT_KEY = "at"
        private const val PERMISSION_KEY = "permission"
        private const val COOLDOWN_KEY = "cooldown"
        private const val CHANCE_KEY = "chance"
        private const val DELAY_KEY = "delay"
        private const val FILTER_KEY = "filter"
        private const val ENTITY_KEY = "entity"
        private const val ATTACKER_KEY = "attacker"
        private const val CAUSE_KEY = "cause"
        private const val ITEM_KEY = "item"
        private const val MYTHIC_KEY = "mythic"
        private const val MYTHIC_ATTACKER_KEY = "mythic_attacker"
        private const val SKILL_KEY = "skill"
        private const val OPTIONS_KEY = "options"
        private const val SCALE_KEY = "scale"
        private const val DURATION_KEY = "duration"
        private const val PARAMETERS_KEY = "parameters"
    }
}
