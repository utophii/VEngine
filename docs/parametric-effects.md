---
title: Parametric effects
section: Server owners
lede: "Describe new shapes with math formulas: curves, surfaces, syntax and schema."
---

VEngine lets you **create new effects directly in configuration**, describing them with mathematical
formulas. No Kotlin class needed - just YAML or the DSL.

The three bundled examples are `basic`, `animated` and `display`. Geometry comes from
configuration. Differential equations and iterated maps use [procedural generators](procedural-generators.md).

## How it works

- **Curve:** several variables (usually `t`), with `x/y/z` formulas of them, sampled over a range.
- **Surface:** two variables (`theta`, `phi`), with `x/y/z` formulas, sampled over a 2D grid.
- Formulas use [exp4j](https://www.objecthunter.net/exp4j/) math syntax with VEngine's lazy
  conditions and local functions. Only math is evaluated. Formulas cannot run scripts or access files.

## Formula syntax

Supported:

- operators `+ - * / % ^`, parentheses
- comparisons `< <= > >= == !=` and short-circuit logical operators `&& ||`
- functions `sin cos tan asin acos atan sinh cosh tanh log log10 log2 exp sqrt cbrt abs ceil floor round signum pow min max`
- `noise(x, y, z)`, `clamp(value, low, high)`, `mix(a, b, factor)`, `if(condition, yes, no)`
- constants `pi` (≈3.1416), `e` (≈2.718), plus `tau`, `TAU` (≈6.2832), `PI`
- variables you declare (`t`, `theta`, `phi`, …) and any named parameters (`R`, `r`, …)
- the **magic variable `time`** - the current effect time (in ticks). It is available in every formula
  and lets you animate the shape directly (e.g. a wave) independently of `angularSpeed`.

> Curves such as helixes and torus knots are formula recipes. Optional definitions are kept in
> the [recipe archive](built-in-primitives.md), outside the plugin jar.

> ⚠️ Multiplication is **explicit**: write `R * cos(t)`, not `R cos(t)`.

### Noise, limits, blending and conditions

| Function | Meaning | Example |
|---|---|---|
| `noise(x, y, z)` | Deterministic, smooth value noise in `[-1, 1]`. All three coordinates are required. Use zero for unused dimensions. | `noise(t * 0.4, time * 0.02, 0)` |
| `clamp(value, low, high)` | Limits a value to the inclusive range. `low` must be at most `high`. | `clamp(time / 40, 0, 1)` |
| `mix(a, b, factor)` | Linear interpolation: `a * (1 - factor) + b * factor`. Factors outside `[0, 1]` extrapolate. | `mix(0.5, 2, clamp(time / 40, 0, 1))` |
| `if(condition, yes, no)` | Evaluates only the selected branch. Zero is false. Any nonzero value is true. | `if(t != 0, sin(t) / t, 1)` |

Comparisons produce `1` or `0`. Arithmetic binds before comparisons, ordering comparisons before
equality, then `&&`, then `||`. Use parentheses when combining conditions for readability.
For example, `if(time < 20 || time >= 80, 0, 1)` enables only the middle part of an effect.
`&&` and `||` skip their right operand when the left operand already determines the result.
Both branches must be valid expressions, even when one branch is never selected.

Noise repeats exactly for the same coordinates and does not advance a random generator. To animate
it, include `time` in a coordinate. Its algorithm matches the value noise used by the turbulence
modifier. It is not Perlin or simplex noise.

### Local functions

Put `functions` at the **top level of the effect file**, alongside `curve`, `surface`, `sdf` or `layers`.
Each quoted signature maps to a formula string:

```yaml
id: noisy_ring
functions:
  'wave(u, amplitude)': 'amplitude * sin(u)'
  'height(u, tick)': 'wave(u * 3, 0.25) + 0.15 * noise(u, tick * 0.02, 0)'
curve:
  variables: [t]
  samples: 96
  angularSpeed: 0
  x: '2 * cos(t)'
  y: 'height(t, time)'
  z: '2 * sin(t)'
particle: END_ROD
duration: 200
```

Functions are local to their file and compiled when the effect loads. They are available in
coordinate, `value` and `age` formulas, SDF formulas, and existing expression fields such as layer `scale`,
layer `parameters`, and the file's `duration`. A layer calling an atom from another file does not
replace that atom's functions. Reloading recompiles the definitions.

All inputs must be explicit arguments: pass `time`, sampling variables, runtime parameters or
PlaceholderAPI values at the call site. A function body can use its arguments, math constants,
built-in functions and other functions in the same file. Forward references and zero-argument
functions such as `'quarterTurn()': 'PI / 2'` are supported.

Function and argument names use letters, digits and underscores and cannot start with a digit.
Names are case-sensitive. Built-in names, duplicate function names, overloads, duplicate argument
names and recursive calls (including indirect recursion) are rejected with a loading warning.
Arguments cannot reuse function or constant names. Use quoted strings for function bodies, even
constant ones: `'one()': '1'`.

Definitions are checked without substituting guessed values for variables. A structurally valid
formula can still produce an arithmetic error for particular inputs. Use `if` to guard division or
other operations with restricted domains. Expressions are limited to 16,384 characters, 128 levels
of nesting (including function calls), and 128 local function definitions per file.
The expanded calculation is also limited to 16,384 evaluation steps, so a chain of functions
cannot accidentally multiply the per-particle work without bound. These limits are checked at load time.

## 1. A curve in YAML

```yaml
# rhodonea (rose) curve
id: parametric_rose
curve:
  variables: [t]                # one variable => curve
  samples: 180                  # number of points
  angularSpeed: 0.03            # animation speed (from time)
  range:
    - { min: 0, max: 6.283185307179586 }
  x: "R * cos(K * t) * cos(t)"  # X formula
  y: "0"                        # Y formula
  z: "R * cos(K * t) * sin(t)"  # Z formula
  parameters:
    R: 2.0
    K: 3.0
particle: DUST
color: { r: 255, g: 100, b: 220 }
scale: 1.0
duration: 200
```

## 2. A surface in YAML

```yaml
id: parametric_torus
surface:
  variables: [theta, phi]       # two variables => surface
  samples: [48, 20]             # [longitude, latitude]
  angularSpeed: 0.05
  range:
    - { min: 0, max: 6.283185307179586 }
    - { min: 0, max: 6.283185307179586 }
  x: "(R + r * cos(phi)) * cos(theta)"
  y: "r * sin(phi)"
  z: "(R + r * cos(phi)) * sin(theta)"
  parameters:
    R: 1.4
    r: 0.45
particle: DUST
color: { r: 120, g: 210, b: 255 }
scale: 1.0
duration: 200
```

## The `curve` / `surface` block schema

| Key | Type | Default | Description |
|---|---|---|---|
| `variables` | `[String]` | `[t]` / `[theta, phi]` | Variable names. 1 → curve, 2+ → surface. |
| `samples` | `Int` or `[Int, Int]` | `64` / `[40, 20]` | Number of samples (longitude/latitude for a surface). |
| `angularSpeed` | `Double` | `0.05` | Time multiplier → phase (animation). |
| `range` | `[{min,max}, ...]` | `[0, tau]` per variable | Range of each variable. |
| `x` / `y` / `z` | `String` | `0` | Coordinate formulas. |
| `value` | `String` | drawing order | Per-point position on the spatial `gradient`, in `0…1`. |
| `age` | `String` | layer age | Per-point normalized age for `colorOverLife` and `sizeOverLife`, clamped to `0…1`. |
| `parameters` | `Map<String, Double>` | - | Default values for parameter variables. |
| `normalX` / `normalY` / `normalZ` | number or `String` | - | Direction each fixed display model faces. All three or none. See [orienting display models](#orienting-display-models). |
| `rotX` / `rotY` / `rotZ` | number or `String` | `0` | Angles of each fixed display model in radians. |
| `alignToNormal` | `outward` / `inward` | - | `surface` only. Faces each fixed display model along the computed surface normal. |

Common fields (`particle`, `color`, `toColor`, `material`, `count`, `offset`, `speed`, `scale`,
`rotationYaw`, `tiltAxis`, `tiltAngle`, `duration`, `modifiers`) are set at the top level and work
the same way as for the other effects. Any runtime `parameters` (e.g. from modifiers) override the
`curve.parameters` values during rendering.

### Individual point age

Use `age` inside a `curve` or `surface` block to give points separate lifetimes. It can use the
sampling variables, `time` in ticks, parameters and local functions, just like the coordinate
formulas. `age` and `value` are independent: a spatial gradient can stay fixed while size changes
with age. Without `age`, points use the [layer's age](writing-effects.md#color-and-size-over-life).

The bundled `basic` effect grows and fades around a ring:

```yaml
id: age_wave
duration: 81
particle: DUST
dustSize: 1.2
gradientInterpolation: hsv
colorOverLife: ["#ffbb22", "#ff3366", "#5522cc"]
sizeOverLife: [0, 1, 0]
curve:
  variables: [t]
  samples: 96
  angularSpeed: 0
  range: [{min: 0, max: 1}]
  x: '2 * cos(t * tau)'
  y: '0'
  z: '2 * sin(t * tau)'
  age: '(time - t * 40) / 40'
```

Each point starts at tick `t * 40` and lives for 40 ticks. Negative ages clamp to `0`, ages above
`1` clamp to `1`. The zero size stops keep points hidden before birth and after death. Age alone
does not remove a point: a nonzero final size remains visible until the effect ends. `angularSpeed`
also shifts the sampling variables seen by `age`. Use zero when the birth schedule should stay
attached to the same points. Infinite effects can animate individual ages explicitly.

Age formulas must produce finite numbers. A NaN or infinite result fails the frame with
`Particle age must be finite`. Use `if` to guard invalid divisions or square roots.

### Orienting display models

A layer that renders [display entities](display-entities.md) with `billboard: fixed` can turn
every model on its own. Add one of these to the `curve` or `surface` block:

- `normalX`, `normalY`, `normalZ` - the direction the front of the model faces, in local effect
  coordinates. All three are required, write `0` for a zero component. The length does not matter.
- `rotX`, `rotY`, `rotZ` - angles in radians, composed exactly like `display.rotation`. Missing
  components are `0`.
- `alignToNormal: outward` or `alignToNormal: inward` - `surface` only. The normal is computed from
  the surface itself, see [automatic surface normals](#automatic-surface-normals).

Each value is a number or a formula. Formulas see the same variables as the coordinates: the
sampling variables with the current phase, `time`, parameters and local functions. Every field is
evaluated on its own, so `normalX: x` only works when `x` is a sampling variable. Reuse a
coordinate expression through a [local function](#local-functions).

```yaml
id: outward_letters
duration: 200
curve:
  variables: [t]
  samples: 12
  angularSpeed: 0
  x: '2 * cos(t)'
  y: 0
  z: '2 * sin(t)'
  normalX: 'cos(t)'
  normalY: 0
  normalZ: 'sin(t)'
display:
  type: text
  text: 'F'
  billboard: fixed
```

The twelve letters stand upright and each one is readable from outside the ring, so a player can
walk around it. The letter at `(2, 0, 0)` faces `+X`, the one at `(0, 0, 2)` faces `+Z`.

**When it applies.** Orientation only affects point models with `billboard: fixed`. Text and pixels
default to `center`, so write `billboard: fixed` for them. With `center`, `vertical` or
`horizontal`, in particle-only layers and on ribbons the fields are ignored and cost nothing. This is
decided by the layer's final options, so the fields can live in a named atom that one layer renders as
text and another as particles.

**Which side is the front.** A text model is readable from its local `+Z` side. A normal turns `+Z`
onto the normal and keeps the top as close to `+Y` as possible. A normal that points almost straight
up or down (`|normalY| >= 0.9999` after normalizing) takes its top from `+Z` instead, so the top can
switch direction as a normal passes the pole. Use `rotX`/`rotY`/`rotZ` for a controlled roll.

**How rotations combine.** The model rotation is `follow × layer × point × display`, the rightmost
acting first:

- `display.rotation` and `display.spin` act in the model's own frame. `display.rotation.z: 1.5708`
  turns every letter a quarter turn within its plane, and `display.spin.z` spins letters in place
  while they keep facing along their normals.
- `rotationRoll`, `rotationPitch`, `rotationYaw` and `tiltAxis`/`tiltAngle` turn oriented models
  together with the shape. A ring tilted by `rotationPitch` keeps its letters facing outward.
- An effect that follows an entity with `rotation:` turns its models once more, together with the shape.

Without any orientation field, models keep their earlier behavior: layer rotations move the points
but leave the models as they were. An explicit `rotZ: 0` already opts in.

Use one orientation per block. Combining `normal*` with `rot*`, or either with `alignToNormal`, is a
load error. For a correction shared by all models use `display.rotation`. A zero or non-finite normal,
or a non-finite angle, keeps only the layer rotation for that point in that frame. The point itself,
its color and its age stay as they are.

#### Automatic surface normals

`alignToNormal: outward` faces each model along `normalize(dP/du × dP/dv)`, where `u` and `v` are
the first and second names in `variables`. `inward` faces the opposite way. The two words name the
sides of the parametrization, not of the shape: swapping the variables or mirroring a formula flips
them, and an open surface may have no inside at all. Reversing a `range` does not flip the side.

```yaml
id: cylinder_labels
duration: 200
surface:
  variables: [u, v]
  samples: [24, 4]
  angularSpeed: 0
  range:
    - {min: 0, max: 6.283185307179586}
    - {min: -1, max: 1}
  x: '2 * cos(u)'
  y: 'v'
  z: '-2 * sin(u)'
  alignToNormal: outward
display:
  type: text
  text: 'F'
  billboard: fixed
```

Here `dP/du × dP/dv = (2 cos(u), 0, -2 sin(u))` points away from the axis, so `outward` shows the
readable side to players outside and `inward` shows it to a player standing inside. With
`z: '2 * sin(u)'` the same cylinder would need `inward` to be readable from outside. Positions and
model counts are the same in both modes. A plane `x: u, y: v, z: 0` faces `+Z` with `outward`.

Normals are finite differences on the grid sampled in the current frame: central differences inside,
second-order one-sided differences on the edges. Opposite edges are never joined, even for a
`[0, tau]` range, because the formula is not guaranteed to be periodic. The seam therefore carries a
small error, about a quarter of a degree for 24 samples around a circle. Normals describe the surface
before modifiers deform it. Samples whose tangents are collinear or not finite, such as a sphere's
pole, keep only the layer rotation in that frame.

## 3. The same effect via the Kotlin DSL

```kotlin
import com.utophii.api.playParametric

player.location.playParametric("ring") {
    function("wave(u, amplitude)", "amplitude * sin(u)")
    variables("t")
    samples(96)
    range(0.0, MathUtils.TAU)
    x("R * cos(t)")
    y("wave(t * 3 + time * 0.05, 0.2)")
    z("R * sin(t)")
    default("R", 2.0)
    render {
        color(Color.AQUA)
        duration(160L)
        scale(1.5)
        modifiers { vortex(strength = 0.3) }
    }
}
```

The `ParametricConfig` builder:
- `variables(...)`, `samples(...)`, `range(...)`, `ranges(...)`
- `x(...)`, `y(...)`, `z(...)`, `angularSpeed(...)`
- `value(formula)`, `age(formula)` - independent spatial gradient and normalized age expressions
- `normal(x, y, z)`, `angles(x, y, z)`, `alignToNormal(DisplayOrientation.Side.OUTWARD)` - the
  [display model orientation](#orienting-display-models). One mode per effect, mixing modes throws
- `default(key, value)`, `defaults(map)`
- `function(signature, body)` - local functions, using the same signatures and rules as YAML
- `render { EffectConfig.() -> Unit }` - the same common settings as in `playEffect`.

For individual lifetimes, configure age and appearance together:

```kotlin
age("(time - t * 40) / 40")
render {
    colorOverLife(Color.RED, Color.BLUE)
    sizeOverLife(0f, 1f, 0f)
}
```

For direct evaluator use, construct `ExpressionFunctions(mapOf("wave(u, a)" to "a * sin(u)"))`
and pass it as the second argument to `ExpressionEvaluator.compile`. A compiled evaluator is shared
safely. Obtain a separate `fork()` for each concurrent calculation.

## 4. Tips

- **Closed curve/surface:** use the `[0, tau]` range (default) - the loop closes, `angularSpeed` spins it.
- **Open arc:** set `range: [{min: 0, max: 3.14}]`.
- **Animation:** `angularSpeed` shifts the phase over time. Driven by the top-level `duration`.
- **Density and thickness:** `samples` (point density), `count` (particles per point), `offset` (spread).
- **Performance:** avoid overly large `samples` - every point is rendered. A reasonable target:
  curve ≤ 256, surface ≤ 64×32.
