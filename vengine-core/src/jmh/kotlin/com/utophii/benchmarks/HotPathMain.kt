@file:JvmName("HotPathMain")

package com.utophii.benchmarks

import com.utophii.api.EffectOptions
import com.utophii.effects.AbstractParticleEffect
import com.utophii.effects.GradientColors
import com.utophii.effects.NumericAnimation
import com.utophii.effects.NumericKeyframe
import com.utophii.effects.NumericTrack
import com.utophii.effects.ParamRange
import com.utophii.effects.ParametricEffect
import com.utophii.effects.ParticleBuffer
import com.utophii.effects.ScriptedLayer
import com.utophii.engine.ParticleBudget
import com.utophii.math.EasingType
import com.utophii.math.ExpressionEvaluator
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle

// Measures the per-frame work of the calculate/render pipeline and reports nanoseconds and allocated bytes
// per frame. Deliberately not JMH: it runs in one small JVM, so it fits a 2 GB sandbox, and it compares the
// pre-change code (copied below as *Legacy) against the current implementation side by side.
fun main() {
    val center = Location(null, 0.0, 64.0, 0.0)
    val buffer = ParticleBuffer(center).also { b ->
        b.acquire(POINTS)
        for (index in 0 until POINTS) {
            val location = b.location(index)
            location.x = index.toDouble()
            location.y = 64.0
            location.z = 0.0
        }
    }

    val dust = EffectOptions(particle = Particle.DUST, color = Color.RED)
    val block = EffectOptions(particle = Particle.BLOCK, material = Material.STONE)

    // frame of a scripted layer without animation tracks
    val staticLayer = ScriptedLayer(
        effect = "torus",
        startTick = 0L,
        durationTicks = 200L,
        options = EffectOptions(parameters = mapOf("radius" to 3.0, "height" to 1.0)),
    )
    // frame of a scripted layer with two animation tracks, the common case for bundled effects
    val animatedLayer = staticLayer.copy(
        animation = NumericAnimation(
            listOf(
                NumericTrack("scale", listOf(NumericKeyframe(0.0, 0.5), NumericKeyframe(100.0, 2.0))),
                NumericTrack("parameters.radius", listOf(NumericKeyframe(0.0, 1.0), NumericKeyframe(100.0, 5.0))),
            ),
        ),
    )
    val runtime = EffectOptions(scale = 1.3, rotationYaw = 0.4)
    val staticBase = staticLayer.mergedBase(runtime)
    val animatedBase = animatedLayer.mergedBase(runtime)

    val parametric = ParametricEffect(
        name = "bench_torus",
        variables = listOf("t"),
        sampling = listOf(POINTS),
        ranges = listOf(ParamRange(0.0, Math.PI * 2.0)),
        xFormula = "cos(t) * 3 + cos(t * 8) * 0.4",
        yFormula = "sin(t * 8) * 0.8",
        zFormula = "sin(t) * 3 + sin(t * 8) * 0.4",
        defaults = mapOf("radius" to 3.0),
        angularSpeed = 0.02,
    )
    val axes = listOf("x", "y", "z").map { ExpressionEvaluator.compile("sin(t) * 3")!! }

    println("frames of $POINTS particles, ns and bytes are per frame (one tick of one effect layer)")
    println()

    bench("particle payload, DUST (before: rebuilt per frame)") {
        AbstractParticleEffect.resolveParticleData(dust)
    }
    bench("particle payload, DUST (after: cached per renderer)") {
        cachedDust
    }
    // block data needs a live server registry, so these two only run where a server provides one
    if (cachedBlock != null) {
        bench("particle payload, BLOCK (before: createBlockData per frame)") {
            AbstractParticleEffect.resolveParticleData(block)
        }
        bench("particle payload, BLOCK (after: cached)") {
            cachedBlock
        }
    } else {
        println("%-56s %s".format("particle payload, BLOCK", "skipped: needs a server registry"))
    }
    bench("scripted layer options, static (before)") {
        legacyResolveOptions(staticLayer, runtime, 12.0)
    }
    bench("scripted layer options, static (after)") {
        staticLayer.resolveBaseOptions(staticBase, 12.0)
    }
    bench("scripted layer options, animated (before)") {
        legacyResolveOptions(animatedLayer, runtime, 12.0)
    }
    bench("scripted layer options, animated (after)") {
        animatedLayer.resolveBaseOptions(animatedBase, 12.0)
    }
    bench("formula frame setup: parameter map + 3 forks (before)") {
        val params = HashMap<String, Double>(8)
        params.putAll(mapOf("radius" to 3.0))
        params["time"] = 12.0
        params to axes.map(ExpressionEvaluator::fork)
    }
    bench("formula frame setup: pooled scratch state (after)") {
        pooledSetup
    }
    bench("formula frame, full calculateInto ($POINTS samples)") {
        parametric.calculateInto(buffer, Location(null, 0.0, 64.0, 0.0), EffectOptions(), 12.0)
    }
    val gradientColors = GradientColors()
    val gradientOptions = EffectOptions(particle = Particle.DUST, gradient = listOf(Color.RED, Color.GREEN, Color.BLUE))
    bench("gradient sampling of one frame, $POINTS particles") {
        gradientColors.resolve(buffer, gradientOptions)
    }
    val singleAxis = ExpressionEvaluator.compile("cos(t) * 3 + cos(t * 8) * 0.4")!!
    val singleFork = singleAxis.fork()
    val singleValues = HashMap<String, Double>().also { it["t"] = 0.5 }
    bench("one exp4j evaluation (1 axis, reused map)") {
        singleFork.evaluate(singleValues)
    }
    bench("transform write of $POINTS particles (matrix reused)") {
        val transform = buffer.transform
        transform.prepare(1.3, 0.4, null, 0.0, 0.0, 0.0)
        for (index in 0 until POINTS) {
            transform.applyInto(buffer.location(index), index.toDouble(), 64.0, 0.0, center)
        }
        buffer
    }
    bench("stable particle ranks, $POINTS particles") {
        var sum = 0.0
        for (index in 0 until POINTS) {
            sum += ParticleBudget.rank(index)
        }
        sum
    }
    bench("budget admit, $POINTS particles x $VIEWERS viewers") {
        val budget = ParticleBudget()
        var kept = 0
        for (viewer in 0 until VIEWERS) {
            val load = budget.load(VIEWER_IDS[viewer])
            for (index in 0 until POINTS) {
                if (budget.admit(load, ParticleBudget.rank(index), budget.baseKeep(64.0, true), 1)) kept++
            }
        }
        kept
    }
}

