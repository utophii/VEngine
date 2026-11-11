package com.utophii.effects

import com.utophii.api.EffectOptions
import com.utophii.api.FollowOptions
import com.utophii.engine.FollowAnchor
import com.utophii.modifiers.MotionModifier
import com.utophii.math.EasingType
import org.bukkit.Bukkit
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.math.PI

class OdeCollisionTest {
    private val serverField = Bukkit::class.java.getDeclaredField("server").apply { isAccessible = true }
    private var previousServer: Any? = null
    private var primaryThread = true
    private val boxes = mutableListOf(BoundingBox(1.0, -100.0, -100.0, 1.1, 100.0, 100.0))
    private var traces = 0
    private val world: World = proxy(World::class.java) { name, args ->
        when (name) {
            "getUID" -> UUID(0, 1)
            "getName" -> "collision-test"
            "rayTraceBlocks" -> {
                assertTrue(primaryThread)
                assertEquals(FluidCollisionMode.NEVER, args[3])
                assertEquals(true, args[4])
                traces++
                val start = (args[0] as Location).toVector()
                boxes.mapNotNull { it.rayTrace(start, args[1] as Vector, args[2] as Double) }
                    .minByOrNull { it.hitPosition.distanceSquared(start) }
            }
            else -> error("Unexpected World call: $name")
        }
    }
    private val center get() = Location(world, 0.0, 0.0, 0.0)

    @BeforeEach fun server() {
        previousServer = serverField.get(null)
        serverField.set(null, proxy(Server::class.java) { name, _ ->
            when (name) {
                "isPrimaryThread" -> primaryThread
                else -> error("Unexpected Server call: $name")
            }
        })
    }

    @AfterEach fun restoreServer() { serverField.set(null, previousServer) }

    private fun effect(
        response: OdeCollision.Response = OdeCollision.Response.BOUNCE,
        restitution: Double = 1.0,
        substeps: Int = 1,
        maxBounces: Int = 8,
        initial: Map<String, String> = emptyMap(),
        accelerations: Map<String, String> = emptyMap(),
        step: String = "1",
        samples: String = "3",
        includeInitial: Boolean = false,
        reveal: String = "1",
    ) = OdeEffect(
        "collision",
        initial = mapOf("x" to "0", "y" to "0", "z" to "0", "vx" to "10", "vy" to "0", "vz" to "0") + initial,
        derivatives = mapOf("x" to "vx", "y" to "vy", "z" to "vz", "vx" to "0", "vy" to "0", "vz" to "0") + accelerations,
        step = step, samples = samples, includeInitial = includeInitial, reveal = reveal,
        output = FormulaEffect.XYZ + mapOf("value" to "vx", "age" to "integrationTime / 10"),
        collision = OdeCollision(response, restitution, substeps, maxBounces),
    )

    @Test fun `disabled collisions preserve the ordinary async trajectory without accessing blocks`() {
        primaryThread = false
        val effect = effect(OdeCollision.Response.NONE)
        assertFalse(effect.calculateOnRender)
        assertEquals(listOf(10.0, 20.0, 30.0), effect.calculate(center, EffectOptions(), 0.0).map { it.x })
        assertEquals(0, traces)
    }

    @Test fun `terminate catches a thin block crossed by the first hidden step and includes the contact`() {
        val buffer = ParticleBuffer(center)
        effect(OdeCollision.Response.TERMINATE).calculateInto(buffer, center, EffectOptions(), 0.0)
        assertEquals(1, buffer.list().size)
        assertEquals(1.0, buffer.location(0).x, 1e-10)
        assertEquals(0.01, buffer.ageAt(0), 1e-10)
        assertEquals(1, traces)
    }

    @Test fun `stop holds all remaining samples at the surface with zero velocity`() {
        val buffer = ParticleBuffer(center)
        effect(OdeCollision.Response.STOP).calculateInto(buffer, center, EffectOptions(), 0.0)
        assertEquals(listOf(1.0, 1.0, 1.0), buffer.list().map { it.x })
        assertEquals(0.0, buffer.valueAt(2))
        assertEquals(1, traces)
    }

    @Test fun `bounce applies restitution to velocity and integrates the unused step`() {
        val buffer = ParticleBuffer(center)
        effect(restitution = 0.5).calculateInto(buffer, center, EffectOptions(), 0.0)
        assertEquals(-3.5, buffer.location(0).x, 2e-6)
        assertEquals(-8.5, buffer.location(1).x, 2e-6)
        assertEquals(-5.0, buffer.valueAt(1), 1e-10)
        assertEquals(0.2, buffer.ageAt(1), 1e-10)
    }

    @Test fun `bounce preserves tangential velocity`() {
        val points = effect(restitution = 0.0, initial = mapOf("vy" to "2")).calculate(center, EffectOptions(), 0.0)
        assertEquals(1.0, points[0].x, 2e-6)
        assertEquals(2.0, points[0].y, 1e-10)
        assertEquals(6.0, points[2].y, 1e-10)
    }

    @Test fun `multiple bounces use remaining time and the bounce cap stops at the last contact`() {
        boxes += BoundingBox(-1.1, -100.0, -100.0, -1.0, 100.0, 100.0)
        val point = effect(samples = "1").calculate(center, EffectOptions(), 0.0).single()
        assertEquals(0.0, point.x, 1e-5)
        assertEquals(6, traces)
        traces = 0
        val stopped = effect(maxBounces = 2).calculate(center, EffectOptions(), 0.0)
        assertTrue(stopped.all { kotlin.math.abs(it.x + 1.0) < 2e-6 })
        assertEquals(2, traces)
    }

