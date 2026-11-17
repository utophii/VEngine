package com.utophii.effects

import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

class YamlEffectGoldenTest {
    @TestFactory
    fun `YAML frames match committed goldens`() = YamlEffectFixtures.names.map { name ->
        dynamicTest(name) {
            val effect = YamlEffectFixtures.load(name)
            val golden = YamlEffectFixtures.readGolden(name)
            // revisit old inputs after overrides to catch stale cached parameters and appearance
            for (input in YamlEffectFixtures.inputs + YamlEffectFixtures.inputs.reversed()) {
                YamlEffectFixtures.assertFrame(golden.getValue(input.name),
                    YamlEffectFixtures.capture(effect, input), "$name/${input.name}")
            }
        }
    }
}