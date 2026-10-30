---
title: Developer guide
section: Developers
lede: "The type-safe Kotlin DSL: EffectConfig reference, atoms, modifiers, handles and the SPI."
---

> **Requirements:** Paper or Spigot **1.21.6 - 26.x**, Java 21+ (servers on 26.x run Java 25,
> but the plugin itself is built for Java 21 bytecode, so one jar covers the whole range).

The DSL is the recommended way to spawn effects from plugin code. Instead of assembling `EffectOptions`
by hand and calling `FXEngine.play(...)`, you get a readable, self-documenting configuration block that
can't be broken by a "wrong" signature.

```kotlin
player.location.playEffect("torus") {
    scale(2.0)
    color(Color.PURPLE)
    duration(120L)
    parameter("points", 240)
}
```

## 1. Quick start

VEngine is a multi-module build. Two artifacts matter to plugin developers:

| Artifact | What's inside | When you need it |
|---|---|---|
| `com.utophii:vengine-api` | Stable contracts only: `ParticleEffect`, `EffectOptions`, `EffectModifier`, `EffectHandle`, the `EffectProvider` SPI. **Zero engine internals.** | Implementing effects for the SPI, or passing VEngine types around your API |
| `com.utophii:vengine-core` | The engine + DSL (`playEffect`, `playParametric`) on top of `vengine-api` | Calling the DSL or `FXEngine` directly |

Server owners install a single shadow jar (`VEngine-<version>.jar`) that bundles everything -
the module split only concerns compile-time consumers.

Then import the DSL:

```kotlin
import com.utophii.api.playEffect
```

Minimal example - spawn an effect at a player's location:

```kotlin
val handle: EffectHandle? = player.location.playEffect("helix") {
    radius(1.0)
    height(3.0)
    turns(3.0)
    duration(80L)
}
```

