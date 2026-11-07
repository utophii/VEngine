---
title: Procedural generators
section: Server owners
lede: "Points, differential equations, iterated maps and nodal fields defined in configuration."
---

The engine supplies algorithms. Your YAML supplies the equations and parameters. A file can
contain one `points`, `ode`, `iteration` or `field` block, or put those blocks directly inside
its `layers`. Named atoms from other files still work through `effect: <name>`.

Start with the three installed examples: `basic.yml` for formulas and particle lifetime,
`animated.yml` for layers and motion, and `display.yml` for item, block and text displays.
The [recipe archive](built-in-primitives.md) contains optional configurations reproducing
the former built-in effects.

## Shared settings

| Setting | Meaning |
|---|---|
| `parameters` | Numeric defaults inside the generator block, overridden by layer or API parameters |
| `samples` | Formula for the sample count, truncated to an integer and limited to 1 through 4096 |
| `output.x`, `output.y`, `output.z` | Local coordinates, before the normal scale, rotation and modifiers |
| `output.value` | Optional scalar for the particle gradient |
| `output.age` | Optional normalized particle age, clamped to 0 through 1 |
| `output.spin` | Optional local Y rotation before the normal layer orientation, in radians |
| `output.yaw` | Optional additional rotation around the vertical axis, in radians |

`time` is the current effect or layer tick, `duration` is its runtime duration, `index` is the
zero-based sample number and `count` is the resolved sample count. `index` and `count` are
available while generating points, not while calculating the sample count. Nonfinite output
coordinates are skipped. Nonfinite ODE or iteration states stop that trajectory or discard
that point. Formula compilation happens when the file loads.

Keep `functions:` at the file root. All inline layers share those function definitions. Existing
formula syntax, lazy `if`, comparisons, noise and parameter animation remain available.
Unchanged local geometry is cached across frames and plays. Changing parameters or formulas
that depend on `time` invalidates it. A uniform `output.spin` or `output.yaw` can animate cached
geometry without recalculating its points. Field projection has its own cache, while settling
and jitter continue to update. These caches each retain only the most recent result.
Continuously changing nonlinear formulas still cost more CPU than specialized handwritten code.

The coordinate outputs are evaluated independently, so `output.y` cannot refer to `output.x`.

## Points

`points` evaluates output formulas once for each index. Unlike a sampled surface, it does not
impose a rectangular parameter grid. `samples` defaults to 128 and `output` is required.

```yaml
points:
  samples: amount
  parameters: {amount: 96, radius: 2}
  output:
    x: 'radius * cos(index / count * tau)'
    y: '0'
    z: 'radius * sin(index / count * tau)'
    value: 'index / count'
```

The Fibonacci sphere recipe also uses the index, with height `1 - 2*index/(count-1)` and
angle `PI*(3-sqrt(5))*index`. No sphere-specific class is needed.

## Differential equations

`ode` integrates named state variables. `initial` and `derivatives` must have identical keys,
with 1 through 16 variables. Every derivative sees the same state at an integration stage.
Use ordinary identifier names, excluding the shared context variables.

```yaml
ode:
  method: rk4
  step: 0.05
  samples: 128
  includeInitial: true
  initial: {x: 1, v: 0}
  derivatives: {x: v, v: '-x'}
  output: {x: x, y: v, z: '0'}
```

| Setting | Default | Meaning |
|---|---|---|
| `method` | `rk4` | `euler` or fourth-order `rk4` |
| `step` | `0.01` | Positive integration step, independent of Minecraft ticks |
| `samples` | `256` | Total trajectory samples |
| `includeInitial` | `false` | Include the initial state as the first sample, otherwise emit after the first step |
| `reveal` | `1` | Visible fraction of the trajectory, clamped to 0 through 1 and rounded up to samples |
| `output` | `{x: x, y: y, z: z}` | Converts state variables into visible coordinates |

`integrationTime` starts at zero and advances by `step`. It is distinct from the effect tick
`time`, including during intermediate RK4 evaluations. Initial formulas can read parameters,
`time` and `duration`. Use `reveal: '(time + 1) / duration'` to draw the path progressively.

