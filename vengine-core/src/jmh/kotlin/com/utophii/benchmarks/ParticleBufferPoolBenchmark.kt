package com.utophii.benchmarks

import com.utophii.effects.ParticleBuffer
import com.utophii.effects.ParticleBufferPool
import org.bukkit.Location
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Threads
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

// one frame's buffer lifecycle as an effect sees it: obtain, fill size particles, release.
// Pooled reuses retained Locations, fresh builds a new buffer every frame, which is what the pool avoids.
// 8192 is above the pool's capacity cap
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
open class ParticleBufferPoolBenchmark {

    @Param("64", "1024", "4096", "8192")
    @JvmField
    var size: Int = 0

    private val center = Location(null, 0.0, 64.0, 0.0)

    @Benchmark
    fun pooled(blackhole: Blackhole) {
        val buffer = ParticleBufferPool.obtain(center)
        fill(buffer)
        blackhole.consume(buffer.list())
        ParticleBufferPool.release(buffer)
    }

    // as many threads as a busy calculator pool
    @Benchmark
    @Threads(CALCULATOR_THREADS)
    fun pooledContended(blackhole: Blackhole) {
        pooled(blackhole)
    }

    @Benchmark
    fun fresh(blackhole: Blackhole) {
        val buffer = ParticleBuffer(center)
        fill(buffer)
        blackhole.consume(buffer.list())
    }

    // writes every slot the way calculateInto does
    private fun fill(buffer: ParticleBuffer) {
        buffer.acquire(size)
        for (index in 0 until size) {
            val location = buffer.location(index)
            location.x = index.toDouble()
            location.y = 64.0
            location.z = -index.toDouble()
        }
    }

    private companion object {
        const val CALCULATOR_THREADS = 4
    }
}
