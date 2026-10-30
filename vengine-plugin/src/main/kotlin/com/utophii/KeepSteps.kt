package com.utophii

import org.bukkit.configuration.ConfigurationSection

// reads `number: fraction` pairs such as `16: 1.0` or `47.5: 0.5` from a config section
object KeepSteps {
    // Bukkit splits keys on dots, so `47.5: 0.5` loads as section `47` holding `5: 0.5`;
    // walking the leaves deeply joins such keys back into their full number
    fun parse(section: ConfigurationSection, onInvalid: (String) -> Unit): List<Pair<Double, Double>> {
        return section.getValues(true)
            .filterValues { it !is ConfigurationSection }
            .mapNotNull { (key, value) ->
                val threshold = key.toDoubleOrNull()
                val keep = value?.toString()?.toDoubleOrNull()
                if (threshold == null || threshold < 0.0 || keep == null) {
                    onInvalid(key)
                    return@mapNotNull null
                }
                threshold to keep.coerceIn(0.0, 1.0)
            }
    }
}
