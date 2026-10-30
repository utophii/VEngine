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
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

// per-frame work of the render/calculate pipeline, measured as one frame.
// The gc profiler reports the bytes each frame allocates, which is what turns into GC pauses on a busy server.
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class RenderHotPathBenchmark {

    private val center = Location(null, 0.0, 64.0, 0.0)
    private val buffer = ParticleBuffer(center)
    private lateinit var layer: ScriptedLayer
    private lateinit var animated: ScriptedLayer
    private lateinit var parametric: ParametricEffect
    private val gradient = GradientColors()
    private val dustOptions = EffectOptions(particle = Particle.DUST, color = Color.RED)
    private val blockOptions = EffectOptions(particle = Particle.BLOCK, material = Material.STONE)
    private val gradientOptions = EffectOptions(
        particle = Particle.DUST,
        gradient = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW),
    )

    @Setup
    fun setup() {
        buffer.acquire(POINTS)
        for (index in 0 until POINTS) {
            val location = buffer.location(index)
            location.x = index.toDouble()
            location.y = 64.0
            location.z = 0.0
        }
        val tracks = listOf(
            NumericTrack("scale", listOf(NumericKeyframe(0.0, 0.5), NumericKeyframe(100.0, 2.0))),
            NumericTrack("parameters.radius", listOf(NumericKeyframe(0.0, 1.0), NumericKeyframe(100.0, 5.0))),
        )
        layer = ScriptedLayer(
            effect = "torus",
            startTick = 0L,
            durationTicks = 200L,
            options = EffectOptions(parameters = mapOf("radius" to 3.0, "height" to 1.0)),
        )
        animated = layer.copy(animation = NumericAnimation(tracks))
        parametric = ParametricEffect(
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
    }

    // ScriptedLayer.resolveOptions per frame: two builders and a parameters map copy per layer per tick
    @Benchmark
    fun scriptedLayerResolveStatic(blackhole: Blackhole) {
        blackhole.consume(layer.resolveOptions(RUNTIME, 12.0))
    }

    // the same with animated tracks, which adds a second builder and a mutable parameter map
    @Benchmark
    fun scriptedLayerResolveAnimated(blackhole: Blackhole) {
        blackhole.consume(animated.resolveOptions(RUNTIME, 12.0))
    }

    // particle payload rebuilt from scratch on every rendered frame
    @Benchmark
    fun dustPayloadPerFrame(blackhole: Blackhole) {
        blackhole.consume(AbstractParticleEffect.resolveParticleData(dustOptions))
    }

    // block payload calls Material#createBlockData, which is the expensive one
    @Benchmark
    fun blockPayloadPerFrame(blackhole: Blackhole) {
        blackhole.consume(AbstractParticleEffect.resolveParticleData(blockOptions))
    }

    // one frame of a formula effect: a parameter map, a fork per axis and one exp4j evaluation per sample
    @Benchmark
    fun parametricFrame(blackhole: Blackhole) {
        parametric.calculateInto(buffer, center, EffectOptions(), 12.0)
        blackhole.consume(buffer.list().size)
    }

    // per-frame gradient sampling of every particle
    @Benchmark
    fun gradientResolve(blackhole: Blackhole) {
        blackhole.consume(gradient.resolve(buffer, gradientOptions))
    }

    // stable rank of every particle, computed per particle per receiving player
    @Benchmark
    fun ranksPerFrame(blackhole: Blackhole) {
        var sum = 0.0
        for (index in 0 until POINTS) {
            sum += ParticleBudget.rank(index)
        }
        blackhole.consume(sum)
    }

    // budget accounting for one frame against VIEWERS receiving players
    @Benchmark
    fun budgetAdmit(blackhole: Blackhole) {
        val budget = ParticleBudget()
        for (viewer in 0 until VIEWERS) {
            val load = budget.load(VIEWERS_UUIDS[viewer])
            for (index in 0 until POINTS) {
                blackhole.consume(budget.admit(load, ParticleBudget.rank(index), budget.baseKeep(64.0, true), 1))
            }
        }
    }

    private companion object {
        const val POINTS = 1024
        const val VIEWERS = 8
        val RUNTIME = EffectOptions(scale = 1.3, rotationYaw = 0.4)
        val VIEWERS_UUIDS = List(VIEWERS) { index -> java.util.UUID(0L, index.toLong() + 1) }
    }
}
