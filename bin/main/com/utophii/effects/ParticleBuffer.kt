package com.utophii.effects

import org.bukkit.Location
import org.bukkit.World
import java.util.concurrent.ConcurrentLinkedQueue

// Reusable, pooled frame buffer that retains a fixed set of [Location] objects across ticks.
// The hot render path therefore allocates no per-particle Location on every frame.
// A buffer is exclusively owned by one in-flight frame at a time (see [ParticleBufferPool]),
// so its Location objects can be safely overwritten and reused once the previous frame is drawn.
class ParticleBuffer(private var base: Location) {
    private val locations = ArrayList<Location>(INITIAL_CAPACITY)
    private var count = 0
    private var boundWorld: World? = null

    // rebinds the buffer to a new effect center (pool reuse across plays)
    fun rebind(newBase: Location) {
        base = newBase
        boundWorld = null
    }

    // ensures exactly `size` reusable Location slots are available and resets the logical count
    fun acquire(size: Int) {
        ensureCapacity(size)
        count = size
        bindWorld()
    }

    // appends one particle coordinate, reusing an existing Location slot when available
    fun add(x: Double, y: Double, z: Double) {
        val index = count
        ensureCapacity(index + 1)
        val location = locations[index]
        location.x = x
        location.y = y
        location.z = z
        count++
    }

    // returns the reusable Location at the given logical index
    fun location(index: Int): Location = locations[index]

    // returns a read-only view of the currently filled particle locations
    fun list(): List<Location> = locations.subList(0, count)

    // returns the retained capacity (for pool size heuristics)
    fun capacity(): Int = locations.size

    // releases the buffer back to the pool, retaining the Location objects for reuse
    fun release() {
        count = 0
    }

    private fun ensureCapacity(size: Int) {
        while (locations.size < size) {
            locations.add(Location(base.world, 0.0, 0.0, 0.0, base.yaw, base.pitch))
        }
    }

    // rebinds the world of every retained Location when the buffer is reused across worlds
    private fun bindWorld() {
        if (base.world !== boundWorld) {
            boundWorld = base.world
            for (i in 0 until count) {
                locations[i].world = base.world
            }
        }
    }

    companion object {
        private const val INITIAL_CAPACITY = 32
    }
}

// Thread-safe pool of reusable [ParticleBuffer] instances.
// Buffers are handed out exclusively (one per in-flight frame) and returned after rendering,
// which removes the steady-state allocation of particle locations from the hot path.
object ParticleBufferPool {
    private const val MAX_BUFFERS = 64
    private const val MAX_CAPACITY = 4096

    private val free = ConcurrentLinkedQueue<ParticleBuffer>()

    // obtains a reusable buffer bound to the supplied effect center
    fun obtain(base: Location): ParticleBuffer {
        val buffer = free.poll()
        return if (buffer != null) {
            buffer.rebind(base)
            buffer
        } else {
            ParticleBuffer(base)
        }
    }

    // returns a buffer to the pool for reuse, dropping it when it grew too large for memory pressure
    fun release(buffer: ParticleBuffer) {
        buffer.release()
        if (buffer.capacity() <= MAX_CAPACITY && free.size < MAX_BUFFERS) {
            free.offer(buffer)
        }
    }
}
