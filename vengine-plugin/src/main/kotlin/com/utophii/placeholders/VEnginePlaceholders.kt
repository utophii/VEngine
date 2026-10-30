package com.utophii.placeholders

import java.util.Locale
import kotlin.math.roundToInt

object VEnginePlaceholders {
    const val IDENTIFIER = "vengine"
    const val LIST_SEPARATOR = ", "

    val documented: List<String> = listOf(
        "%${IDENTIFIER}_effects%",
        "%${IDENTIFIER}_effects_list%",
        "%${IDENTIFIER}_active%",
        "%${IDENTIFIER}_active_list%",
        "%${IDENTIFIER}_active_names%",
        "%${IDENTIFIER}_view_distance%",
        "%${IDENTIFIER}_nearest%",
        "%${IDENTIFIER}_nearest_id%",
        "%${IDENTIFIER}_nearest_distance%",
        "%${IDENTIFIER}_nearby%",
        "%${IDENTIFIER}_quality%",
        "%${IDENTIFIER}_particles_tick%",
    )

    // returns null when the token is unknown, or when a player-relative token is asked without a viewer
    fun resolve(params: String, snapshot: Snapshot, viewer: Viewer? = null): String? {
        val key = params.trim().lowercase()
        if (key.isEmpty()) {
            return null
        }
        return when (key) {
            EFFECTS -> snapshot.effectNames.size.toString()
            EFFECTS_LIST -> snapshot.effectNames.joinToString(LIST_SEPARATOR)
            ACTIVE -> snapshot.running.size.toString()
            ACTIVE_LIST -> snapshot.running
                .sortedBy { effect -> effect.id.lowercase() }
                .joinToString(LIST_SEPARATOR) { effect -> effect.id }
            ACTIVE_NAMES -> snapshot.running
                .distinctBy { effect -> effect.effectName.lowercase() }
                .sortedBy { effect -> effect.effectName.lowercase() }
                .joinToString(LIST_SEPARATOR) { effect -> effect.effectName }
            VIEW_DISTANCE -> formatNumber(snapshot.viewDistance)
            NEAREST -> playerValue(viewer) { nearest(snapshot, it)?.effectName ?: NONE }
            NEAREST_ID -> playerValue(viewer) { nearest(snapshot, it)?.id ?: NONE }
            NEAREST_DISTANCE -> playerValue(viewer) { current ->
                val closest = nearest(snapshot, current) ?: return@playerValue NONE
                formatNumber(distance(current, closest))
            }
            NEARBY -> playerValue(viewer) { nearbyCount(snapshot, it).toString() }
            QUALITY -> (snapshot.quality * PERCENT).roundToInt().toString()
            PARTICLES_TICK -> snapshot.particlesPerTick.toString()
            else -> null
        }
    }

    private fun nearest(snapshot: Snapshot, viewer: Viewer): Running? {
        return snapshot.running
            .filter { effect -> effect.worldName.equals(viewer.worldName, ignoreCase = true) }
            .minWithOrNull(compareBy<Running> { effect -> distanceSquared(viewer, effect) }.thenBy { effect -> effect.id.lowercase() })
    }

    private fun nearbyCount(snapshot: Snapshot, viewer: Viewer): Int {
        val radius = snapshot.viewDistance.coerceAtLeast(0.0)
        val radiusSquared = radius * radius
        return snapshot.running.count { effect ->
            effect.worldName.equals(viewer.worldName, ignoreCase = true) &&
                distanceSquared(viewer, effect) <= radiusSquared
        }
    }

    private fun distanceSquared(viewer: Viewer, effect: Running): Double {
        val dx = viewer.x - effect.x
        val dy = viewer.y - effect.y
        val dz = viewer.z - effect.z
        return dx * dx + dy * dy + dz * dz
    }

    private fun distance(viewer: Viewer, effect: Running): Double {
        return kotlin.math.sqrt(distanceSquared(viewer, effect))
    }

    // player-relative tokens stay unresolved (null) when there is no viewer, instead of pretending the value is zero
    private inline fun playerValue(viewer: Viewer?, block: (Viewer) -> String): String? {
        return if (viewer == null) null else block(viewer)
    }

    // one decimal, US separators, trailing .0 dropped so a default view distance reads as 64 rather than 64.0
    private fun formatNumber(value: Double): String {
        if (!value.isFinite()) {
            return NONE
        }
        val text = String.format(Locale.US, ONE_DECIMAL, value)
        return if (text.endsWith(TRAILING_ZERO)) text.dropLast(TRAILING_ZERO.length) else text
    }

    data class Snapshot(
        val effectNames: List<String>,
        val running: List<Running>,
        val viewDistance: Double,
        // auto-quality keep fraction, 1.0 = full
        val quality: Double = 1.0,
        val particlesPerTick: Int = 0,
    )

    data class Running(
        val id: String,
        val effectName: String,
        val worldName: String,
        val x: Double,
        val y: Double,
        val z: Double,
    )

    data class Viewer(
        val worldName: String,
        val x: Double,
        val y: Double,
        val z: Double,
    )

    private const val NONE = ""
    private const val EFFECTS = "effects"
    private const val EFFECTS_LIST = "effects_list"
    private const val ACTIVE = "active"
    private const val ACTIVE_LIST = "active_list"
    private const val ACTIVE_NAMES = "active_names"
    private const val VIEW_DISTANCE = "view_distance"
    private const val NEAREST = "nearest"
    private const val NEAREST_ID = "nearest_id"
    private const val NEAREST_DISTANCE = "nearest_distance"
    private const val NEARBY = "nearby"
    private const val QUALITY = "quality"
    private const val PARTICLES_TICK = "particles_tick"
    private const val PERCENT = 100
    private const val ONE_DECIMAL = "%.1f"
    private const val TRAILING_ZERO = ".0"
}
