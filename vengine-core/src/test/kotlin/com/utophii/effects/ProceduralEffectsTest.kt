package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.math.OdeIntegrator
import org.bukkit.Location
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin

class ProceduralEffectsTest {
    private val center = Location(null, 0.0, 0.0, 0.0)

    @Test
    fun `Euler and RK4 update state simultaneously and honor the initial sample`() {
        fun oscillator(method: OdeIntegrator.Method) = OdeEffect(
            "oscillator", mapOf("x" to "1", "v" to "0"), mapOf("x" to "v", "v" to "-x"),
            method, step = "0.1", samples = "11", includeInitial = true,
            output = mapOf("x" to "x", "y" to "v", "z" to "integrationTime"),
        )
        val euler = oscillator(OdeIntegrator.Method.EULER).calculate(center, EffectOptions(), 0.0)
        assertEquals(1.0, euler[1].x, 1e-12)
        assertEquals(-0.1, euler[1].y, 1e-12)
        val rk4 = oscillator(OdeIntegrator.Method.RK4).calculate(center, EffectOptions(), 0.0)
        assertEquals(11, rk4.size)
        assertEquals(1.0, rk4[0].x)
        assertEquals(cos(1.0), rk4.last().x, 1e-6)
        assertEquals(-sin(1.0), rk4.last().y, 1e-6)
        assertEquals(1.0, rk4.last().z, 1e-12)
    }

    @Test
    fun `six state trajectory preserves acceleration and progressive reveal`() {
        val effect = OdeEffect("fall",
            initial = mapOf("x" to "0", "y" to "10", "z" to "0", "vx" to "2", "vy" to "0", "vz" to "0"),
            derivatives = mapOf("x" to "vx", "y" to "vy", "z" to "vz", "vx" to "0", "vy" to "-2", "vz" to "0"),
            step = "0.1", samples = "11", includeInitial = true, reveal = "(time + 1) / duration")
        val options = EffectOptions(duration = 11)
        assertEquals(1, effect.calculate(center, options, 0.0).size)
        val end = effect.calculate(center, options, 10.0).last()
        assertEquals(2.0, end.x, 1e-12)
        assertEquals(9.0, end.y, 1e-12)
    }

    @Test
    fun `iteration maps update simultaneously and reset each point`() {
        val effect = IterationEffect("swap", mapOf("x" to "index", "y" to "1", "z" to "0"),
            listOf(mapOf("x" to "y", "y" to "x", "z" to "iteration")), iterations = "3", samples = "5")
        val points = effect.calculate(center, EffectOptions(), 0.0)
        points.forEachIndexed { i, p ->
            assertEquals(1.0, p.x)
            assertEquals(i.toDouble(), p.y)
            assertEquals(2.0, p.z)
        }
    }

    @Test
    fun `point output supports index count gradients age and parameter overrides`() {
        val effect = PointEffect("points", "amount", mapOf("x" to "index", "y" to "count", "z" to "time",
            "value" to "index / count", "age" to "time / duration"), mapOf("amount" to 5.0))
        val buffer = ParticleBuffer(center)
        effect.calculateInto(buffer, center, EffectOptions(parameters = mapOf("amount" to 3.0), duration = 10), 5.0)
        assertEquals(3, buffer.list().size)
        assertEquals(2.0, buffer.location(2).x)
        assertEquals(3.0, buffer.location(2).y)
        assertEquals(2.0 / 3.0, buffer.valueAt(2))
        assertEquals(0.5, buffer.ageAt(2))
    }

    @Test
    fun `nonfinite coordinates are skipped and sample limits bound work`() {
        val effect = PointEffect("bounded", "1000000000", mapOf("x" to "if(index == 0, sqrt(-1), index)", "y" to "0", "z" to "0"))
        assertEquals(FormulaEffect.MAX_POINTS - 1, effect.calculate(center, EffectOptions(), 0.0).size)
        assertThrows(IllegalArgumentException::class.java) {
            OdeEffect("invalid", mapOf("x" to "0"), mapOf("y" to "1"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            IterationEffect("invalid", mapOf("x" to "0"), listOf(mapOf("x" to "x")), selection = IterationEffect.Selection.BITS)
        }
    }

    @Test
    fun `cached geometry honors changing parameters centers transforms colors and uniform spin`() {
        val shared = PointEffect("cached", "4", mapOf("x" to "radius", "y" to "index", "z" to "0",
            "value" to "radius / 4", "age" to "time / duration", "spin" to "time * speed"),
            mapOf("radius" to 1.0, "speed" to 0.1))
        for (time in listOf(0.0, 1.0, 2.0, 0.0)) {
            val opts = EffectOptions(parameters = mapOf("radius" to if (time == 2.0) 3.0 else 1.0), rotationRoll = 0.3, scale = 2.0)
            val shifted = Location(null, time, 3.0, 5.0)
            val expected = PointEffect("fresh", "4", mapOf("x" to "radius", "y" to "index", "z" to "0",
                "value" to "radius / 4", "age" to "time / duration", "spin" to "time * speed"),
                mapOf("radius" to 1.0, "speed" to 0.1))
            assertEquals(expected.calculate(shifted, opts, time), shared.calculate(shifted, opts, time))
        }
        val rotation = PointEffect("rotation", "1", mapOf("x" to "1", "y" to "0", "z" to "0", "yaw" to "angle"))
        assertEquals(1, rotation.calculate(center, EffectOptions(parameters = mapOf("angle" to 0.0)), 0.0).size)
        assertTrue(rotation.calculate(center, EffectOptions(parameters = mapOf("angle" to Double.NaN)), 0.0).isEmpty())
    }

    @Test
    fun `cache includes initial values supplied under state variable names`() {
        val effect = OdeEffect("initial", mapOf("x" to "x"), mapOf("x" to "0"),
            samples = "1", parameters = mapOf("x" to 1.0))
        assertEquals(1.0, effect.calculate(center, EffectOptions(), 0.0).single().x)
        assertEquals(3.0, effect.calculate(center, EffectOptions(parameters = mapOf("x" to 3.0)), 0.0).single().x)
    }

    @Test
    fun `projection cache follows animated fields and does not freeze settling or gradients`() {
        val effect = FieldEffect("moving", "u - shift - time * 0.001", samples = "20", settle = "10",
            parameters = mapOf("shift" to 0.0))
        for (time in listOf(0.0, 5.0, 20.0, 5.0)) {
            val options = EffectOptions(parameters = mapOf("shift" to 0.2))
            val fresh = FieldEffect("fresh", "u - shift - time * 0.001", samples = "20", settle = "10",
                parameters = mapOf("shift" to 0.0))
            assertEquals(fresh.calculate(center, options, time), effect.calculate(center, options, time))
        }
    }

    @Test
    fun `shared generators calculate concurrent plays without leaking state`() {
        val effect = ProceduralFixtures.effect("julia")
        val pool = Executors.newFixedThreadPool(4)
        try {
            val jobs = (0 until 16).map { i ->
                val opts = EffectOptions(parameters = mapOf("cOrbit" to i / 100.0, "points" to 32.0))
                val expected = effect.calculate(center, opts, i.toDouble())
                pool.submit<Boolean> { expected == effect.calculate(center, opts, i.toDouble()) }
            }
            assertTrue(jobs.all { it.get() })
        } finally {
            pool.shutdownNow()
        }
    }
}
