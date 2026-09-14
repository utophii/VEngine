package com.utophii.modifiers

import com.utophii.api.EffectModifier
import com.utophii.math.EasingType
import org.bukkit.Location
import org.bukkit.util.Vector

// translates the whole rendered effect along a waypoint path over time
// offsets are relative to the effect's spawn point: the displacement at time 0 is always (0, 0, 0),
// so the effect starts exactly where it was spawned and then glides through the legs
// because this is an EffectModifier, it works with every engine primitive and scripted layer
class MotionModifier(
    val legs: List<MotionLeg>,
    val mode: Mode = Mode.HOLD,
) : EffectModifier {

    init {
        require(legs.isNotEmpty()) { "MotionModifier requires at least one path leg" }
        legs.forEach { leg ->
            require(leg.ticks > 0.0) { "Motion leg duration must be positive ticks" }
        }
    }

    // one path segment: travel from the previous waypoint to offset within ticks using easing
    data class MotionLeg(
        val offset: Vector,
        val ticks: Double,
        val easing: EasingType = DEFAULT_EASING,
    )

    // what the effect does once it reaches the last waypoint
    enum class Mode {
        // stays at the final waypoint until the effect ends
        HOLD,

        // jumps back to the spawn point and replays the path while the effect is running
        LOOP,

        // travels the path forwards and backwards alternately
        PING_PONG,
    }

    // total one-way travel time of the whole path in ticks
    val totalTicks: Double = legs.sumOf { it.ticks }

    // shifts every particle by the current path displacement; allocation-free on the render hot path
    override fun modify(loc: Location, time: Double): Location {
        val pathTime = resolvePathTime(time)
        val finished = !walkLegs(pathTime) { fromX, fromY, fromZ, leg, eased ->
            loc.x += fromX + (leg.offset.x - fromX) * eased
            loc.y += fromY + (leg.offset.y - fromY) * eased
            loc.z += fromZ + (leg.offset.z - fromZ) * eased
        }
        if (finished) {
            val last = legs.last().offset
            loc.x += last.x
            loc.y += last.y
            loc.z += last.z
        }
        return loc
    }

    // displacement from the spawn point at the given effect time; allocates, intended for tests and tooling
    fun displacementAt(time: Double): Vector {
        val pathTime = resolvePathTime(time)
        var result = legs.last().offset.clone()
        walkLegs(pathTime) { fromX, fromY, fromZ, leg, eased ->
            result = Vector(
                fromX + (leg.offset.x - fromX) * eased,
                fromY + (leg.offset.y - fromY) * eased,
                fromZ + (leg.offset.z - fromZ) * eased,
            )
        }
        return result
    }

    // maps effect time to one-way path time according to the playback mode
    private fun resolvePathTime(time: Double): Double {
        if (time <= 0.0) {
            return 0.0
        }
        return when (mode) {
            Mode.HOLD -> time.coerceAtMost(totalTicks)
            Mode.LOOP -> positiveModulo(time, totalTicks)
            Mode.PING_PONG -> {
                val cycle = totalTicks * PING_PONG_FACTOR
                val phase = positiveModulo(time, cycle)
                if (phase <= totalTicks) phase else cycle - phase
            }
        }
    }

    // walks the legs at pathTime; invokes visit with the previous waypoint and eased progress, returns false past the end
    private inline fun walkLegs(pathTime: Double, visit: (fromX: Double, fromY: Double, fromZ: Double, leg: MotionLeg, eased: Double) -> Unit): Boolean {
        var elapsed = 0.0
        var fromX = 0.0
        var fromY = 0.0
        var fromZ = 0.0
        for (leg in legs) {
            if (pathTime <= elapsed + leg.ticks) {
                val progress = ((pathTime - elapsed) / leg.ticks).coerceIn(0.0, 1.0)
                visit(fromX, fromY, fromZ, leg, leg.easing.apply(progress))
                return true
            }
            elapsed += leg.ticks
            fromX = leg.offset.x
            fromY = leg.offset.y
            fromZ = leg.offset.z
        }
        return false
    }

    private fun positiveModulo(value: Double, period: Double): Double {
        val result = value % period
        return if (result < 0.0) result + period else result
    }

    companion object {
        val DEFAULT_EASING: EasingType = EasingType.EASE_IN_OUT_SINE
        private const val PING_PONG_FACTOR = 2.0

        // single-leg convenience: glide from the spawn point to offset within ticks
        fun to(
            offset: Vector,
            ticks: Long,
            easing: EasingType = DEFAULT_EASING,
            mode: Mode = Mode.HOLD,
        ): MotionModifier = MotionModifier(listOf(MotionLeg(offset.clone(), ticks.toDouble(), easing)), mode)
    }
}
