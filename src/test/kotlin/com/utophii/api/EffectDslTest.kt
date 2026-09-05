package com.utophii.api

import com.utophii.modifiers.ColorModifier
import com.utophii.modifiers.RotationModifier
import com.utophii.modifiers.TurbulenceModifier
import com.utophii.modifiers.VortexModifier
import org.bukkit.Color
import org.bukkit.Particle
import org.bukkit.util.Vector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// verifies the type-safe DSL configures EffectOptions and modifier chains without a running server
class EffectDslTest {

    private fun assertColor(actual: Color?, expected: Color) {
        requireNotNull(actual)
        assertEquals(expected.red, actual.red)
        assertEquals(expected.green, actual.green)
        assertEquals(expected.blue, actual.blue)
    }

    @Test
    fun `effect config sets rendering fields`() {
        val opts = EffectConfig().apply {
            particle(Particle.HEART)
            color(Color.RED)
            toColor(Color.BLUE)
            dustSize(1.5f)
            scale(2.0)
            rotationYaw(0.5)
            tiltAngle(0.3)
            count(4)
            speed(0.1)
            offset(1.0, 2.0, 3.0)
            duration(120L)
        }.build()

        assertEquals(Particle.HEART, opts.particle)
        assertColor(opts.color, Color.RED)
        assertColor(opts.toColor, Color.BLUE)
        assertEquals(1.5f, opts.dustSize)
        assertEquals(2.0, opts.scale)
        assertEquals(0.5, opts.rotationYaw)
        assertEquals(0.3, opts.tiltAngle)
        assertEquals(4, opts.count)
        assertEquals(0.1, opts.speed)
        assertEquals(1.0, opts.offsetX)
        assertEquals(2.0, opts.offsetY)
        assertEquals(3.0, opts.offsetZ)
        assertEquals(120L, opts.duration)
    }

    @Test
    fun `isotropic offset spreads uniformly`() {
        val opts = EffectConfig().apply { offset(4.0) }.build()
        assertEquals(4.0, opts.offsetX)
        assertEquals(4.0, opts.offsetY)
        assertEquals(4.0, opts.offsetZ)
    }

    @Test
    fun `parameters populate numeric map and shortcuts`() {
        val opts = EffectConfig().apply {
            parameter("sigma", 10.0)
            parameter("steps", 200)
            radius(1.5)
            points(64)
        }.build()

        assertEquals(10.0, opts.parameters["sigma"])
        assertEquals(200.0, opts.parameters["steps"])
        assertEquals(1.5, opts.parameters["radius"])
        assertEquals(64.0, opts.parameters["points"])
    }

    @Test
    fun `modifiers config builds ordered chain with defaults`() {
        val opts = EffectConfig().apply {
            modifiers {
                turbulence()
                rotation()
                vortex()
                color(Color.RED, Color.BLUE)
            }
        }.build()

        assertEquals(4, opts.modifiers.size)
        assertTrue(opts.modifiers[0] is TurbulenceModifier)
        assertTrue(opts.modifiers[1] is RotationModifier)
        assertTrue(opts.modifiers[2] is VortexModifier)
        assertTrue(opts.modifiers[3] is ColorModifier)
    }

    @Test
    fun `modifier forwarded parameters produce different output`() {
        val opts = EffectConfig().apply {
            modifiers { turbulence(strength = 0.5, frequency = 2.0, octaves = 6) }
        }.build()

        val turb = opts.modifiers[0] as TurbulenceModifier
        val loc = org.bukkit.Location(null, 1.0, 2.0, 3.0)
        val out = turb.modify(loc, 0.0)
        // a non-zero strength must perturb at least one coordinate
        val displaced = Math.abs(out.x - 1.0) > 1.0E-9 ||
            Math.abs(out.y - 2.0) > 1.0E-9 ||
            Math.abs(out.z - 3.0) > 1.0E-9
        assertTrue(displaced, "turbulence modifier should displace the particle")
    }

    @Test
    fun `empty receiver audience stays empty`() {
        val opts = EffectConfig().apply { }.build()
        assertEquals(0, opts.receivers.size)
    }

    @Test
    fun `tilt axis is cloned into options`() {
        val axis = Vector(1.0, 0.0, 0.0)
        val opts = EffectConfig().apply { tiltAxis(axis) }.build()
        val cloned = requireNotNull(opts.tiltAxis)
        assertEquals(1.0, cloned.x)
        assertEquals(0.0, cloned.y)
        assertEquals(0.0, cloned.z)
    }

    @Test
    fun `parametric config builds an effect and render options`() {
        val spec = ParametricConfig().apply {
            variables("t")
            samples(8)
            range(0.0, 6.283185307179586)
            x("R * cos(t)")
            y("0")
            z("R * sin(t)")
            default("R", 2.0)
            render {
                scale(2.0)
                duration(80L)
            }
        }
        val effect = spec.buildEffect("ring")
        assertEquals("ring", effect.name)
        assertEquals(2.0, spec.options.scale)
        assertEquals(80L, spec.options.duration)

        // validate the DSL-built circle: 8 samples, first point at t=0 => x = R * scale = 4
        val positions = effect.calculate(org.bukkit.Location(null, 0.0, 0.0, 0.0), spec.options, 0.0)
        assertEquals(8, positions.size)
        assertEquals(4.0, positions[0].x, 1.0E-4)
    }
}
