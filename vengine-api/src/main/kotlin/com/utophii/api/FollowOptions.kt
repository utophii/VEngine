package com.utophii.api

import org.bukkit.entity.Entity
import org.bukkit.util.Vector

/**
 * binds a running effect to an entity: every frame the whole effect is moved to the entity
 * and, depending on [rotation], turned with it. Auras, wings, projectile trails and mount effects.
 *
 * The effect is authored in the entity's local frame: +Z is forward, +Y is up and +X is the
 * entity's left (Minecraft's frame at yaw 0). The same frame applies to [offset].
 *
 * Null fields fall back to the effect's own [FollowDefaults] (the `follow:` block of its YAML file),
 * then to no offset, [FollowRotation.NONE] and stopping with the entity.
 *
 * @property entity entity the effect follows
 * @property offset local offset from the pivot in blocks, rotated together with the effect
 * @property rotation which orientation of the entity the effect inherits
 * @property stopWithEntity cancels the effect once the entity dies, despawns or its player leaves;
 * when false the effect stays at the last known position until its duration ends
 */
data class FollowOptions(
    val entity: Entity,
    val offset: Vector? = null,
    val rotation: FollowRotation? = null,
    val stopWithEntity: Boolean? = null,
) {
    // fills the fields left null with the effect's defaults
    fun withDefaults(defaults: FollowDefaults?): FollowOptions {
        if (defaults == null) {
            return this
        }
        return copy(
            offset = offset ?: defaults.offset,
            rotation = rotation ?: defaults.rotation,
            stopWithEntity = stopWithEntity ?: defaults.stopWithEntity,
        )
    }
}

// how an effect prefers to be attached when it is played on an entity
data class FollowDefaults(
    val offset: Vector? = null,
    val rotation: FollowRotation? = null,
    val stopWithEntity: Boolean? = null,
)

// which orientation a followed effect inherits from its entity
enum class FollowRotation {
    // translation only
    NONE,

    // body yaw of living entities, head yaw otherwise
    BODY,

    // head yaw without pitch
    HEAD,

    // head yaw and pitch, pivoting at the eyes
    LOOK,

    // yaw and pitch of the movement direction
    VELOCITY,
}