// the payload cache the renderer now keeps, see FrameRenderer.PayloadCache
private val cachedDust: Any? = AbstractParticleEffect.resolveParticleData(EffectOptions(particle = Particle.DUST, color = Color.RED))
private val cachedBlock: Any? = runCatching {
    AbstractParticleEffect.resolveParticleData(EffectOptions(particle = Particle.BLOCK, material = Material.STONE))
}.getOrNull()

// the per-frame scratch of a formula effect, as the pooled version reuses it
private val pooledParams = HashMap<String, Double>(16)
private val pooledAxes = listOf("x", "y", "z").map { ExpressionEvaluator.compile("sin(t) * 3")!!.fork() }
private val pooledSetup: Any
    get() {
        pooledParams.clear()
        pooledParams.putAll(mapOf("radius" to 3.0))
        pooledParams["time"] = 12.0
        return pooledParams to pooledAxes
    }

// a copy of ScriptedLayer.resolveOptions as it was before the change, for the before/after comparison
private fun legacyResolveOptions(layer: ScriptedLayer, runtime: EffectOptions, time: Double): EffectOptions {
    val options = layer.options
    val mergedParameters = options.parameters + runtime.parameters
    val builder = options.toBuilder()
    builder.scale(options.scale * runtime.scale)
    builder.rotationYaw(options.rotationYaw + runtime.rotationYaw)
    builder.rotationPitch(options.rotationPitch + runtime.rotationPitch)
    builder.rotationRoll(options.rotationRoll + runtime.rotationRoll)
    (runtime.tiltAxis ?: options.tiltAxis)?.let(builder::tiltAxis)
    val tiltAngle = if (runtime.tiltAxis != null) runtime.tiltAngle else options.tiltAngle + runtime.tiltAngle
    builder.tiltAngle(tiltAngle)
    if (runtime.receivers.isNotEmpty()) builder.receivers(runtime.receivers)
    if (runtime.modifiers.isNotEmpty()) builder.modifiers(options.modifiers + runtime.modifiers)
    builder.parameters(mergedParameters)
    runtime.color?.let(builder::color)
    runtime.toColor?.let(builder::toColor)
    if (runtime.gradient.isNotEmpty()) builder.gradient(runtime.gradient)
    runtime.material?.let(builder::material)
    runtime.display?.let(builder::display)
    if (runtime.budgetExempt) builder.budgetExempt(true)
    if (!runtime.lod) builder.lod(false)
    val animated = layer.animation.apply(builder.build(), time)
    return animated.toBuilder().duration(layer.durationTicks ?: runtime.duration).build()
}

// runs a batch, discards the first rounds as warmup and reports the median of the measured rounds
private inline fun bench(name: String, rounds: Int = 7, iterations: Int = 2000, body: () -> Any?) {
    val mx = java.lang.management.ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
    repeat(WARMUP) { round ->
        var sink = 0L
        for (index in 0 until iterations) {
            sink += body()?.hashCode()?.toLong() ?: 1L
        }
        blackholeSink = sink
    }
    val nanos = LongArray(rounds)
    val bytes = LongArray(rounds)
    for (round in 0 until rounds) {
        val bytesBefore = mx?.getThreadAllocatedBytes(Thread.currentThread().id) ?: 0L
        val start = System.nanoTime()
        var sink = 0L
        for (index in 0 until iterations) {
            sink += body()?.hashCode()?.toLong() ?: 1L
        }
        val elapsed = System.nanoTime() - start
        val allocated = (mx?.getThreadAllocatedBytes(Thread.currentThread().id) ?: 0L) - bytesBefore
        blackholeSink = sink
        nanos[round] = elapsed / iterations
        bytes[round] = allocated / iterations
    }
    nanos.sort()
    bytes.sort()
    println(
        "%-56s %8d ns  %7d B".format(name, nanos[rounds / 2], bytes[rounds / 2]),
    )
}

@Volatile
private var blackholeSink: Long = 0L

private const val WARMUP = 3
private const val POINTS = 1024
private const val VIEWERS = 8
private val VIEWER_IDS = List(VIEWERS) { index -> java.util.UUID(0L, index.toLong() + 1) }

private val unused = EasingType.LINEAR
