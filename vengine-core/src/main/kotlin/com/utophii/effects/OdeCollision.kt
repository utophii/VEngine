package com.utophii.effects

import org.bukkit.FluidCollisionMode
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.util.Vector

// optional block response for a physical ODE, with bounded work per integration step
data class OdeCollision(
    val response: Response = Response.NONE,
    val restitution: Double = 1.0,
    val substeps: Int = 1,
    val maxBounces: Int = 8,
) {
    enum class Response { NONE, TERMINATE, STOP, BOUNCE }

    init {
        require(restitution.isFinite() && restitution in 0.0..1.0) { "collision.restitution must be between 0 and 1" }
        require(substeps in 1..64) { "collision.substeps must be between 1 and 64" }
        require(maxBounces in 1..64) { "collision.maxBounces must be between 1 and 64" }
    }
}

// maps a physical state into the final world frame and maps collision corrections back into state space
internal class OdeCollisionFrame(basis: List<Location>) {
    private val origin = basis[0]
    private val x = basis[1].toVector().subtract(origin.toVector())
    private val y = basis[2].toVector().subtract(origin.toVector())
    private val z = basis[3].toVector().subtract(origin.toVector())
    private val determinant = x.dot(y.clone().crossProduct(z))
    private val inverseX: Vector
    private val inverseY: Vector
    private val inverseZ: Vector

    init {
        require(determinant.isFinite() && determinant != 0.0) { "Collision transform must be finite and invertible" }
        inverseX = y.clone().crossProduct(z).multiply(1.0 / determinant)
        inverseY = z.clone().crossProduct(x).multiply(1.0 / determinant)
        inverseZ = x.clone().crossProduct(y).multiply(1.0 / determinant)
    }

    fun position(local: Vector): Location = origin.clone().add(direction(local))

    fun direction(local: Vector): Vector = Vector(
        x.x * local.x + y.x * local.y + z.x * local.z,
        x.y * local.x + y.y * local.y + z.y * local.z,
        x.z * local.x + y.z * local.y + z.z * local.z,
    )

    fun localPosition(world: Vector): Vector = localDirection(world.clone().subtract(origin.toVector()))

    fun localDirection(world: Vector): Vector = Vector(inverseX.dot(world), inverseY.dot(world), inverseZ.dot(world))

    data class Hit(val fraction: Double, val position: Vector, val normal: Vector)

    fun trace(from: Vector, to: Vector): Hit? {
        val start = position(from)
        val world = start.world ?: return null
        check(Bukkit.isPrimaryThread()) { "ODE block collisions must be calculated on the server thread" }
        val end = position(to)
        val displacement = end.toVector().subtract(start.toVector())
        val distance = displacement.length()
        if (!distance.isFinite() || distance <= 1.0e-12) return null
        val hit = world.rayTraceBlocks(start, displacement.multiply(1.0 / distance), distance, FluidCollisionMode.NEVER, true)
            ?: return null
        val normal = hit.hitBlockFace?.direction ?: displacement.clone().multiply(-1.0)
        val fraction = (hit.hitPosition.distance(start.toVector()) / distance).coerceIn(0.0, 1.0)
        return Hit(fraction, hit.hitPosition, normal)
    }
}