---
title: Writing your own effects
section: Server owners
lede: "Formula atoms, layered shows, modifiers and keyframe animation, all in YAML."
---

Every effect is a `.yml` file in `plugins/VEngine/effects/`. The effect name is the file name
(or the `id:` field inside). Edit a file, run `/ve reload`, play it with `/ve play <name>`.
Broken files never crash the plugin - they are skipped with a warning in the console.

There are two kinds of effect files:

1. **Formula atom** - a shape described by math (`curve:` or `surface:` block)
2. **Scripted effect** - a show composed of `layers:`, each layer playing an atom with its own
   timing, particle and modifiers

## Formula atoms

A curve samples one variable (`t`), a surface samples two (`theta`, `phi`). Each sample becomes
one particle per frame.

```yaml
id: my_ring
curve:
  variables: [t]
  samples: 96                       # particles per frame
  range: [{min: 0, max: 6.2831853}] # 0 to tau (full circle)
  x: "R * cos(t)"
  y: "0"
  z: "R * sin(t)"
  parameters:                       # your own constants, usable in x/y/z and overridable at play time
    R: 2.0
  angularSpeed: 0.05                # how fast the shape internally animates, per tick
particle: FLAME                     # Bukkit particle name
duration: 120                       # ticks (20 = 1 second); default 60
```

Quick reference:

| Key | Default | Notes |
|---|---|---|
| `variables` | `[t]` (curve) / `[theta, phi]` (surface) | sampling variable names |
| `samples` | `64` (curve) / `40,20` (surface) | per-frame particle count; scalar or list |
| `range` | `0..tau` | list of `{min, max}` per variable |
| `x`, `y`, `z` | `0` | formulas; may use `t`/`theta`/`phi`, your `parameters`, math functions, and the magic `time` variable (current tick of the effect) |
| `parameters` | - | named constants; also overridable via `/ve play` or the DSL |
| `particle`, `duration` | `FLAME`, `60` | file-level defaults |

Multiplication is always explicit: write `R * cos(t)`, not `R cos(t)`. Full function list and
surface examples: [Parametric Effects](parametric-effects.md).

Built-in atoms you can play or reuse as layers: `sphere`, `helix`, `torus`, `rosette`,
`lissajous`, `torus_knot`, `beam`, `spline_beam`, `supershape`, `rk4_trajectory`, and the strange
attractors `lorenz`, `aizawa`, `thomas`, `rossler`, `halvorsen`. Their parameters are listed in the
[Developer Guide](developer-guide.md#3-primitive-atoms-and-their-parameters).

## Scripted effects

A scripted effect layers atoms into a show:

```yaml
id: my_show
duration: 200
layers:
  - effect: helix             # any atom or scripted effect name
    start: 0                  # first tick this layer appears (default 0)
    duration: 160             # how long this layer runs (default: rest of the show)
    particle: END_ROD
    color: {r: 120, g: 80, b: 255}   # or "#7850FF"; used by DUST-type particles
    count: 1
    scale: 1.2
    rotationYaw: 0.0
    rotationPitch: 1.5708     # radians: tilts the whole layer
    rotationRoll: 0.0
    parameters:
      turns: 5
    modifiers: []             # see below
    animation: {}             # see below
    display: {}               # optional: models instead of particles, see below
```

Colors accept `{r, g, b}` maps or hex strings (`"#FF8800"`, `FF8800`).

### Display entities

Add a `display:` block to a layer and it places an item, block or text model at every point
instead of a particle:

```yaml
display:
  type: item                  # item | block | text
  material: AMETHYST_CLUSTER
  size: 0.7
  spin: {x: 0, y: 0.12, z: 0} # radians per tick
  glow: "#B26BFF"
```

All keys, animation targets and limits: [Display entities](display-entities.md).

### Modifiers

Post-processing applied to every particle of a layer, each frame:

```yaml
modifiers:
  - type: turbulence
    strength: 0.15            # default 0.12
    frequency: 0.35
    octaves: 4
  - type: rotation            # spins the shape around an axis
    axis: {x: 0, y: 1, z: 0}
    angularVelocity: 0.08     # radians per tick
  - type: vortex              # swirling pull with a soft core
    coreRadius: 1.2
    strength: 0.4
  - type: motion              # moves the whole layer along waypoints
    mode: hold                # hold | loop | ping_pong
    easing: ease_in_out_sine  # default easing for legs without their own
    path:
      - {x: 10, y: 2, z: 0, ticks: 60}
      - {x: 20, y: 0, z: 0, ticks: 40, easing: ease_out_cubic}
```

Waypoint offsets are relative to the effect's spawn point: at tick 0 the layer is exactly where
it was played. To give several layers the same path, write it once and reuse it with a YAML
anchor (`&name` / `*name`) - see `traveling_comet.yml` for a working example.

### Keyframe animation

Number tracks animate layer values over time:

```yaml
animation:
  easing: ease_in_out_sine    # track-level default
  tracks:
    - target: rotationRoll
      loop: true              # repeat after the last keyframe
      keyframes:
        - {tick: 0,   value: 0}
        - {tick: 100, value: 3.1416}
    - target: scale
      keyframes:
        - {tick: 0,  value: 0.5}
        - {tick: 20, value: 1.5, easing: ease_out_bounce}
```

Track targets: `scale`, `rotationYaw`, `rotationPitch`, `rotationRoll`, `tiltAngle`,
`parameters.<name>` (your formula parameter), `color.r`, `color.g`, `color.b`, and - on layers with
a `display:` block - `display.size`, `display.rotation.x`, `display.rotation.y`, `display.rotation.z`.

### Orientation cheat sheet

Angles are in radians. Common values: `30` deg = 0.524, `45` deg = 0.785, `90` deg = 1.5708,
`180` deg = 3.1416, `270` deg = 4.7124. Rotation order: roll, then pitch, then yaw -
the layer tilts in its own frame first, then turns around the world's vertical axis.

## A complete example

`traveling_comet.yml` (bundled) is the reference build: a sphere head and a helix trail share a
three-leg flight path through a YAML anchor, while a rosette gate ping-pongs at the spawn point.
Open it in `plugins/VEngine/effects/` and read it top to bottom - it uses nearly every feature on
this page. For display entities, read `crystal_ward.yml` next.

## Troubleshooting

| Symptom | Meaning |
|---|---|
| `Skipping effect '<file>': no valid layers were found` | Every layer failed to parse; look for the layer warnings just above |
| `Skipping layer for unknown primitive effect '<name>'` | Layer references an effect name that does not exist (typo, or the atom file failed to load) |
| `Skipping parametric effect '<file>': one of the x/y/z formulas is invalid` | Formula syntax error - often a missing `*` or unbalanced parenthesis |
| Nothing visible, no warnings | `duration` too short (YAML default is 60 ticks; `/ve play` lets you override), particle typo, or you are beyond `view-distance` in `config.yml` |
| `Unknown display type '<name>'` | `display.type` must be `item`, `block` or `text`; the layer falls back to particles |
| Display layer shows fewer models than points | A layer is capped at 256 display entities |
| Effect plays but does not move | Motion legs need `ticks > 0`, and the layer must live long enough to reach the waypoint |
