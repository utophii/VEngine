package com.utophii.effects

import com.utophii.api.EffectOptions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class YamlEffectConcurrencyTest {
    @TestFactory
    fun `shared YAML effects match isolated sequential calculations`() = YamlEffectFixtures.names.map { name ->
        dynamicTest(name) {
            val inputs = YamlEffectFixtures.inputs + (0 until WORKERS).map { worker ->
                YamlEffectFixtures.Input("worker_$worker", worker + 0.75, EffectOptions(
                    duration = 8L + worker,
                    parameters = mapOf("radius" to 0.5 + worker, "bias" to worker * 0.1,
                        "points" to 32.0 + worker * 8, "life" to 4.0 + worker),
                    scale = 0.75 + worker * 0.2, rotationYaw = worker * 0.15,
                    rotationPitch = worker * -0.1, rotationRoll = worker * 0.2,
                ), x = worker * 3.0, y = -worker.toDouble(), z = worker * 0.5)
            }
            // references use fresh instances
            val expected = inputs.map { YamlEffectFixtures.capture(YamlEffectFixtures.load(name), it) }
            val shared = YamlEffectFixtures.load(name)
            val barrier = CyclicBarrier(WORKERS)
            val pool = Executors.newFixedThreadPool(WORKERS) { task ->
                Thread(task, "yaml-effect-test-$name").apply { isDaemon = true }
            }
            try {
                val jobs = (0 until WORKERS).map { worker ->
                    pool.submit {
                        repeat(ROUNDS) { round ->
                            // collide on the cold instance and again on every round of cache/pool reuse
                            barrier.await(10, TimeUnit.SECONDS)
                            val index = (round + worker) % inputs.size
                            val input = inputs[index]
                            YamlEffectFixtures.assertFrame(expected[index], YamlEffectFixtures.capture(shared, input),
                                "$name worker=$worker round=$round input=${input.name}")
                        }
                    }
                }
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                jobs.forEach { it.get((deadline - System.nanoTime()).coerceAtLeast(1), TimeUnit.NANOSECONDS) }
            } finally {
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "$name workers did not terminate")
            }
        }
    }

    companion object {
        private const val WORKERS = 4
        private const val ROUNDS = 100
    }
}