`playEffect` is an extension function on [`org.bukkit.Location`](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Location.html).
It returns an [`EffectHandle`](https://github.com/utophii/VEngine/blob/main/vengine-api/src/main/kotlin/com/utophii/api/EffectHandle.kt) or `null` when no such
effect is registered.

> Requirement: `FXEngine.initialize(plugin)` must already have run (it happens in `VEngine.onEnable()`).

## 2. `EffectConfig` reference

All methods configure an effect and its **common** properties. The full table is below.

### Particle rendering and material

| Method | Type | Description | Example |
|---|---|---|---|
| `particle()` | `Particle` | Bukkit particle type. | `particle(Particle.FLAME)` |
| `color()` | `Color` | Primary color (start color for `DUST_COLOR_TRANSITION`). | `color(Color.fromRGB(0, 255, 160))` |
| `toColor()` | `Color` | End color for `DUST_COLOR_TRANSITION`. | `toColor(Color.BLUE)` |
| `gradient()` | `Color...` | Per-particle colors sampled from these stops, see [Gradients](writing-effects.md#gradients). | `gradient(Color.PURPLE, Color.ORANGE)` |
| `dustSize()` | `Float` | Dust particle size. | `dustSize(1.4f)` |
| `material()` | `Material` | Material for `BLOCK`/`FALLING_DUST` and `ITEM`. | `material(Material.AMETHYST_BLOCK)` |
| `count()` | `Int` | Number of particles per point (min 1). | `count(4)` |
| `speed()` | `Double` | "Extra" particle speed/parameter. | `speed(0.1)` |
| `offset(x, y, z)` | `Double, Double, Double` | Anisotropic spread per axis. | `offset(0.1, 0.1, 0.1)` |
| `offset(spread)` | `Double` | Isotropic spread on all axes. | `offset(0.2)` |
| `display()` | `DisplayOptions` | Render [display entities](#display-entities) at the points instead of particles. | `display(DisplayOptions(type = DisplayOptions.Type.ITEM))` |

### Display entities

`display(DisplayOptions(...))` places one item, block or text display entity at every point the
effect calculates, instead of a particle. `DisplayOptions` lives in `vengine-api`
(`com.utophii.api.DisplayOptions`) and mirrors the YAML `display:` block:

```kotlin
import com.utophii.api.DisplayOptions
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.util.Vector

player.location.playEffect("helix") {
    duration(160L)
    parameters(mapOf("radius" to 1.4, "height" to 0.0, "turns" to 1.0, "points" to 6.0))
    display(
        DisplayOptions(
            type = DisplayOptions.Type.ITEM,
            material = Material.AMETHYST_CLUSTER,
            size = Vector(0.7, 0.7, 0.7),
            spin = Vector(0.0, 0.12, 0.0),      // radians per tick
            phase = 5.0,                        // ticks of spin offset between pieces
            blockLight = 15, skyLight = 15,
            glowColor = Color.fromRGB(0xB26BFF),
        )
    )
}
```

| Property | Default | Description |
|---|---|---|
| `type` | `ITEM` | `ITEM`, `BLOCK` or `TEXT`. |
| `material` | `AMETHYST_SHARD` | Item model (`ITEM`) or block state (`BLOCK`). |
| `text` | empty | MiniMessage lines for `TEXT`, point `i` shows `text[i % text.size]`. |
| `size` | `(0.5, 0.5, 0.5)` | Per-axis model scale in blocks, multiplied by `scale`. |
| `rotation` / `spin` | zero | Base local rotation (radians, X→Y→Z) and angular velocity (radians/tick). |
| `phase` | `0.0` | Spin offset in ticks between consecutive points. |
| `billboard` | `null` | `null` = `FIXED` for item/block, `CENTER` for text. |
| `blockLight` / `skyLight` | `null` | Forced light `0..15`, `null` = world light. |
| `glowColor` | `null` | Outline glow color, `null` = no glow. |
| `interpolationTicks` | `2` | Client interpolation for movement and transformation (`0..59`). |
| `viewRange` | `null` | Render distance multiplier (`1.0` = 64 blocks), `null` = engine view distance. |
| `shadowRadius` / `shadowStrength` | `0.0` / `1.0` | Ground shadow. |
| `textBackground` / `textShadow` / `seeThrough` | `null` / `false` / `false` | `TEXT` styling. |
| `withParticles` | `false` | Also spawn the effect's particles at the same points. |

Notes:

- Works with every effect - built-in atoms, formula atoms, scripted layers and your own
  `AbstractParticleEffect` subclasses - because the swap happens in the shared frame renderer.
- Entities are spawned on the server thread, are non-persistent, are capped at 256 per effect
  (or per scripted layer), and are removed when the handle is cancelled or the effect ends.
- With `receivers(...)` set, the entities are hidden from every other player.
- YAML reference and animation targets (`display.size`, `display.rotation.x/y/z`):
  [Display entities](display-entities.md).

### Transformations

| Method | Type | Description | Example |
|---|---|---|---|
| `scale()` | `Double` | Geometric scale. | `scale(2.0)` |
| `rotationYaw()` | `Double` | Rotation around the vertical (Y) axis, **radians**. | `rotationYaw(PI / 3)` |
| `rotationPitch()` | `Double` | Tilt around the X axis - leans the effect forward/backward, **radians**. | `rotationPitch(PI / 4)` |
| `rotationRoll()` | `Double` | Tilt around the Z axis - leans the effect sideways, **radians**. | `rotationRoll(0.3)` |
| `orientation()` | `yaw, pitch, roll` | Sets all three euler angles at once. | `orientation(pitch = PI / 2)` |
| `tiltAxis()` | `Vector` | Arbitrary tilt axis (advanced, applied after the euler angles). | `tiltAxis(Vector(1.0, 0.0, 0.0))` |
| `tiltAngle()` | `Double` | Tilt angle around `tiltAxis`, **radians**. | `tiltAngle(0.4)` |

Orientation notes:

- Euler order is **roll → pitch → yaw**: the effect tilts in its own local frame first, then spins
  around the world Y axis. Angles are additive for scripted layers (layer angle + runtime angle).
- Common radian values: `30° = 0.524`, `45° = 0.785`, `90° = PI / 2`, `180° = PI`.
  In Kotlin you can write `Math.toRadians(45.0)`.
- All angles are supported in scripted YAML too (`rotationYaw`, `rotationPitch`, `rotationRoll`
  on a layer) and can be animated over time with `animation` tracks targeting
  `rotationYaw` / `rotationPitch` / `rotationRoll`:

  ```yaml
  layers:
    - effect: beam
      particle: END_ROD
      rotationPitch: 1.5708        # a vertical beam becomes a horizontal beam
      animation:
        tracks:
          - target: rotationRoll   # ...which then slowly rolls around
            keyframes:
              - {tick: 0,   value: 0}
              - {tick: 100, value: 3.1416}
  ```
- Vanilla particles themselves are camera-facing sprites and **cannot be rotated individually** -
  VEngine rotates the arrangement of points that make up the effect, never the sprite.

### Time, audience and modifiers

| Method | Type | Description | Example |
|---|---|---|---|
| `duration()` | `Long` | Duration in **ticks** (20 t = 1 s). | `duration(120L)` |
| `receivers(vararg)` | `Player...` | Restrict to an explicit audience. Empty = everyone nearby. | `receivers(player, staff)` |
| `receivers(list)` | `Iterable<Player>` | Same, from a collection. | `receivers(onlinePlayers)` |
| `follow()` | `Entity, Vector?, FollowRotation?, Boolean?` | Bind the effect to an entity. `null` arguments use the effect's `follow:` block. See [Following entities](following-entities.md). | `follow(player, rotation = FollowRotation.BODY)` |
| `subject()` | `Player?` | Player whose PlaceholderAPI values fill `%placeholders%` in the effect's YAML. Defaults to the followed player. | `subject(player)` |
| `budgetExempt()` | `Boolean` | Ignore the particle limits and auto-quality, like `budget: exempt` in YAML. | `budgetExempt()` |
| `lod()` | `Boolean` | `false` keeps full density at any distance, like `lod: false`. | `lod(false)` |
| `modifiers { }` | lambda | Nested [modifier DSL](#4-modifiers). | `modifiers { vortex() }` |
| `parameter()` | `String, Double/Int` | Effect-specific numeric parameter. | `parameter("majorRadius", 1.4)` |
| `parameters()` | `Map<String, Double>` | Several parameters at once. | `parameters(mapOf(...))` |
| `radius()` | `Double` | Shortcut for `parameter("radius", value)`. | `radius(1.5)` |
| `points()` | `Int` | Shortcut for `parameter("points", value)`. | `points(240)` |

The default `duration` is `1` tick (`EffectOptions.DEFAULT_DURATION_TICKS`). Always set it explicitly
if you want a live, animated effect.

`Entity.playEffect(name, offset, rotation) { ... }` is a shortcut that calls `follow()` for the
entity:

```kotlin
player.playEffect("wings", offset = Vector(0.0, 1.2, -0.3), rotation = FollowRotation.BODY) {
    duration(EffectOptions.INFINITE_DURATION)
}
arrow.playEffect("comet_trail", rotation = FollowRotation.VELOCITY)
```

## 3. Primitive atoms and their parameters

VEngine is an **engine for creating effects**, not a "catalog of ready-made ones". Everything is built
on **atoms**: a small set of building blocks from which you compose effects (YAML layers, formulas,
modifiers, DSL). Most atoms are **formulas** (see [Parametric Effects](parametric-effects.md)). Only the
irreducible ones - a uniform sphere and numerically integrated systems - remain as code.

### Built-in atoms (Kotlin)
`sphere`, `rk4_trajectory`, the strange attractors (`lorenz`, `aizawa`, `thomas`, `rossler`,
`halvorsen`), the fractals (`julia`, `sierpinski_triangle`, `sierpinski_tetrahedron`,
`sierpinski_carpet`, `menger_sponge`) and the Chladni plates (`chladni`, `chladni_circle`) are
always registered. Every parameter with its default is listed in
[Built-in primitives](built-in-primitives.md).

```kotlin
location.playEffect("julia") {
    duration(200)
    parameter("radius", 1.8)
    parameter("cOrbit", 0.06)
}
```

### Formula atoms (YAML in `effects/`)
Atoms defined by formulas. Their parameters are the formula variables (via `parameter`/`parameters`).
| Name | Key parameters |
|---|---|
| `helix` | `radius` (1.0), `height` (3.0), `turns` (3.0) |
| `torus` | `majorRadius` (1.4), `minorRadius` (0.45) |
| `lissajous` | `amplitudeX` (1.6), `amplitudeZ` (1.6), `a` (3.0), `b` (2.0), `delta` (π/2) |
| `rosette` | `majorRadius` (1.0), `minorRadius` (0.25), `distance` (0.9) |
| `torus_knot` | `majorRadius` (1.6), `minorRadius` (0.6), `p` (2.0), `q` (3.0) |
| `beam` | `length` (5.0), `height` (1.2), `waveSpeed` (0.2), `waveAmplitude` (0.08) |
| `spline_beam` | `length` (6.0), `arcHeight` (1.4), `waveAmplitude` (0.12), `waveFrequency` (3.0), `waveSpeed` (0.25) |
| `supershape` | `radius` (1.6), `m1/a1/b1/n11/n12/n13`, `m2/a2/b2/n21/n22/n23` |

> Common to all atoms: `points` / `samples` (density) and `angularSpeed` (animation) are overridden
> on the fly and can be set in any `playEffect`/DSL/YAML layer. For example, `parameter("points", 96)`
> increases density.

## 4. Modifiers

`modifiers { }` is a nested DSL that collects a chain of particle-position post-processing. The chain
is applied in declaration order.

```kotlin
player.location.playEffect("torus_knot") {
    scale(1.5)
    duration(200L)
    modifiers {
        vortex(strength = 0.4)              // hydrodynamic vortex
        rotation(angularVelocity = 0.08)    // rotation around an axis
        turbulence(strength = 0.18)         // fBm turbulence
        color(Color.RED, Color.BLUE)        // time-based color pulse
    }
}
```

| Method | Positional args | Default | Description |
|---|---|---|---|
| `turbulence()` | `strength, frequency, octaves` | `0.12, 0.35, 4` | Deterministic fBm displacement. |
| `rotation()` | `axis, angularVelocity, initialAngle, pivotOffset` | `Y-axis`, `0.05`, `0.0`, `0.0` | Rotation around an axis through center + `pivotOffset`. |
| `vortex()` | `axis, coreRadius, strength` | `Y-axis`, `1.2`, `0.15` | Vortex rotation with Gaussian falloff. |
| `color()` | `from, to, periodTicks` | `-`, `-`, `40.0` | Smooth color shift over time (doesn't affect positions). |
| `motion()` | `mode, path { }` | `HOLD` | Moves the whole effect along a waypoint path (see below). |
| `motionTo()` | `x, y, z, ticks, easing, mode` | `HOLD`, sine easing | Single-hop glide to a spawn-relative offset. |
| `add()` | `EffectModifier` | - | Add an arbitrary/custom modifier. |

Defaults come from the modifiers' public constants (`TurbulenceModifier.DEFAULT_STRENGTH` etc.), so
`vortex()` with no args `===` `VortexModifier()`.

### Moving effects along a path (`motion`)

`MotionModifier` translates **all** particles of an effect over time - the shape itself keeps
animating while the whole effect glides through the world. Waypoint offsets are **relative to the
spawn point**: at time 0 the displacement is `(0, 0, 0)`, so the effect always starts exactly where
you spawned it.

```kotlin
player.location.playEffect("helix") {
    radius(0.8)
    duration(200L)
    modifiers {
        motion(mode = MotionModifier.Mode.HOLD) {       // how the path ends: HOLD | LOOP | PING_PONG
            to(x = 10.0, y = 0.0, z = 0.0, ticks = 80L)                      // glide +10 blocks east
            to(x = 10.0, y = 5.0, z = 0.0, ticks = 40L, easing = EasingType.EASE_OUT_CUBIC)
        }
    }
}

// single-hop convenience
loc.playEffect("beam") {
    duration(60L)
    modifiers { motionTo(x = 0.0, y = 8.0, z = 0.0, ticks = 40L) }
}
```

To move an effect to **absolute coordinates**, use `moveTo` on `EffectConfig` - it is resolved
against the spawn location, so the effect's center lands exactly on the target:

```kotlin
val spawn = player.location
spawn.playEffect("torus_knot") {
    duration(160L)
    moveTo(spawn.clone().add(20.0, 3.0, 0.0), ticks = 100L)   // glide to an exact coordinate
    moveTo(spawn.clone().add(20.0, 8.0, 0.0), ticks = 40L)    // ...then rise another 5 blocks
}
```

The same is available in scripted YAML effects as a layer modifier:

```yaml
modifiers:
  - type: motion
    mode: hold                 # hold | loop | ping_pong
    easing: ease_in_out_sine   # default for legs without their own easing
    path:
      - {x: 10, y: 0, z: 0, ticks: 40}
      - {x: 10, y: 5, z: 0, ticks: 20, easing: ease_out_cubic}
```

Notes:
- Displacement is recomputed every frame, so view-distance culling follows the moving effect.
- `mode: loop` jumps back to the spawn point each cycle (handy for patrol-like paths),
  `ping_pong` travels the path back and forth smoothly.
- Because motion is an `EffectModifier`, it composes with every primitive, formula atom and scripted
  layer, and stacks with `rotation`, `vortex`, etc.

### A custom modifier

```kotlin
val myModifier = EffectModifier { loc, _ ->
    loc.clone().add(0.0, 1.0, 0.0)   // raises the particle by one block
}

player.location.playEffect("torus") {
    modifiers {
        add(myModifier)
    }
}
```

## 5. Complete example

```kotlin
import com.utophii.api.EffectHandle
import com.utophii.api.playEffect
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.util.Vector

fun spawnPartyEffect(player: Player): EffectHandle? =
    player.location.playEffect("spline_beam") {
        // material and rendering
        particle(Particle.DUST_COLOR_TRANSITION)
        color(Color.fromRGB(120, 80, 255))
        toColor(Color.fromRGB(0, 255, 200))
        dustSize(1.2f)

        // geometry
        scale(1.4)
        rotationYaw(player.location.yaw * PI / 180.0)
        tiltAxis(Vector(0.0, 0.0, 1.0))
        tiltAngle(0.15)
        offset(0.05, 0.05, 0.05)

        // time and audience
        duration(100L)
        receivers(player)

        // modifiers
        modifiers {
            vortex(axis = Vector(0.0, 1.0, 0.0), coreRadius = 1.5, strength = 0.3)
            rotation(angularVelocity = 0.1)
        }

        // effect-specific parameters
        parameter("length", 8.0)
        parameter("arcHeight", 2.0)
        points(160)
    }
```

| Common property | What it changes |
|---|---|
| `offset(x, y, z)` | Random spread of particles around a point → effect "thickness". |
| `scale` | Size of the whole shape. |
| `rotationYaw` / `tiltAxis`+`tiltAngle` | Shape orientation in space (full 3D rotation). |
| `count` | Particle density at each position. |
| `speed` | Bukkit particle speed parameter. |

## 6. Controlling a running effect

`playEffect` returns an `EffectHandle?` that lets you stop the effect early:

```kotlin
val handle = player.location.playEffect("lorenz") {
    duration(600L)
}

// cancel the effect (e.g. on player quit)
handle?.let { it.cancel() }

// check its state
if (handle?.isCancelled == true) { /* ... */ }
```

It's handy to keep a `handle` in a `UUID -> EffectHandle` map and cancel on `PlayerQuitEvent`:

```kotlin
val handles = mutableMapOf<java.util.UUID, com.utophii.api.EffectHandle>()

handles[player.uniqueId] = player.location.playEffect("torus") {
    duration(200L)
}

// listener
playerQuitEvent.player.uniqueId?.let { handles.remove(it)?.cancel() }
```

For global control from the plugin, `FXEngine` methods are available:

```kotlin
FXEngine.stopAll()                 // stop everything, return the count
FXEngine.stop(handle.id)           // stop by id
FXEngine.activeHandles()           // list of active effects
```

`handle.id` and `handle.effectName` are available anywhere.

## 7. Debugging and FAQ

**Effect doesn't appear - what to check?**
1. The effect name is registered: `FXEngine.effectNames()` (includes both primitives and `.yml` scripts).
2. `duration > 0` - default is `1` tick.
3. The player is within the `view-distance` from `config.yml` (default 64 blocks). Otherwise the particle
   is culled and never sent to the player.
4. `particle` supports color/material (for `DUST`/`DUST_COLOR_TRANSITION`/`BLOCK`/`ITEM` the matching
   parameter is required).

**Why doesn't `particle(Particle.DUST)` show my color?**
For `DUST` the color comes from `color`. For `DUST_COLOR_TRANSITION` from `color` and `toColor`. For
`BLOCK`/`FALLING_DUST` from `material(block)`.

**Radians or degrees?**
All angles are in **radians**. Remember: `PI` comes from `kotlin.math`.

**How do I run a scripted (YAML) effect?**
The same way - by its registry name. Scripted effects override primitives with the same name.

**Another plugin calls `FXEngine.play(...)`. Does the DSL replace this?**
Yes, but the old API remains. `playEffect` internally just calls
`FXEngine.play(name, this, EffectConfig().apply(config).build())`.

## 8. How it works internally (for the curious)

- Pattern: extension function + `@DslMarker` annotation (`@VEngineDsl`).
- `Location.playEffect` creates an `EffectConfig`, applies the config block, builds `EffectOptions`
  via `build()` and hands it to `FXEngine.play(...)`.
- `EffectConfig` wraps `EffectOptions.builder()`, and all its methods delegate to the builder.
- `ModifierConfig` collects a `List<EffectModifier>` in call order.
- `@VEngineDsl` forbids referencing an outer DSL receiver from a nested block - preventing accidental
  "invisible" calls to the parent config's methods.

Module: `com.utophii.api.PlayEffectDsl`. Entry point: `com.utophii.api.playEffect`.

## 9. VEngine as an engine: extension via SPI

The public engine API is not "ready-made effects" but the **ability to add your own**:

- `engine/` - registry, scheduler, lifecycle (`FXEngine`, `EffectScheduler`, `EffectHandle`).
- `api/` - contracts (`ParticleEffect`, `EffectModifier`), DSL (`playEffect`, `playParametric`) and SPI (`EffectProvider`).
- `math/` - expressions (formulas), modifiers, core config.

### Registering effects from an external plugin

Implement `EffectProvider` and register it in `onEnable`:

```kotlin
import com.utophii.api.EffectProvider
import com.utophii.api.ParticleEffect
import com.utophii.engine.FXEngine

// any class implementing ParticleEffect (your own effect or your own formula)
class MyEffect : ParticleEffect {
    override val name = "my_beam"
    override fun play(center: Location, opts: EffectOptions): EffectHandle = // ...
    override fun calculate(center: Location, opts: EffectOptions, time: Double): List<Location> = // ...
}

override fun onEnable() {
    FXEngine.registerProvider(EffectProvider { listOf(MyEffect()) })
}
```

The registered effect becomes a **primitive atom**: available via `/ve play`, `FXEngine.effect(name)`, the
DSL `playEffect("my_beam")`, and it can be used as a base layer in YAML scripts.

Key `FXEngine` methods:
- `registerProvider(provider)` - register an external effect provider (SPI).
- `registerEffect(effect)` - register a single effect manually.
- `primitiveEffect(name)` / `effect(name)` - look up an effect/atom.
- `stopAll()` / `stop(id)` / `activeHandles()` - manage running effects.

> "Engine vs content": the built-in atoms are **examples** (in `resources/effects/`), and the real
> product is the **ability to create effects** (formulas, layers, modifiers, and via SPI). So `effects/`
> isn't a "catalog" but included building blocks that can be replaced or extended.
