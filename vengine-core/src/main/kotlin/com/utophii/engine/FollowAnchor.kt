package com.utophii.engine

import com.utophii.api.FollowOptions
import com.utophii.api.FollowRotation
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.util.Vector
import org.joml.Quaternionf
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// moves frames calculated around a fixed origin onto a followed entity
// frames are still calculated asynchronously around the spawn origin. The rigid transform runs at render time
// on the server thread, which is the only place the entity may be read and also yields the freshest position
// follow is expected to already carry the effect's defaults
class FollowAnchor(private val follow: FollowOptions, origin: Location) {
    private val offset = follow.offset ?: Vector()
    private val rotation = follow.rotation ?: FollowRotation.NONE
    private val stopWithEntity = follow.stopWithEntity ?: true
    private val originX = origin.x
    private val originY = origin.y
    private val originZ = origin.z

    // kept when the entity disappears and the effect is allowed to outlive it
    private var world: World? = origin.world
    private var pivotX = origin.x
    private var pivotY = origin.y
    private var pivotZ = origin.z
    private var yawRadians = 0.0
    private var pitchRadians = 0.0

    // false once the effect should stop because its entity is gone
    fun isAlive(): Boolean = !stopWithEntity || follow.entity.isValid

    // current pivot of the followed entity, for placeholders and other position snapshots
    fun position(): Location = Location(world, pivotX, pivotY, pivotZ)

    val entity: Entity
        get() = follow.entity

    // the rotation apply used for the last frame, for turning display models with the entity
    fun frameRotation(): Quaternionf? {
        if (rotation == FollowRotation.NONE) {
            return null
        }
        val quaternion = Quaternionf().rotateY((-yawRadians).toFloat())
        if (rotation == FollowRotation.LOOK || rotation == FollowRotation.VELOCITY) {
            quaternion.rotateX(pitchRadians.toFloat())
        }
        return quaternion
    }

    // rewrites every position in place: p' = pivot + R * (offset + p - origin)
    // R applies pitch around the local X axis, then yaw around Y (Bukkit yaw is clockwise, hence -yaw)
    fun apply(positions: List<Location>) {
        sample()
        val rotateYaw = rotation != FollowRotation.NONE
        val rotatePitch = rotation == FollowRotation.LOOK || rotation == FollowRotation.VELOCITY
        val sinYaw = if (rotateYaw) sin(-yawRadians) else 0.0
        val cosYaw = if (rotateYaw) cos(-yawRadians) else 1.0
        val sinPitch = if (rotatePitch) sin(pitchRadians) else 0.0
        val cosPitch = if (rotatePitch) cos(pitchRadians) else 1.0

        positions.forEach { location ->
            val localX = location.x - originX + offset.x
            val localY = location.y - originY + offset.y
            val localZ = location.z - originZ + offset.z

            val pitchedY = localY * cosPitch - localZ * sinPitch
            val pitchedZ = localY * sinPitch + localZ * cosPitch

            location.world = world
            location.x = pivotX + localX * cosYaw + pitchedZ * sinYaw
            location.y = pivotY + pitchedY
            location.z = pivotZ - localX * sinYaw + pitchedZ * cosYaw
        }
    }

    private fun sample() {
        val entity = follow.entity
        if (!entity.isValid) {
            return
        }
        val pivot = if (rotation == FollowRotation.LOOK && entity is LivingEntity) entity.eyeLocation else entity.location
        world = pivot.world
        pivotX = pivot.x
        pivotY = pivot.y
        pivotZ = pivot.z

        when (rotation) {
            FollowRotation.NONE -> Unit
            FollowRotation.BODY -> yawRadians = Math.toRadians(bodyYaw(entity, pivot).toDouble())
            FollowRotation.HEAD -> yawRadians = Math.toRadians(pivot.yaw.toDouble())
            FollowRotation.LOOK -> {
                yawRadians = Math.toRadians(pivot.yaw.toDouble())
                pitchRadians = Math.toRadians(pivot.pitch.toDouble())
            }
            FollowRotation.VELOCITY -> sampleHeading(entity)
        }
    }

    // derives yaw/pitch from the movement direction
    private fun sampleHeading(entity: Entity) {
        val velocity = entity.velocity
        val horizontal = sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
        if (horizontal * horizontal + velocity.y * velocity.y < MIN_HEADING_SPEED_SQUARED) {
            return
        }
        // matches Bukkit's convention: direction = (-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
        yawRadians = atan2(-velocity.x, velocity.z)
        pitchRadians = atan2(-velocity.y, horizontal)
    }

    private fun bodyYaw(entity: Entity, pivot: Location): Float =
        (entity as? LivingEntity)?.bodyYaw ?: pivot.yaw

    companion object {
        private const val MIN_HEADING_SPEED_SQUARED = 1.0e-6
    }
}
