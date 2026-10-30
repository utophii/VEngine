package com.utophii.effects

import com.utophii.math.FrameTransform
import org.bukkit.Location
import org.bukkit.World
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

// Reusable, pooled frame buffer that retains a fixed set of [Location] objects across ticks.
// The hot render path therefore allocates no per-particle Location on every frame.
// A buffer is exclusively owned by one in-flight frame at a time (see [ParticleBufferPool]),
// so its Location objects can be safely overwritten and reused once the previous frame is drawn.
class ParticleBuffer(private var base: Location) {
    private val locations = ArrayList<Location>(INITIAL_CAPACITY)
    private var count = 0

    // optional per-particle scalar in 0..1 that picks the particle's gradient color
    private var values = DoubleArray(INITIAL_CAPACITY)
    private var valued = false

    // optional per-particle RGB that overrides the gradient
    private var colors = IntArray(INITIAL_CAPACITY)
    private var colored = false
    private var boundWorld: World? = null

    // rotation matrix of the frame being written, rebuilt only when the effect's orientation changes
    val transform = FrameTransform()

    // rebinds the buffer to a new effect center (pool reuse across plays)
    fun rebind(newBase: Location) {
        base = newBase
        boundWorld = null
    }

    // ensures exactly `size` reusable Location slots are available and resets the logical count
    fun acquire(size: Int) {
        ensureCapacity(size)
        count = size
        valued = false
        colored = false
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
        if (valued) values[index] = NO_VALUE
        if (colored) colors[index] = NO_COLOR
    }

    // assigns the gradient value of the particle at index
    fun value(index: Int, value: Double) {
        if (!valued) {
            valued = true
            values.fill(NO_VALUE, 0, count)
        }
        values[index] = value
    }

    // true once the effect assigned at least one value this frame
    fun hasValues(): Boolean = valued

    // gradient value at index
    fun valueAt(index: Int): Double = if (valued) values[index] else NO_VALUE

    // assigns the RGB color (0xRRGGBB) of the particle at index
    fun color(index: Int, rgb: Int) {
        if (!colored) {
            colored = true
            colors.fill(NO_COLOR, 0, count)
        }
        colors[index] = rgb and RGB_MASK
    }

    // true once the effect assigned at least one color this frame
    fun hasColors(): Boolean = colored

    // RGB at index, or -1 when the particle has none
    fun colorAt(index: Int): Int = if (colored) colors[index] else NO_COLOR

    // returns the reusable Location at the given logical index
    fun location(index: Int): Location = locations[index]

    // returns a read-only view of the currently filled particle locations
    fun list(): List<Location> = locations.subList(0, count)

    // returns the retained capacity (for pool size heuristics)
    fun capacity(): Int = locations.size

    // releases the buffer back to the pool, retaining the Location objects for reuse
    fun release() {
        count = 0
        valued = false
        colored = false
    }

    private fun ensureCapacity(size: Int) {
        while (locations.size < size) {
            locations.add(Location(base.world, 0.0, 0.0, 0.0, base.yaw, base.pitch))
        }
        if (values.size < size) {
            values = values.copyOf(maxOf(size, values.size * 2))
        }
        if (colors.size < size) {
            colors = colors.copyOf(maxOf(size, colors.size * 2))
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
        private const val NO_VALUE = Double.NaN
        const val NO_COLOR = -1
        private const val RGB_MASK = 0xFFFFFF
    }
}

// Thread-safe pool of reusable [ParticleBuffer] instances.
// Buffers are handed out exclusively (one per in-flight frame) and returned after rendering,
// which removes the steady-state allocation of particle locations from the hot path.
// Two tiers: many ordinary buffers, and a few large ones so dense surfaces are pooled too
// without a pile of multi-megabyte buffers staying in memory. Larger buffers are dropped
object ParticleBufferPool {
    const val SMALL_CAPACITY = 4096
    const val LARGE_CAPACITY = 32768
    private const val MAX_SMALL_BUFFERS = 64
    private const val MAX_LARGE_BUFFERS = 4

    private val small = Tier(MAX_SMALL_BUFFERS)
    private val large = Tier(MAX_LARGE_BUFFERS)

    // obtains a reusable buffer bound to the supplied effect center.
    // sizeHint is the particle count the caller expects, usually its previous frame's,
    // so a small frame does not take a large buffer that a dense effect needs
    fun obtain(base: Location, sizeHint: Int = 0): ParticleBuffer {
        val tier = if (sizeHint > SMALL_CAPACITY) large else small
        val buffer = tier.poll() ?: return ParticleBuffer(base)
        buffer.rebind(base)
        return buffer
    }

    // returns a buffer to the pool for reuse, dropping it when its tier is full or it grew past every tier
    fun release(buffer: ParticleBuffer) {
        buffer.release()
        val capacity = buffer.capacity()
        when {
            capacity <= SMALL_CAPACITY -> small.offer(buffer)
            capacity <= LARGE_CAPACITY -> large.offer(buffer)
        }
    }

    // a bounded free list, the counter avoids ConcurrentLinkedQueue.size, which walks the whole queue
    private class Tier(private val limit: Int) {
        private val free = ConcurrentLinkedQueue<ParticleBuffer>()
        private val count = AtomicInteger()

        fun poll(): ParticleBuffer? = free.poll()?.also { count.decrementAndGet() }

        fun offer(buffer: ParticleBuffer) {
            if (count.incrementAndGet() > limit) {
                count.decrementAndGet()
                return
            }
            free.offer(buffer)
        }
    }
}
