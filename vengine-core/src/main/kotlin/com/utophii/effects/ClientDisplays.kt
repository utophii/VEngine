package com.utophii.effects

import com.utophii.api.DisplayOptions
import com.utophii.api.EffectOptions
import com.utophii.engine.FollowAnchor
import org.bukkit.Location
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// display models of one running effect that exist only on the clients.
// A followed effect's models ride its entity as passengers
interface ClientDisplays {
    // colors, when given, holds one ARGB color per position for PIXEL models
    fun sync(positions: List<Location>, opts: EffectOptions, display: DisplayOptions, time: Double, anchor: FollowAnchor?, colors: IntArray? = null)

    fun clear()

    // virtual passenger ids this effect has shown to one viewer
    fun idsFor(viewer: UUID): IntArray
}

// switch and registry for packet-based displays
object PacketDisplays {
    private val byVehicle = ConcurrentHashMap<Int, MutableSet<ClientDisplays>>()

    @Volatile
    private var factory: ((Location) -> ClientDisplays)? = null

    val isEnabled: Boolean
        get() = factory != null

    // called by the PacketEvents bootstrap
    fun install(newFactory: ((Location) -> ClientDisplays)?) {
        factory = newFactory
        if (newFactory == null) {
            byVehicle.clear()
        }
    }

    // origin is the spawn point of the effect
    fun create(origin: Location): ClientDisplays? = factory?.invoke(origin)

    fun register(vehicleId: Int, displays: ClientDisplays) {
        byVehicle.computeIfAbsent(vehicleId) { ConcurrentHashMap.newKeySet() }.add(displays)
    }

    fun unregister(vehicleId: Int, displays: ClientDisplays) {
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
