---
title: Built-in primitives
section: Server owners
lede: "Atoms that ship as code: spheres, physics, strange attractors, fractals and Chladni figures, with every parameter."
---

Most atoms are YAML files in `plugins/VEngine/effects/`. The atoms on this page are built into the
plugin, because they need numerical methods a formula cannot express. They are always available,
even with an empty `effects/` folder.

Use them like any atom: `/ve play julia`, or as a layer that sets `parameters:`:

```yaml
layers:
  - effect: julia
    particle: DUST
    gradient: ["#3a0ca3", "#f72585", "#ffbe0b"]
    parameters:
      radius: 1.8
      cOrbit: 0.06
```

Every parameter can also be animated with a `parameters.<name>` [keyframe
track](writing-effects.md#keyframe-animation). `points` of the fractal and Chladni atoms is capped at 4096.

## Sphere and physics

| Atom | Parameters (default) |
|---|---|
| `sphere` | `radius` (1.5), `points` (160), `angularSpeed` (0.04) |
| `rk4_trajectory` | `steps` (120), `timeStep` (0.08), `initialX/Y/Z` (0), `initialVelocityX/Y/Z` (0.46, 0.66, 0), `gravityX/Y/Z` (0, -0.2, 0), `drag` (0.06), `curlStrength` (0.18), `curlFrequency` (0.85), `curlOctaves` (3), `curlEpsilon` (0.04) |

`sphere` spreads its points evenly (Fibonacci sphere), without the crowding at the poles a
latitude/longitude grid has. `rk4_trajectory` integrates a thrown particle with gravity, drag and
curl-noise wind. Each step is one particle along the path. `rk4_comet.yml` is a ready example.

## Strange attractors

| Atom | Parameters (default) |
|---|---|
| `lorenz` | `sigma` (10), `rho` (28), `beta` (8/3), `dt` (0.015), `steps` (220), `angularSpeed` (0.03) |
| `aizawa` | `alpha` (0.06), `beta` (0.235), `gamma` (12.47), `dt` (0.015), `steps` (220), `angularSpeed` (0.03) |
| `thomas` | `b` (0.211), `dt` (0.025), `steps` (220), `angularSpeed` (0.03) |
| `rossler` | `a` (0.1), `b` (0.2), `c` (5.7), `dt` (0.02), `steps` (220), `angularSpeed` (0.03) |
| `halvorsen` | `a` (1.1), `dt` (0.01), `steps` (220), `angularSpeed` (0.03) |

One particle per integration step (`steps` = particle count), spinning around the vertical axis
at `angularSpeed`. `dt` stretches or shortens the visible trajectory. Small parameter changes can
turn a strange attractor into a single point or make it fly apart, so change them in small steps.

## Fractals

| Atom | Parameters (default) |
|---|---|
| `julia` | `points` (600), `depth` (24), `radius` (1.5), `cRe` (-0.8), `cIm` (0.156), `cOrbit` (0), `cSpeed` (0.02), `colorMode` (0), `angularSpeed` (0) |
| `sierpinski_triangle` | `points` (400), `depth` (6), `radius` (1.5), `pulse` (0), `pulseSpeed` (0.08), `angularSpeed` (0.02) |
| `sierpinski_tetrahedron` | same, `points` (600) |
| `sierpinski_carpet` | same, `points` (512) |
| `menger_sponge` | same, `points` (1200) |

**`julia`** draws the boundary of the Julia set of z² + c in the vertical plane. `radius` is the
size in blocks of |z| = 2. `cRe` and `cIm` pick the set. A non-zero `cOrbit` moves c around that
point on a circle at `cSpeed` radians per tick, so the set keeps morphing. `depth` (up to 64) is
the number of inverse iteration steps: more is sharper. Gradient colors run along the boundary
with `colorMode: 0`, outwards with `colorMode: 1`.

**Sierpinski atoms** place copies of a shape at its corners, `depth` levels deep (up to 16).
The triangle and carpet stand in the vertical plane, the tetrahedron stands on its base, and the
Menger sponge is drawn on the faces of its cube. `pulse` pushes the copies apart (and back) on
every level at `pulseSpeed`, `0` keeps them still. Gradient colors give each corner copy its own
band.

`julia_portal.yml` and `sierpinski_pyramid.yml` are ready examples.

## Chladni figures

Sand on a vibrating plate: every grain starts at a random spot and slides to the nodal lines,
where the plate does not move. The plate lies flat (horizontal).

| Atom | Plate | Parameters (default) |
|---|---|---|
| `chladni` | square | `n` (3), `m` (5), `mix` (-1) |
| `chladni_circle` | round | `n` (2), `m` (1), `n2` (0), `m2` (2), `mix` (0) |

Shared parameters: `size` (1.5, half width or radius in blocks), `points` (800), `settle` (60,
ticks the sand takes to reach the lines), `period` (0, restarts the settling every that many
ticks), `jitter` (0.02, how much grains shake), `angularSpeed` (0).

- **`chladni`**: `n` and `m` are the wave numbers along the two edges. `mix = -1` and `mix = 1` are
  the two classic figure families. Values in between and fractional `n`, `m` morph between
  figures.
- **`chladni_circle`**: `n` is the number of nodal diameters and `m` the number of nodal circles,
  whole numbers (up to 12 and 8). `n2`, `m2` describe a second pattern added with weight `mix`:
  `0` is off, large values let the second pattern take over.

Animate `mix` with a keyframe track to watch the sand move from one figure to another.
`chladni_disc.yml` does exactly that. Gradient colors show how strongly the plate vibrated where
each grain started.

## Formula atoms in effects/

The bundled YAML atoms can be played and used as layers the same way. Their parameters are listed
in the `parameters:` block of each file.

| Kind | Bundled atoms | Docs |
|---|---|---|
| Curves | `helix`, `lissajous`, `rosette`, `torus_knot`, `beam`, `spline_beam`, `parametric_rose` | [Parametric effects](parametric-effects.md) |
| Surfaces | `torus`, `parametric_torus`, `supershape` | [Parametric effects](parametric-effects.md) |
| SDF surfaces | `gyroid_orb`, `metaballs` | [SDF surfaces and L-systems](sdf-and-lsystems.md) |
| L-systems | `fern`, `bush`, `koch_snowflake`, `wild_weed` | [SDF surfaces and L-systems](sdf-and-lsystems.md) |
| Shapes | `sun_picture`, `text_banner`, `wire_icosahedron`, `emblem_epicycles` | [Imported shapes](imported-shapes.md) |
