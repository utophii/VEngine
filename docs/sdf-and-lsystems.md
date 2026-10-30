---
title: SDF surfaces and L-systems
section: Server owners
lede: "Two more kinds of formula atoms: implicit surfaces f(x, y, z) = 0, and plants drawn by a turtle."
---

Besides [curves and surfaces](parametric-effects.md), an atom file can hold an `sdf:` block or an
`lsystem:` block. Both are atoms: play them with `/ve play`, or use them as layers of a scripted
effect like any other atom.

## SDF surfaces

An `sdf:` block describes a surface as the set of points where a formula is zero, usually a
signed distance function (negative inside, positive outside). Particles are spread through the
cube `[-bounds, bounds]` on every axis and pulled onto the surface.

```yaml
id: metaballs
sdf:
  f: "smin(sdTorus(x, y, z, 1.0, 0.18), sdSphere(x - orbit * cos(time * speed), y, z, 0.45), blend)"
  bounds: 1.6
  points: 700
  parameters:
    orbit: 1.0
    speed: 0.05
    blend: 0.35
particle: DUST
dustSize: 0.6
gradient: ["#06d6a0", "#118ab2"]
```

| Key | Default | Description |
|---|---|---|
| `f` | required | Surface formula over `x`, `y`, `z`, your `parameters` and `time` (ticks) |
| `value` | height | Formula over the surface point, `0..1`, that picks each particle's [gradient](writing-effects.md#gradients) color. Without it, colors run bottom to top |
| `bounds` | `1.5` | Half size of the cube particles start in, in blocks before `scale` |
| `points` | `600` | Particles tried, up to 4096. Particles that cannot reach the surface inside the cube are dropped, so a small shape in a big cube shows fewer |
| `parameters` | - | Named constants, overridable from layers, `/ve` and the DSL |
| `angularSpeed` | `0.05` | Spin around the vertical axis, radians per tick |

`points`, `bounds` and `angularSpeed` can also be set as parameters of a layer.

### Distance functions

| Function | Shape |
|---|---|
| `sdSphere(x, y, z, r)` | Sphere of radius `r` |
| `sdBox(x, y, z, bx, by, bz)` | Box with half sizes `bx`, `by`, `bz` |
| `sdTorus(x, y, z, R, r)` | Torus lying in the horizontal plane, ring radius `R`, tube radius `r` |
| `smin(a, b, k)` | Smooth union of two shapes, `k` is the blend distance |
| `smax(a, b, k)` | Smooth intersection. `smax(a, -b, k)` cuts `b` out of `a` |

To move a shape, shift its coordinates: `sdSphere(x - 1, y, z, 0.5)` is a sphere centered at
`x = 1`. Any other formula works as well, for example the gyroid
`sin(x) * cos(y) + sin(y) * cos(z) + sin(z) * cos(x)`, and `gyroid_orb.yml` carves one into a ball
with `smax`. All [math functions](parametric-effects.md) are available.

### Performance

A formula without `time` gives the same surface every frame, so it is computed once per set of
parameter values and cached. Static surfaces cost almost nothing per frame. A formula with `time`
is recomputed every tick: keep `points` moderate for those.

## L-systems

An `lsystem:` block rewrites a string of symbols a few times and draws the result with a 3D
turtle. The drawing grows from the root outwards, all branches at once.

```yaml
id: fern
lsystem:
  axiom: X
  rules:
    X: "F+[[X]-X]-F[-FX]+X"
    F: FF
  iterations: 5
  angle: 25
  size: 3.5
  spacing: 0.07
  points: 1600
  growth: 80
  anchor: base
particle: DUST
dustSize: 0.7
gradient: ["#5c3d2e", "#8a6a3b", "#6a994e", "#a7c957"]
```

| Key | Default | Description |
|---|---|---|
| `axiom` | required | Starting string |
| `rules` | - | Symbol to replacement, see [Rules](#rules) |
| `iterations` | `4` | Rewriting steps. Rewriting stops early, with a warning, past one million symbols |
| `angle` | `25` | Turning angle in degrees |
| `size` | `3` | Largest extent of the drawing in blocks, the drawing is scaled to fit |
| `spacing` | `0.08` | Blocks between particles along branches |
| `points` | `1500` | Particle cap, up to 4096. Spacing widens when the drawing needs more |
| `growth` | `0` | Ticks the plant takes to grow from the root. `0` shows it fully grown |
| `anchor` | `base` | `base`: root at the effect position. `center`: drawing centered on it |
| `angularSpeed` | `0.05` | Spin around the vertical axis, radians per tick |
| `seed` | random per play | Fixes the random choices of stochastic rules |

### Turtle symbols

| Symbol | Action |
|---|---|
| `F`, `G` | Move forward and draw |
| `f` | Move forward without drawing |
| `+`, `-` | Turn left, right |
| `&`, `^` | Pitch down, up |
| `\`, `/` | Roll left, right |
| `\|` | Turn around |
| `[`, `]` | Save, restore the turtle position and heading (branches) |

Any other symbol (`X`, `A`, `L`, ...) only takes part in rewriting. The turtle starts heading up,
so a system that only uses `+` and `-` stands in the vertical plane.

### Rules

A rule maps one symbol to a replacement string:

```yaml
rules:
  F: "F+F--F+F"
```

Several replacements make the rule stochastic: every occurrence of the symbol picks one at
random. A plain list makes them equally likely, `{to, weight}` entries set relative weights:

```yaml
rules:
  X:
    - { to: "F[+X]F[-X]X", weight: 2 }
    - { to: "F[+X]FX", weight: 1 }
    - { to: "F[-X]FX", weight: 1 }
  F: ["F", "FF"]
```

With stochastic rules, every play grows a different plant unless `seed` is set in the block, or
passed as a `seed` parameter by a layer.

### Animating

Layers can override these parameters, and [keyframe tracks](writing-effects.md#keyframe-animation)
can animate them:

| Parameter | Effect |
|---|---|
| `angle` | Turning angle in degrees: animate it to make a plant sway or unfold |
| `growth` | Growth time in ticks |
| `progress` | Growth from `0` to `1`, set directly instead of by time |
| `seed` | Which random plant to draw |
| `angularSpeed` | Spin |

The gradient value of a particle is its distance from the root along the branches, so colors run
from the trunk (first stop) to the tips (last stop).

Bundled examples: `fern.yml`, `bush.yml` (3D), `koch_snowflake.yml`, `wild_weed.yml`
(stochastic), and `living_tree.yml`, which layers two plants.
