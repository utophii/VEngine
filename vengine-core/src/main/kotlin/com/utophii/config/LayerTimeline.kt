package com.utophii.config

// resolves after: references between layers into absolute start ticks at load time
internal object LayerTimeline {

    // the timing keys of one layer as written in YAML
    data class Timing(
        val id: String?,
        val start: Long,
        val duration: Long?,
        val after: String?,
        val delay: Long,
    )

    // start tick per layer in input order
    data class Resolved(
        val starts: List<Long?>,
        val problems: List<String>,
    )

    // a layer without its own duration lasts until the effect ends, so after: such a layer starts at effectDuration
    fun resolve(timings: List<Timing>, effectDuration: Long): Resolved {
        val problems = mutableListOf<String>()
        val indexById = mutableMapOf<String, Int>()
        timings.forEachIndexed { index, timing ->
            val id = timing.id ?: return@forEachIndexed
            if (indexById.putIfAbsent(id, index) != null) {
                problems += "duplicate layer id '$id'; `after:` refers to the first one"
            }
        }

        val starts = arrayOfNulls<Long>(timings.size)
        val resolved = BooleanArray(timings.size)
        val visiting = BooleanArray(timings.size)

        fun startOf(index: Int): Long? {
            if (resolved[index]) {
                return starts[index]
            }
            val timing = timings[index]
            val after = timing.after
            val start = if (after == null) {
                timing.start
            } else if (visiting[index]) {
                problems += "layer '${timing.id ?: "#${index + 1}"}' is part of an `after:` cycle"
                null
            } else {
                visiting[index] = true
                val target = indexById[after]
                val value = if (target == null) {
                    problems += "layer '${timing.id ?: "#${index + 1}"}' refers to unknown layer '$after'"
                    null
                } else {
                    startOf(target)?.let { targetStart -> endOf(timings[target], targetStart, effectDuration) + timing.delay }
                }
                visiting[index] = false
                value
            }
            starts[index] = start
            resolved[index] = true
            return start
        }

        timings.indices.forEach(::startOf)
        return Resolved(starts.toList(), problems)
    }

    private fun endOf(timing: Timing, start: Long, effectDuration: Long): Long =
        timing.duration?.let { start + it } ?: effectDuration.coerceAtLeast(start)
}
