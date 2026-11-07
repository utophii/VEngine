package com.utophii.effects

import com.utophii.config.YamlEffectLoader
import java.io.File
import java.util.logging.Logger

internal object ProceduralFixtures {
    fun effect(name: String): AbstractParticleEffect =
        YamlEffectLoader(File("src/test/resources"), Logger.getAnonymousLogger())
            .load(File("src/test/resources/recipes/$name.yml")) as AbstractParticleEffect
}