For physics, use six state variables `x`, `y`, `z`, `vx`, `vy`, `vz`, with position derivatives
equal to velocity and velocity derivatives equal to acceleration. Gravity, drag and curl noise
can be combined in those formulas. Former attractor recipes select `euler` to preserve their
original numerical behavior.

## Iterated maps

`iteration` starts a separate state for each point and repeatedly applies a branch map.
`initial` and every branch must declare the same 1 through 16 state variables. Branch updates
are simultaneous. Initial formulas can depend on `index` and `copy`.

| Setting | Default | Meaning |
|---|---|---|
| `branches` | required | 1 through 64 maps from variable names to next-state formulas |
| `iterations` | `24` | Formula, limited to 1 through 128, or 64 for bit selection |
| `samples` | `512` | Number of points |
| `selection` | `hash` | `hash`, `bits` or `stratified` |
| `reverse` | `false` | Apply the chosen branch address from last digit to first |
| `copies` | `1` | Number of copies, limited to the point count |
| `output` | `{x: x, y: y, z: z}` | Converts the final state into coordinates |

`hash` chooses repeatable branches from each point's index. `bits` requires exactly two branches
and uses successive bits of the same 64-bit hash, suitable for inverse complex iteration.
`stratified` spreads the first address digits evenly across cells and hashes finer digits,
suitable for self-similar shapes. `reverse: true` composes those transforms from fine to coarse.

Branch formulas also see `branch` and `iteration`, both zero-based. `copy` is `index % copies`.
`address` is a normalized branch address for gradient coloring. With reverse composition it
reads the address coarse to fine. Otherwise it reads the last 16 choices, newest first.
The point index determines branch choices, so repeated calculations do not flicker.

Affine maps produce triangles, tetrahedra and carpets. Nonlinear maps can implement the real
and imaginary parts of inverse complex functions. Output formulas can place copies on different
planes or faces without introducing shape-specific engine code.

## Nodal fields

`field` starts points in a square or disk, projects them onto `f(u, v) = 0`, then animates their
movement from the starting positions to the projected positions. Coordinates `u` and `v` span
the unit domain. The default output places them in the horizontal XZ plane.

| Setting | Default | Meaning |
|---|---|---|
| `f` | required | Scalar field formula over `u`, `v`, parameters and `time` |
| `domain` | `square` | `square` spans -1 through 1 on both axes, `disk` has radius 1 |
| `samples` | `256` | Number of grains |
| `peak` | `1` | Positive normalization magnitude for the field strength |
| `settle` | `60` | Ticks to reach the projected positions, at least 1 |
| `period` | `0` | Positive values restart the settling at that interval |
| `jitter` | `0` | Deterministic shaking proportional to local field strength |
| `output` | `{x: u, y: '0', z: v, value: strength}` | Coordinates and gradient |

`strength` is `abs(f)` at the starting position divided by `peak`. Projection keeps the settled
target in the selected domain. Jitter may move particles slightly beyond the domain boundary.
This preserves the former plate behavior while allowing arbitrary nodal fields.

## Additional math functions

| Function | Meaning |
|---|---|
| `atan2(y, x)` | Signed polar angle |
| `besselJ(n, x)` | First-kind Bessel function, integer order truncated and limited to -12 through 12 |
| `besselMode(n, m, r)` | Cached normalized radial Bessel profile, matching the former circular plate modes |
| `curlX(x,y,z,t,frequency,octaves,epsilon)` | X component of the existing curl-noise field |
| `curlY(...)`, `curlZ(...)` | Other components with the same seven arguments |

`besselMode` rounds and limits order to 0 through 12 and band to 0 through 8, with band at least
1 for order zero. Radius is clamped to 0 through 1. Profiles have 1024 samples and use linear
interpolation. Curl octave counts are truncated and limited to 0 through 16, frequency and
epsilon use the engine's positive minimum values. Consecutive curl component calls reuse the
same vector when their arguments match.

A parameter named `mix` can appear as a variable. `mix(...)` remains a function call.
