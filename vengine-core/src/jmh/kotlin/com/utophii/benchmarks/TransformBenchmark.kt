package com.utophii.benchmarks

import com.utophii.math.FrameTransform
import com.utophii.math.MathUtils
import org.bukkit.Location
import org.bukkit.util.Vector
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OperationsPerInvocation
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

// one frame of POINTS particles through the per-particle transform, scores are per particle.
// transformInto writes into retained Locations, transform is the allocating version it replaced,
// frameMatrix is the path writeParticle takes: a matrix built once per frame, checked per particle
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class TransformBenchmark {

    // none: scale only; euler: yaw, pitch and roll, tilt: euler plus an arbitrary tilt axis
    @Param("none", "euler", "tilt")
    @JvmField
    var rotation: String = ""

    private val center = Location(null, 100.0, 64.0, -100.0)
    private val xs = DoubleArray(POINTS)
    private val ys = DoubleArray(POINTS)
    private val zs = DoubleArray(POINTS)
    private val locals = arrayOfNulls<Vector>(POINTS)
    private val outputs = Array(POINTS) { Location(null, 0.0, 0.0, 0.0) }
    private val frame = FrameTransform()

    private var yaw = 0.0
    private var pitch = 0.0
    private var roll = 0.0
    private var tiltAxis: Vector? = null
    private var tiltAngle = 0.0

    @Setup
    fun setup() {
        // points on a torus-like ring, the shape of a typical effect frame
        for (index in 0 until POINTS) {
            val t = index * TWO_PI / POINTS
            xs[index] = RADIUS * kotlin.math.cos(t)
            ys[index] = kotlin.math.sin(t * WAVES)
            zs[index] = RADIUS * kotlin.math.sin(t)
            locals[index] = Vector(xs[index], ys[index], zs[index])
        }
        val euler = rotation != NONE
        yaw = if (euler) YAW else 0.0
        pitch = if (euler) PITCH else 0.0
        roll = if (euler) ROLL else 0.0
        tiltAxis = if (rotation == TILT) Vector(1.0, 2.0, 3.0) else null
        tiltAngle = if (rotation == TILT) TILT_ANGLE else 0.0
    }

    @Benchmark
    @OperationsPerInvocation(POINTS)
    fun transformInto(blackhole: Blackhole) {
        for (index in 0 until POINTS) {
            val out = outputs[index]
            MathUtils.transformInto(out, xs[index], ys[index], zs[index], center, SCALE, yaw, tiltAxis, tiltAngle, pitch, roll)
            blackhole.consume(out)
        }
    }

    @Benchmark
    @OperationsPerInvocation(POINTS)
    fun frameMatrix(blackhole: Blackhole) {
        for (index in 0 until POINTS) {
            val out = outputs[index]
            frame.prepare(SCALE, yaw, tiltAxis, tiltAngle, pitch, roll)
            frame.applyInto(out, xs[index], ys[index], zs[index], center)
            blackhole.consume(out)
        }
    }

    @Benchmark
    @OperationsPerInvocation(POINTS)
    fun transformAllocating(blackhole: Blackhole) {
        for (index in 0 until POINTS) {
            blackhole.consume(MathUtils.transform(locals[index]!!, center, SCALE, yaw, tiltAxis, tiltAngle, pitch, roll))
        }
    }

    private companion object {
        const val POINTS = 1024
        const val NONE = "none"
        const val TILT = "tilt"
        const val TWO_PI = Math.PI * 2.0
        const val RADIUS = 3.0
        const val WAVES = 6.0
        const val SCALE = 1.3
        const val YAW = 0.7
        const val PITCH = 0.4
        const val ROLL = 0.2
        const val TILT_ANGLE = 0.5
    }
}
