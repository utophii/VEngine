---
title: Parametric effects
section: Server owners
lede: "Describe new shapes with math formulas: curves, surfaces, syntax and schema."
---

VEngine lets you **create new effects directly in configuration**, describing them with mathematical
formulas. No Kotlin class needed - just YAML or the DSL.

This is the core of "engine-ness": the bundled effects (`helix`, `torus`, `lorenz`, …) are only
**built-in atoms**, and an arbitrary shape is defined by a formula.

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

> The built-in atoms (`helix`, `torus`, `lissajous`, `rosette`, `torus_knot`, `beam`, `spline_beam`,
> `supershape`) are themselves defined by **formulas** - their configs live in `resources/effects/`
> and can be used as ready-made examples or copied.

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
coordinate and `value` formulas, SDF formulas, and existing expression fields such as layer `scale`,
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
| `parameters` | `Map<String, Double>` | - | Default values for parameter variables. |

Common fields (`particle`, `color`, `toColor`, `material`, `count`, `offset`, `speed`, `scale`,
`rotationYaw`, `tiltAxis`, `tiltAngle`, `duration`, `modifiers`) are set at the top level and work
the same way as for the other effects. Any runtime `parameters` (e.g. from modifiers) override the
`curve.parameters` values during rendering.

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
- `default(key, value)`, `defaults(map)`
- `function(signature, body)` - local functions, using the same signatures and rules as YAML
- `render { EffectConfig.() -> Unit }` - the same common settings as in `playEffect`.

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