    @Test fun `substeps detect a curved path whose endpoints are below the obstacle`() {
        boxes.clear()
        boxes += BoundingBox(-100.0, 1.8, -100.0, 100.0, 2.0, 100.0)
        val points = effect(OdeCollision.Response.TERMINATE, substeps = 8,
            initial = mapOf("y" to "1", "vx" to "0", "vy" to "4"),
            accelerations = mapOf("vy" to "-8"), samples = "1").calculate(center, EffectOptions(), 0.0)
        assertEquals(1.8, points.single().y, 1e-10)
    }

    @Test fun `gravity continues after contact rather than reflecting the finished free trajectory`() {
        boxes.clear()
        boxes += BoundingBox(-100.0, -1.0, -100.0, 100.0, 0.0, 100.0)
        val point = effect(substeps = 64, initial = mapOf("y" to "1", "vx" to "0"),
            accelerations = mapOf("vy" to "-2"), step = "2", samples = "1")
            .calculate(center, EffectOptions(), 0.0).single()
        assertEquals(1.0, point.y, 1e-4)
    }

    @Test fun `world geometry is queried again for each frame and each center`() {
        val effect = effect(OdeCollision.Response.TERMINATE, samples = "1")
        assertEquals(1.0, effect.calculate(center, EffectOptions(), 0.0).single().x)
        boxes.clear()
        assertEquals(10.0, effect.calculate(center, EffectOptions(), 0.0).single().x)
        assertEquals(30.0, effect.calculate(center.add(20.0, 0.0, 0.0), EffectOptions(), 0.0).single().x)
        assertEquals(3, traces)
    }

    @Test fun `scale roll translation and motion use the rendered world frame for bounce normals`() {
        boxes.clear()
        boxes += BoundingBox(-100.0, 22.0, -100.0, 100.0, 22.1, 100.0)
        val opts = EffectOptions(scale = 2.0, rotationRoll = PI / 2,
            modifiers = listOf(MotionModifier.to(Vector(10, 0, 0), 1, EasingType.LINEAR)))
        val points = effect(initial = mapOf("vx" to "2"), samples = "2")
            .calculate(Location(world, 0.0, 20.0, 30.0), opts, 1.0)
        assertEquals(10.0, points[0].x, 1e-9)
        assertEquals(20.0, points[0].y, 2e-6)
        assertEquals(16.0, points[1].y, 2e-6)
        assertEquals(30.0, points[1].z, 1e-9)
    }

    @Test fun `follow pose is applied before tracing and is not applied twice`() {
        val origin = Location(world, 100.0, 0.0, 0.0)
        val entity = proxy(Entity::class.java) { name, _ ->
            when (name) {
                "isValid" -> true
                "getLocation" -> center
                else -> error("Unexpected Entity call: $name")
            }
        }
        val buffer = ParticleBuffer(origin)
        effect(OdeCollision.Response.TERMINATE).calculateForRender(buffer, origin, EffectOptions(), 0.0,
            FollowAnchor(FollowOptions(entity), origin))
        assertEquals(1.0, buffer.list().single().x, 1e-10)
        assertSame(world, buffer.location(0).world)
    }

    @Test fun `initial visibility does not raycast until there is a step`() {
        val effect = effect(includeInitial = true, samples = "10", reveal = "(time + 1) / duration")
        assertEquals(0.0, effect.calculate(center, EffectOptions(duration = 10), 0.0).single().x)
        assertEquals(0, traces)
        assertEquals(2, effect.calculate(center, EffectOptions(duration = 10), 1.0).size)
        assertTrue(traces > 0)
    }

    @Test fun `follow across worlds queries the destination and buffer reuse restores the original world`() {
        var destinationTraces = 0
        val destination = proxy(World::class.java) { name, _ ->
            when (name) {
                "getUID" -> UUID(0, 2)
                "getName" -> "destination"
                "rayTraceBlocks" -> { destinationTraces++
                    null
                }
                else -> error("Unexpected World call: $name")
            }
        }
        val entity = proxy(Entity::class.java) { name, _ ->
            when (name) {
                "isValid" -> true
                "getLocation" -> Location(destination, 0.0, 0.0, 0.0)
                else -> error("Unexpected Entity call: $name")
            }
        }
        val buffer = ParticleBuffer(center)
        val effect = effect(OdeCollision.Response.TERMINATE)
        effect.calculateForRender(buffer, center, EffectOptions(), 0.0, FollowAnchor(FollowOptions(entity), center))
        assertEquals(3, destinationTraces)
        assertEquals(0, traces)
        assertTrue(buffer.list().all { it.world === destination })
        effect.calculateInto(buffer, center, EffectOptions(), 0.0)
        assertSame(world, buffer.list().single().world)
        assertEquals(1.0, buffer.location(0).x, 1e-10)
        assertEquals(1, traces)
    }

    @Test fun `external asynchronous collision calculation fails before touching blocks`() {
        primaryThread = false
        assertThrows(IllegalStateException::class.java) { effect().calculate(center, EffectOptions(), 0.0) }
        assertEquals(0, traces)
    }

    private fun <T> proxy(type: Class<T>, call: (String, Array<out Any?>) -> Any?): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, arguments ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(instance)
                "equals" -> instance === arguments?.get(0)
                "toString" -> "Test${type.simpleName}"
                else -> call(method.name, arguments ?: emptyArray())
            }
        })
}
