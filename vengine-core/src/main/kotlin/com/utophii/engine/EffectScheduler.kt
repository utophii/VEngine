package com.utophii.engine

import com.utophii.api.EffectHandle
import com.utophii.api.EffectOptions
import com.utophii.api.SimpleEffectHandle
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level

// owns Bukkit task scheduling, active handles and frame synchronization for VEngine effects
class EffectScheduler(val plugin: JavaPlugin) {

    private val activeHandles = ConcurrentHashMap<String, EffectHandle>()
    private val running = ConcurrentHashMap<String, RunningEffect>()
    private val anchors = ConcurrentHashMap<String, FollowAnchor>()
    private val idCounter = AtomicInteger(1)

    // everything the loop advances
    private val tickables = ArrayList<Tickable>()
    // registrations from any thread
    private val pending = ConcurrentLinkedQueue<Tickable>()
    // finished calculations waiting to be rendered on the server thread
    private val completed = ConcurrentLinkedQueue<Runnable>()
    private val calculator: ExecutorService = Executors.newFixedThreadPool(CALCULATOR_THREADS) { runnable ->
        Thread(runnable, "VEngine-calc").apply { isDaemon = true }
    }
    // one server-thread loop drives every effect
    private val loop: BukkitTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable(::tick), 0L, FRAME_PERIOD_TICKS)

    // view-distance culling radius in blocks; particles are only sent to players within this radius of the particle location
    var viewDistance: Double = DEFAULT_VIEW_DISTANCE_BLOCKS
        get() = field
        set(value) { field = value.coerceAtLeast(MIN_VIEW_DISTANCE_BLOCKS) }

    // squared view distance for cheap squared-distance comparisons
    private val viewDistanceSquared: Double
        get() = viewDistance * viewDistance

    // particle limits, distance LOD and lag-driven quality applied to every spawned particle
    val budget = ParticleBudget()

    // called on the server thread after lag changed the quality level
    var onQualityChange: (() -> Unit)? = null
    private var tickCounter = 0L
    private var singleRank = 0

    // advanced once per server tick by the loop
    internal interface Tickable {
        fun tick(): Boolean

        fun cancel()
    }

    // adds a tickable to the loop
    internal fun register(tickable: Tickable) {
        pending.add(tickable)
    }

    // renders the frames calculated since the last tick
    private fun tick() {
        budget.startTick()
        if (budget.quality.enabled && tickCounter++ % QUALITY_CHECK_TICKS == 0L) {
            if (budget.quality.update(plugin.server.averageTickTime)) {
                onQualityChange?.invoke()
            }
        }
        while (true) {
            val delivery = completed.poll() ?: break
            runGuarded(delivery::run)
        }
        while (true) {
            tickables += pending.poll() ?: break
        }
        tickables.removeIf { tickable -> !runGuarded(tickable::tick, fallback = true) }
    }

    // keeps one failing effect from breaking the loop for all others
    private fun <R> runGuarded(action: () -> R, fallback: R): R = try {
        action()
    } catch (t: Throwable) {
        plugin.logger.log(Level.SEVERE, "VEngine effect tick failed", t)
        fallback
    }

    private fun runGuarded(action: () -> Unit) = runGuarded(action, Unit)

    // calculates frames on the calculator pool and renders them on the server thread one tick later
    // a new frame is skipped while the previous one is still being calculated, so time keeps moving under load
    // when a computed frame is dropped release disposes it exactly once
    // onStop runs once on the server thread when the effect finishes or is cancelled
    // keepAlive is checked on the server thread every tick
    fun <T> scheduleFrames(
        effectName: String,
        initialDelayTicks: Long = INITIAL_DELAY_TICKS,
        durationTicks: Long,
        calculate: (Double) -> T,
        render: (Double, T) -> Unit,
        release: ((T) -> Unit)? = null,
        origin: Location? = null,
        onStop: (() -> Unit)? = null,
        keepAlive: (() -> Boolean)? = null,
        anchor: FollowAnchor? = null,
    ): EffectHandle {
        val handleId = "$effectName#${idCounter.getAndIncrement()}"
        val handle = SimpleEffectHandle(handleId, effectName) {
            forget(handleId)
            onStop?.let(::runOnMainThread)
        }

        register(FrameJob(handle, initialDelayTicks, durationTicks, calculate, render, release, keepAlive))
        activeHandles[handleId] = handle
        track(handle, origin, anchor)
        return handle
    }

    // one effect's frame clock
    private inner class FrameJob<T>(
        private val handle: EffectHandle,
        private var delayTicks: Long,
        private val durationTicks: Long,
        private val calculate: (Double) -> T,
        private val render: (Double, T) -> Unit,
        private val release: ((T) -> Unit)?,
        private val keepAlive: (() -> Boolean)?,
    ) : Tickable {
        private var frameIndex = 0L
        private var calculating = false

        override fun tick(): Boolean {
            if (handle.isCancelled) {
                return false
            }
            if (delayTicks > 0) {
                delayTicks--
                return true
            }
            if (frameIndex >= durationTicks || keepAlive?.invoke() == false) {
                handle.cancel()
                return false
            }

            val time = (frameIndex++).toDouble()
            if (calculating) {
                return true
            }
            calculating = true
            calculator.execute {
                val frame = try {
                    if (handle.isCancelled) null else Frame(calculate(time))
                } catch (t: Throwable) {
                    plugin.logger.log(Level.SEVERE, "VEngine effect ${handle.id} failed to calculate a frame", t)
                    null
                }
                completed.add { deliver(time, frame) }
            }
            return true
        }

        override fun cancel() {
            handle.cancel()
        }

        private fun deliver(time: Double, frame: Frame<T>?) {
            calculating = false
            frame ?: return
            try {
                if (!handle.isCancelled) {
                    render(time, frame.value)
                }
            } finally {
                release?.invoke(frame.value)
            }
        }
    }

    // boxes a calculated frame
    private class Frame<T>(val value: T)

    // registers a composite or custom handle in the active handle registry
    fun registerHandle(handle: EffectHandle) {
        activeHandles[handle.id] = handle
    }

    // registers a composite handle and automatically unregisters it after ttlTicks,
    // so scripted effects that finished naturally do not linger in the active handle listing.
    // origin is the spawn point placeholders use
    fun registerComposite(handle: EffectHandle, ttlTicks: Long, origin: Location? = null, anchor: FollowAnchor? = null) {
        activeHandles[handle.id] = handle
        track(handle, origin, anchor)
        if (ttlTicks >= EffectOptions.INFINITE_DURATION) {
            return
        }
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            // any straggler children cancel themselves and leave the registry through their own callbacks
            handle.cancel()
            forget(handle.id)
        }, ttlTicks.coerceAtLeast(MIN_COMPOSITE_TTL_TICKS))
    }

    // removes a handle from the listing without stopping it
    fun detach(id: String) {
        forget(id)
    }

    // stops a running effect by handle ID
    fun stop(id: String): Boolean {
        val handle = activeHandles[id] ?: activeHandles.entries.firstOrNull { it.key.equals(id, ignoreCase = true) }?.value
        return if (handle != null) {
            handle.cancel()
            forget(handle.id)
            true
        } else {
            false
        }
    }

    // stops all active running effects
    fun stopAll(): Int {
        val count = activeHandles.size
        activeHandles.values.toList().forEach(EffectHandle::cancel)
        activeHandles.clear()
        running.clear()
        anchors.clear()
        // detached effects are not listed in activeHandles, so stop whatever the loop still drives as well
        runOnMainThread {
            pending.toList().forEach(Tickable::cancel)
            tickables.toList().forEach(Tickable::cancel)
        }
        return count
    }

    // returns a snapshot of active effect handles
    fun activeHandles(): List<EffectHandle> = activeHandles.values.toList()

    // returns a snapshot of user-facing spawned effects, including their spawn point
    // followed effects report the last sampled position of their entity instead of the spawn point
    fun runningEffects(): List<RunningEffect> = running.values.map { effect ->
        val position = anchors[effect.id]?.position() ?: return@map effect
        val worldName = position.world?.name ?: return@map effect
        effect.copy(worldName = worldName, x = position.x, y = position.y, z = position.z)
    }

    // remembers a spawned effect's origin so placeholders can answer "what is playing, and where"
    private fun track(handle: EffectHandle, origin: Location?, anchor: FollowAnchor? = null) {
        if (origin == null) {
            return
        }
        anchor?.let { anchors[handle.id] = it }
        val worldName = origin.world?.name ?: return
        running[handle.id] = RunningEffect(
            id = handle.id,
            effectName = handle.effectName,
            worldName = worldName,
            x = origin.x,
            y = origin.y,
            z = origin.z,
        )
    }

    // drops a handle from both the task registry and the placeholder snapshot
    private fun forget(id: String) {
        activeHandles.remove(id)
        running.remove(id)
        anchors.remove(id)
    }

    // spawn point of one user-facing effect. Coordinates are copied so later Location mutation cannot move the snapshot
    data class RunningEffect(
        val id: String,
        val effectName: String,
        val worldName: String,
        val x: Double,
        val y: Double,
        val z: Double,
    )

    // spawns a particle only to players within the configured view distance of the particle location
    // explicit receivers are filtered by distance; when none are given the particle is broadcast only to nearby online players in the same world
    // budgeted particles count against the limits and thin out with lag, lod thins them out with distance
    fun spawnParticle(
        location: Location,
        particle: Particle,
        data: Any?,
        receivers: Iterable<Player>,
        count: Int = DEFAULT_PARTICLE_COUNT,
        offsetX: Double = DEFAULT_PARTICLE_OFFSET,
        offsetY: Double = DEFAULT_PARTICLE_OFFSET,
        offsetZ: Double = DEFAULT_PARTICLE_OFFSET,
        speed: Double = DEFAULT_PARTICLE_SPEED,
        budgeted: Boolean = true,
        lod: Boolean = true,
    ) {
        // lone particles take turns in rank, otherwise every one of them would be kept or dropped together
        spawnParticles(
            listOf(location), particle, data, receivers, count, offsetX, offsetY, offsetZ, speed,
            budgeted = budgeted, lod = lod, rankOffset = singleRank++,
        )
    }

    // spawns one frame of particles with the same culling as spawnParticle.
    // Nearby players are resolved once per frame: candidates are pre-culled against the frame's bounding box,
    // then each particle is checked against cached player coordinates
    // dataAt, when given, supplies the payload of each particle by index.
    // Each player receives a stable subset of the frame sized by the budget, see ParticleBudget,
    // rankOffset shifts which indices form that subset
    fun spawnParticles(
        locations: List<Location>,
        particle: Particle,
        data: Any?,
        receivers: Iterable<Player>,
        count: Int = DEFAULT_PARTICLE_COUNT,
        offsetX: Double = DEFAULT_PARTICLE_OFFSET,
        offsetY: Double = DEFAULT_PARTICLE_OFFSET,
        offsetZ: Double = DEFAULT_PARTICLE_OFFSET,
        speed: Double = DEFAULT_PARTICLE_SPEED,
        dataAt: ((Int) -> Any?)? = null,
        budgeted: Boolean = true,
        lod: Boolean = true,
        rankOffset: Int = 0,
    ) {
        if (locations.isEmpty()) {
            return
        }
        val explicitReceivers = receivers.toList()
        var viewers: FrameViewers? = null
        locations.forEachIndexed { index, location ->
            val world = location.world ?: return@forEachIndexed
            val current = viewers?.takeIf { it.world == world }
                ?: FrameViewers.collect(world, locations, explicitReceivers, viewDistance, budget).also { viewers = it }
            val payload = if (dataAt != null) dataAt(index) else data
            val rank = ParticleBudget.rank(index + rankOffset)
            current.forEachNear(location, viewDistanceSquared) { viewer, distanceSquared ->
                if (budgeted) {
                    val base = budget.baseKeep(distanceSquared, lod)
                    if (!budget.admit(current.loads[viewer], rank, base, count)) return@forEachNear
                } else if (lod && rank >= budget.distanceKeep(distanceSquared)) {
                    return@forEachNear
                }
                val player = current.players[viewer]
                budget.sent(count)
                player.spawnParticle(
                    particle,
                    location,
                    count,
                    offsetX,
                    offsetY,
                    offsetZ,
                    speed,
                    payload,
                )
            }
        }
    }

    // online players of one world that may see any particle of the current frame, with their positions sampled once
    private class FrameViewers(
        val world: World,
        val players: List<Player>,
        val loads: List<ParticleBudget.PlayerLoad>,
        private val coordinates: DoubleArray,
    ) {
        // action receives the viewer index and its squared distance to the location
        inline fun forEachNear(location: Location, radiusSquared: Double, action: (Int, Double) -> Unit) {
            val x = location.x
            val y = location.y
            val z = location.z
            for (index in players.indices) {
                val dx = coordinates[index * AXES] - x
                val dy = coordinates[index * AXES + 1] - y
                val dz = coordinates[index * AXES + 2] - z
                val distanceSquared = dx * dx + dy * dy + dz * dz
                if (distanceSquared <= radiusSquared) {
                    action(index, distanceSquared)
                }
            }
        }

        companion object {
            private const val AXES = 3

            // keeps the candidates within radius of the bounding box of the frame's particles in this world
            fun collect(
                world: World,
                locations: List<Location>,
                explicitReceivers: List<Player>,
                radius: Double,
                budget: ParticleBudget,
            ): FrameViewers {
                var minX = Double.POSITIVE_INFINITY
                var minY = Double.POSITIVE_INFINITY
                var minZ = Double.POSITIVE_INFINITY
                var maxX = Double.NEGATIVE_INFINITY
                var maxY = Double.NEGATIVE_INFINITY
                var maxZ = Double.NEGATIVE_INFINITY
                locations.forEach { location ->
                    if (location.world != world) return@forEach
                    minX = minOf(minX, location.x); maxX = maxOf(maxX, location.x)
                    minY = minOf(minY, location.y); maxY = maxOf(maxY, location.y)
                    minZ = minOf(minZ, location.z); maxZ = maxOf(maxZ, location.z)
                }

                val candidates = explicitReceivers.ifEmpty { world.players }
                val players = ArrayList<Player>(candidates.size)
                val loads = ArrayList<ParticleBudget.PlayerLoad>(candidates.size)
                val coordinates = DoubleArray(candidates.size * AXES)
                val radiusSquared = radius * radius
                candidates.forEach { player ->
                    if (!player.isOnline || player.world != world) return@forEach
                    val position = player.location
                    val dx = axisGap(position.x, minX, maxX)
                    val dy = axisGap(position.y, minY, maxY)
                    val dz = axisGap(position.z, minZ, maxZ)
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) return@forEach
                    val offset = players.size * AXES
                    coordinates[offset] = position.x
                    coordinates[offset + 1] = position.y
                    coordinates[offset + 2] = position.z
                    players.add(player)
                    loads.add(budget.load(player.uniqueId))
                }
                return FrameViewers(world, players, loads, coordinates)
            }

            // distance from value to the [min, max] interval along one axis
            private fun axisGap(value: Double, min: Double, max: Double): Double = when {
                value < min -> min - value
                value > max -> value - max
                else -> 0.0
            }
        }
    }

    // runs action on the server thread
    fun runOnMainThread(action: () -> Unit) {
        if (plugin.server.isPrimaryThread) {
            action()
        } else if (plugin.isEnabled) {
            plugin.server.scheduler.runTask(plugin, Runnable(action))
        }
    }

    // stops every effect and shuts the loop and the calculator pool down for good
    fun cancelAll() {
        stopAll()
        loop.cancel()
        calculator.shutdownNow()
        tickables.clear()
        pending.clear()
        completed.clear()
    }

    companion object {
        private const val INITIAL_DELAY_TICKS = 0L
        private const val FRAME_PERIOD_TICKS = 1L
        private const val QUALITY_CHECK_TICKS = 20L
        private const val MIN_COMPOSITE_TTL_TICKS = 1L
        private val CALCULATOR_THREADS = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        private const val DEFAULT_PARTICLE_COUNT = 1
        private const val DEFAULT_PARTICLE_OFFSET = 0.0
        private const val DEFAULT_PARTICLE_SPEED = 0.0

        // default view-distance culling radius in blocks
        const val DEFAULT_VIEW_DISTANCE_BLOCKS = 64.0
        private const val MIN_VIEW_DISTANCE_BLOCKS = 0.0
    }
}