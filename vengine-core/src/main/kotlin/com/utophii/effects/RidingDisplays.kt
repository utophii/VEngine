package com.utophii.effects

import com.utophii.api.DisplayOptions
import com.utophii.api.EffectOptions
import com.utophii.engine.FollowAnchor
import org.bukkit.Location
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// display models of one followed effect that ride its entity as client-side passengers
interface RidingDisplays {
    fun sync(positions: List<Location>, opts: EffectOptions, display: DisplayOptions, time: Double, anchor: FollowAnchor)

    fun clear()

    // virtual passenger ids this effect has shown to one viewer
    fun idsFor(viewer: UUID): IntArray
}

// switch and registry for packet-based riding displays; only active when the PacketEvents plugin is installed.
// this object never touches PacketEvents classes itself
object PacketDisplays {
    private val byVehicle = ConcurrentHashMap<Int, MutableSet<RidingDisplays>>()

    @Volatile
    private var factory: (() -> RidingDisplays)? = null

    val isEnabled: Boolean
        get() = factory != null

    // called by the PacketEvents bootstrap
    fun install(newFactory: (() -> RidingDisplays)?) {
        factory = newFactory
        if (newFactory == null) {
            byVehicle.clear()
        }
    }

    fun create(): RidingDisplays? = factory?.invoke()

    fun register(vehicleId: Int, displays: RidingDisplays) {
        byVehicle.computeIfAbsent(vehicleId) { ConcurrentHashMap.newKeySet() }.add(displays)
    }

    fun unregister(vehicleId: Int, displays: RidingDisplays) {
        byVehicle.computeIfPresent(vehicleId) { _, set -> set.remove(displays); set.ifEmpty { null } }
    }

    // every virtual passenger of the vehicle that the viewer currently sees, across all effects on it
    fun virtualPassengers(vehicleId: Int, viewer: UUID): IntArray {
        val set = byVehicle[vehicleId] ?: return EMPTY
        if (set.size == 1) {
            return set.first().idsFor(viewer)
        }
        return set.flatMap { displays -> displays.idsFor(viewer).asList() }.toIntArray()
    }

    private val EMPTY = IntArray(0)
}
