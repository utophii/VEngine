---
title: Effect recipes and migration
section: Server owners
lede: "Optional geometry recipes and migration from the former built-in effect names."
---

A fresh installation contains three effects: `basic`, `animated` and `display`. The engine no
longer registers named spheres, attractors, fractals or vibrating plates at startup. Their
algorithms are available through [procedural generators](procedural-generators.md).

## Optional recipes

Former YAML examples and configurations reproducing all 14 former built-in effects are kept in
[the recipe archive](https://github.com/utophii/VEngine/tree/main/vengine-core/src/test/resources/recipes).
They are test resources and are not packaged in the plugin jar or copied into a server.

Copy the recipes you want into `plugins/VEngine/effects/`, then run `/ve reload`. A recipe with
`layers` also needs the atom files named by its `effect` entries. For example, `cosmic_gate`
uses `torus`, `sphere` and `helix`. Imported shapes need their assets in `plugins/VEngine/shapes/`,
available from the [shape archive](https://github.com/utophii/VEngine/tree/main/vengine-core/src/test/resources/shapes).
Copying the full recipe and shape archives restores the old catalog when desired.

| Former names | Configurable mechanism |
|---|---|
| `sphere` | `points`, with Fibonacci sampling formulas |
| `lorenz`, `aizawa`, `thomas`, `rossler`, `halvorsen` | `ode`, with explicit Euler integration |
| `rk4_trajectory` | `ode`, with six state variables, RK4, gravity, drag and curl noise |
| `julia` | `iteration`, inverse complex maps with stable bit selection |
| `sierpinski_triangle`, `sierpinski_tetrahedron`, `sierpinski_carpet`, `menger_sponge` | `iteration`, stratified affine maps and output transforms |
| `chladni`, `chladni_circle` | `field`, nodal projection with square or disk boundaries |
| `helix`, `torus`, `lissajous`, `rosette`, `torus_knot`, `beam`, `spline_beam`, `supershape` | Existing `curve` and `surface` formulas |
| `gyroid_orb`, `metaballs` | Existing `sdf` formulas |
| `fern`, `bush`, `koch_snowflake`, `wild_weed` | Existing `lsystem` rules |
| `sun_picture`, `text_banner`, `wire_icosahedron`, `emblem_epicycles` | Existing `shape` import and Fourier rendering |

The recipe parameter defaults reproduce the previous implementation. Regression checks compare
coordinates and gradient values against snapshots captured before removing the old classes.
The recipes preserve their numerical methods and point selection, including the former
Menger representation on the six outer cube faces.

## Existing installations

Updates never delete or overwrite existing files in `plugins/VEngine/effects/` or `shapes/`.
Old scripts referencing a former built-in name need the corresponding recipe installed, or
need that recipe's geometry embedded directly in their layers. For an embedded recipe, copy
its root `functions` into the destination file's `functions` block too.

Replace references in commands, triggers, MythicMobs skills and plugin code when adopting the
three new example names. `/ve list` shows what is actually registered on the server.

The classes `SphereEffect`, the five specialized attractor classes, `RK4TrajectoryEffect`,
`JuliaEffect`, `SierpinskiEffect` and the specialized plate classes have been removed from
`vengine-core`. Plugins importing those classes must rebuild against the public general
constructors described in the [developer guide](developer-guide.md#3-configured-atoms-and-procedural-generators).
The `vengine-api` contracts, provider registration, rendering and modifiers remain available.
