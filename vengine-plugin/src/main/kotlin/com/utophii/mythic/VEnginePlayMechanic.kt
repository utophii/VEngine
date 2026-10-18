package com.utophii.mythic

import com.utophii.api.EffectOptions
import com.utophii.api.FollowOptions
import com.utophii.api.ParticleEffect
import com.utophii.effects.ScriptedEffect
import com.utophii.engine.FXEngine
import io.lumine.mythic.api.adapters.AbstractEntity
import io.lumine.mythic.api.adapters.AbstractLocation
import io.lumine.mythic.api.config.MythicLineConfig
import io.lumine.mythic.api.skills.ITargetedEntitySkill
import io.lumine.mythic.api.skills.ITargetedLocationSkill
import io.lumine.mythic.api.skills.SkillMetadata
import io.lumine.mythic.api.skills.SkillResult
import io.lumine.mythic.api.skills.placeholders.PlaceholderDouble
import io.lumine.mythic.api.skills.placeholders.PlaceholderString
import io.lumine.mythic.bukkit.BukkitAdapter
import io.lumine.mythic.core.skills.SkillExecutor
import io.lumine.mythic.core.skills.SkillMechanic
import io.lumine.mythic.core.skills.placeholders.PlaceholderContext
import org.bukkit.Location
import org.bukkit.entity.Player
import java.io.File

// - vengine{effect=torus;scale=2;duration=60;p.radius=3} @target
// plays a VEngine effect at every target of the line. Entity targets are followed when the effect has a
// follow: block, follow=false keeps it where the entity stood
class VEnginePlayMechanic(
    executor: SkillExecutor,
    file: File?,
    line: String,
    config: MythicLineConfig,
    private val tracker: MythicEffectTracker,
) : SkillMechanic(executor, file, line, config), ITargetedEntitySkill, ITargetedLocationSkill {
    private val effect: PlaceholderString = config.getPlaceholderString(arrayOf("effect", "e"), "")
    private val scale: PlaceholderDouble? = config.getString(arrayOf("scale", "s"))?.let(PlaceholderDouble::of)
    private val duration: PlaceholderDouble? = config.getString(arrayOf("duration", "d"))?.let(PlaceholderDouble::of)
    private val follow: Boolean = config.getBoolean(arrayOf("follow", "f"), true)
    private val parameters: Map<String, PlaceholderDouble> = parseParameters(config)

    init {
        // effects touch entities and the Bukkit scheduler
        setAsyncSafe(false)
    }

    override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
        val entity = BukkitAdapter.adapt(target) ?: return SkillResult.INVALID_TARGET
        if (!entity.isValid) {
            return SkillResult.INVALID_TARGET
        }
        val context = placeholderContext(data, target, target.location)
        val effect = resolve(effect.get(context)) ?: return SkillResult.INVALID_CONFIG
        val builder = options(effect, data, entity as? Player, context)
        // like /ve play: an effect with a follow: block sticks to the entity it was played on
        val handle = if (follow && effect.followDefaults != null) {
            effect.play(entity.location, builder.follow(FollowOptions(entity)).build())
        } else {
            effect.play(entity.location, builder.build())
        }
        tracker.track(handle, listOfNotNull(data.caster.entity?.uniqueId, entity.uniqueId).distinct())
        return SkillResult.SUCCESS
    }

    override fun castAtLocation(data: SkillMetadata, target: AbstractLocation): SkillResult {
        val location: Location = BukkitAdapter.adapt(target)
        if (location.world == null) {
            return SkillResult.INVALID_TARGET
        }
        val context = placeholderContext(data, null, target)
        val effect = resolve(effect.get(context)) ?: return SkillResult.INVALID_CONFIG
        val handle = effect.play(location, options(effect, data, null, context).build())
        data.caster.entity?.uniqueId?.let { tracker.track(handle, listOf(it)) }
        return SkillResult.SUCCESS
    }

    private fun resolve(name: String?): ParticleEffect? =
        name?.trim()?.takeIf(String::isNotEmpty)?.let(FXEngine::effect)

    private fun options(
        effect: ParticleEffect,
        data: SkillMetadata,
        targetPlayer: Player?,
        context: PlaceholderContext,
    ): EffectOptions.Builder {
        val value = { placeholder: PlaceholderDouble -> placeholder.get(context) }
        // PlaceholderAPI values inside the effect are read for the target player
        val subject = targetPlayer ?: data.caster.entity?.bukkitEntity as? Player
        val builder = EffectOptions.builder().subject(subject)
        scale?.let(value)?.takeIf { it > 0.0 }?.let(builder::scale)
        // formula atoms last a single tick by default
        val ticks = duration?.let(value)?.toLong() ?: DEFAULT_ATOM_DURATION_TICKS.takeIf { effect !is ScriptedEffect }
        ticks?.let { builder.duration(it.coerceAtLeast(1L)) }
        if (parameters.isNotEmpty()) {
            builder.parameters(parameters.mapValues { (_, placeholder) -> value(placeholder) })
        }
        return builder
    }

    private companion object {
        const val PARAMETER_PREFIX = "p."
        const val DEFAULT_ATOM_DURATION_TICKS = 60L

        // MythicMobs lowercases keys, the raw line still has the case effect parameters are written in
        val PARAMETER_KEY = Regex("""[{;]\s*p\.([A-Za-z0-9_]+)\s*=""")

        fun parseParameters(config: MythicLineConfig): Map<String, PlaceholderDouble> {
            val originalNames = PARAMETER_KEY.findAll(config.line).associate { match ->
                val name = match.groupValues[1]
                name.lowercase() to name
            }
            return config.entrySet()
                .filter { (key, _) -> key.startsWith(PARAMETER_PREFIX) && key.length > PARAMETER_PREFIX.length }
                .associate { (key, raw) ->
                    val name = key.removePrefix(PARAMETER_PREFIX)
                    (originalNames[name] ?: name) to PlaceholderDouble.of(raw)
                }
        }
    }
}

// what MythicMobs placeholders like <target.name> or <caster.level> are read against
internal fun placeholderContext(data: SkillMetadata, entity: AbstractEntity?, location: AbstractLocation): PlaceholderContext =
    PlaceholderContext.builder().meta(data).caster(data.caster).entity(entity).location(location).build()
