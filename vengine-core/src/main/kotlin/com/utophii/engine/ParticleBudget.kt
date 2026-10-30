package com.utophii.engine

import com.utophii.math.Hash
import java.util.UUID
import kotlin.math.sqrt

// decides how many particles each player receives: a global and a per-player limit per tick,
// distance LOD and a quality level that drops while the server lags.
// Every particle index has a fixed rank in [0, 1) and is sent while its rank is below the keep fraction,
// so a lower fraction thins a shape evenly and the kept subset stays the same from frame to frame.
// Limits act on the previous ticks' demand, which keeps the check O(1) and fair to effects rendered last
class ParticleBudget {
    // particles per tick across all players, 0 = unlimited
    var globalLimit: Int = UNLIMITED
        set(value) { field = value.coerceAtLeast(UNLIMITED) }

    // particles per tick one player receives, 0 = unlimited
    var playerLimit: Int = UNLIMITED
        set(value) { field = value.coerceAtLeast(UNLIMITED) }

    // keep fraction by distance, interpolated linearly between steps, empty = no LOD
    var lod: List<LodStep> = emptyList()
        set(value) { field = value.sortedBy(LodStep::distance) }

    val quality = QualityControl()

    // particles sent during the last finished tick
    var sentLastTick: Int = 0
        private set

    private var sent = 0
    private var globalDemand = 0.0
    private var globalPeak = 0.0
    private var globalScale = FULL
    private val players = HashMap<UUID, PlayerLoad>()

    // closes the previous tick: its demand sets the scale of the next one
    fun startTick() {
        sentLastTick = sent
        sent = 0
        globalPeak = peak(globalPeak, globalDemand)
        globalScale = scale(globalLimit, globalPeak)
        globalDemand = 0.0
        val iterator = players.values.iterator()
        while (iterator.hasNext()) {
            val load = iterator.next()
            load.peak = peak(load.peak, load.demand)
            load.scale = scale(playerLimit, load.peak)
            load.demand = 0.0
            if (load.peak < FORGET_DEMAND) {
                iterator.remove()
            }
        }
    }

    // budget state of one receiving player
    fun load(player: UUID): PlayerLoad = players.getOrPut(player, ::PlayerLoad)

    // fraction of a particle at distanceSquared from the player that LOD and quality keep, before limits
    fun baseKeep(distanceSquared: Double, lodEnabled: Boolean): Double {
        val distance = if (lodEnabled) distanceKeep(distanceSquared) else FULL
        return distance * quality.keep
    }

    // records the demand of one budgeted particle and tells whether this player receives it
    fun admit(load: PlayerLoad, rank: Double, base: Double, count: Int): Boolean {
        val weight = base * count
        load.demand += weight
        globalDemand += weight
        return rank < base * minOf(globalScale, load.scale)
    }

    // counts particles that were actually sent
    fun sent(count: Int) {
        sent += count
    }

    // fraction the distance LOD alone keeps
    fun distanceKeep(distanceSquared: Double): Double {
        val steps = lod
        if (steps.isEmpty()) {
            return FULL
        }
        val first = steps[0]
        if (distanceSquared <= first.distance * first.distance) {
            return first.keep
        }
        for (index in 1 until steps.size) {
            val far = steps[index]
            if (distanceSquared <= far.distance * far.distance) {
                val near = steps[index - 1]
                val t = (sqrt(distanceSquared) - near.distance) / (far.distance - near.distance)
                return near.keep + (far.keep - near.keep) * t
            }
        }
        return steps.last().keep
    }

    // per-tick load of one receiving player
    class PlayerLoad internal constructor() {
        internal var demand = 0.0
        internal var peak = 0.0
        internal var scale = FULL
    }

    // keep fraction at a distance in blocks
    data class LodStep(val distance: Double, val keep: Double)

    // lowers the keep fraction while the average tick time stays above configured thresholds
    class QualityControl {
        var enabled: Boolean = false

        // thresholds in milliseconds per tick, sorted ascending
        var levels: List<QualityLevel> = emptyList()
            set(value) {
                field = value.sortedBy(QualityLevel::mspt)
                level = NO_LEVEL
            }

        // how far below a threshold the tick time must fall before quality steps back up
        var recoverMargin: Double = DEFAULT_RECOVER_MARGIN
            set(value) { field = value.coerceAtLeast(0.0) }

        // index of the active level, -1 = full quality
        var level: Int = NO_LEVEL
            private set

        val keep: Double
            get() = if (enabled && level >= 0) levels[level].keep else FULL

        // reacts to a fresh average tick time, returns true when the level changed
        fun update(mspt: Double): Boolean {
            val before = level
            if (!enabled) {
                level = NO_LEVEL
                return before != level
            }
            val target = levels.indexOfLast { mspt >= it.mspt }
            if (target > level) {
                level = target
            }
            while (level >= 0 && mspt < levels[level].mspt - recoverMargin) {
                level--
            }
            return before != level
        }
    }

    // keep fraction while the average tick time is at or above mspt
    data class QualityLevel(val mspt: Double, val keep: Double)

    companion object {
        const val UNLIMITED = 0
        const val DEFAULT_RECOVER_MARGIN = 3.0
        private const val FULL = 1.0
        private const val NO_LEVEL = -1
        // remembered demand fades by this factor per tick
        private const val PEAK_DECAY = 0.9
        private const val FORGET_DEMAND = 0.5

        // stable rank of a particle index in [0, 1)
        fun rank(index: Int): Double = Hash.unit(Hash.mix(index.toLong()))

        private fun peak(previous: Double, demand: Double): Double = maxOf(demand, previous * PEAK_DECAY)

        private fun scale(limit: Int, demand: Double): Double =
            if (limit == UNLIMITED || demand <= limit) FULL else limit / demand
    }
}
