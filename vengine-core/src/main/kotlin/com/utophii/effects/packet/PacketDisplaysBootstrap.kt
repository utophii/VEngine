package com.utophii.effects.packet

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketListenerCommon
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers
import com.utophii.effects.PacketDisplays
import com.utophii.engine.FXEngine

// wires packet-based riding displays into the engine
object PacketDisplaysBootstrap {
    private var registered: PacketListenerCommon? = null

    fun enable() {
        PacketDisplays.install { PacketRidingDisplays(FXEngine.scheduler()) }
        registered = PacketEvents.getAPI().eventManager.registerListener(PassengersListener, PacketListenerPriority.NORMAL)
    }

    fun disable() {
        registered?.let(PacketEvents.getAPI().eventManager::unregisterListener)
        registered = null
        PacketDisplays.install(null)
    }

    // the server resends a vehicle's passenger list whenever a real rider mounts or dismounts, and the client
    // replaces its list with it
    private object PassengersListener : PacketListener {
        override fun onPacketSend(event: PacketSendEvent) {
            if (event.packetType != PacketType.Play.Server.SET_PASSENGERS) {
                return
            }
            val viewer = event.user.uuid ?: return
            val packet = WrapperPlayServerSetPassengers(event)
            val virtual = PacketDisplays.virtualPassengers(packet.entityId, viewer)
            if (virtual.isEmpty()) {
                return
            }
            packet.passengers = (packet.passengers.asList() + virtual.asList()).distinct().toIntArray()
            event.markForReEncode(true)
        }
    }
}
