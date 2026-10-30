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
- Formulas are evaluated by a **safe** engine ([exp4j](https://www.objecthunter.net/exp4j/)) - only
  math is parsed, no code is executed.

## Formula syntax

Supported:

- operators `+ - * / % ^`, parentheses
- functions `sin cos tan asin acos atan sinh cosh tanh log log10 log2 exp sqrt cbrt abs ceil floor round`
- constants `pi` (≈3.1416), `e` (≈2.718), plus `tau`, `TAU` (≈6.2832), `PI`
- variables you declare (`t`, `theta`, `phi`, …) and any named parameters (`R`, `r`, …)
- the **magic variable `time`** - the current effect time (in ticks). It is available in every formula
  and lets you animate the shape directly (e.g. a wave) independently of `angularSpeed`.

> The built-in atoms (`helix`, `torus`, `lissajous`, `rosette`, `torus_knot`, `beam`, `spline_beam`,
> `supershape`) are themselves defined by **formulas** - their configs live in `resources/effects/`
> and can be used as ready-made examples or copied.

> ⚠️ Multiplication is **explicit**: write `R * cos(t)`, not `R cos(t)`.

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
    variables("t")
    samples(96)
    range(0.0, MathUtils.TAU)
    x("R * cos(t)")
    y("0")
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
- `render { EffectConfig.() -> Unit }` - the same common settings as in `playEffect`.

## 4. Tips

- **Closed curve/surface:** use the `[0, tau]` range (default) - the loop closes, `angularSpeed` spins it.
- **Open arc:** set `range: [{min: 0, max: 3.14}]`.
- **Animation:** `angularSpeed` shifts the phase over time. Driven by the top-level `duration`.
- **Density and thickness:** `samples` (point density), `count` (particles per point), `offset` (spread).
- **Performance:** avoid overly large `samples` - every point is rendered. A reasonable target:
  curve ≤ 256, surface ≤ 64×32.
