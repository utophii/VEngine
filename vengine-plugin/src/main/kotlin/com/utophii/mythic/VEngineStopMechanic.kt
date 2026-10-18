package com.utophii.mythic

import io.lumine.mythic.api.adapters.AbstractEntity
import io.lumine.mythic.api.config.MythicLineConfig
import io.lumine.mythic.api.skills.ITargetedEntitySkill
import io.lumine.mythic.api.skills.SkillMetadata
import io.lumine.mythic.api.skills.SkillResult
import io.lumine.mythic.api.skills.placeholders.PlaceholderString
import io.lumine.mythic.core.skills.SkillExecutor
import io.lumine.mythic.core.skills.SkillMechanic
import java.io.File

// - vengine-stop{effect=torus} @self
// stops the effects the vengine mechanic played on the target or that the target cast
class VEngineStopMechanic(
    executor: SkillExecutor,
    file: File?,
    line: String,
    config: MythicLineConfig,
    private val tracker: MythicEffectTracker,
) : SkillMechanic(executor, file, line, config), ITargetedEntitySkill {
    private val effect: PlaceholderString? = config.getString(arrayOf("effect", "e"))?.let(PlaceholderString::of)

    init {
        setAsyncSafe(false)
    }

    override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
        val name = effect?.get(placeholderContext(data, target, target.location))?.trim()?.takeIf(String::isNotEmpty)
        tracker.stop(target.uniqueId, name)
        return SkillResult.SUCCESS
    }
}
