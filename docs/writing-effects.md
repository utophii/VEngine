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
| `samples` | `64` (curve) / `40,20` (surface) | per-frame particle count, scalar or list |
| `range` | `0..tau` | list of `{min, max}` per variable |
| `x`, `y`, `z` | `0` | formulas, may use `t`/`theta`/`phi`, your `parameters`, math functions, and the magic `time` variable (current tick of the effect) |
| `parameters` | - | named constants, also overridable via `/ve play` or the DSL |
| `particle`, `duration` | `FLAME`, `60` | file-level defaults |

Multiplication is always explicit: write `R * cos(t)`, not `R cos(t)`. Full function list and
surface examples: [Parametric Effects](parametric-effects.md).

Other kinds of atoms: implicit surfaces and L-system plants ([SDF surfaces and
L-systems](sdf-and-lsystems.md)), and pictures, text, OBJ models and SVG outlines ([Imported
shapes](imported-shapes.md)). Atoms built into the plugin - `sphere`, physics, strange attractors,
fractals and Chladni figures - are listed with all their parameters in [Built-in
primitives](built-in-primitives.md).

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

Add a top-level [`follow:` block](following-entities.md) and the effect sticks to the player or
mob that plays it.

### Layer timing

`start` and `duration` place a layer on the show's timeline in ticks. Instead of counting ticks,
a layer can start after another one ends: give that layer an `id` and refer to it with `after`.

```yaml
duration: 200
layers:
  - id: charge
    effect: sphere
    duration: 60
  - id: burst
    effect: torus
    after: charge             # starts at tick 60
    duration: 40
  - effect: helix
    after: burst
    delay: 10                 # starts 10 ticks after burst ends, at tick 110
```

A layer without its own `duration` runs to the end of the show, so `after:` such a layer starts
when the show ends and never appears. Unknown ids and `after:` cycles are reported in the console
and those layers are skipped. With duplicate ids, `after:` refers to the first layer.

### Gradients

`gradient` gives every particle of a layer its own color, sampled from a list of stops:

```yaml
particle: DUST
dustSize: 0.6
gradient: ["#3a0ca3", "#f72585", "#ffbe0b"]
```

Where a particle falls on the gradient depends on the atom: fractals color by depth or position
along the boundary, L-systems from root to tips, SDF surfaces by their `value` formula or height,
and Chladni figures by vibration. Atoms that assign nothing color their particles in drawing order,
first stop to last. Gradients work with `DUST`, `DUST_COLOR_TRANSITION` and `ENTITY_EFFECT`
particles and with `pixel` displays. Image shapes use the picture's colors unless they set
`colors: gradient`.

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

### Particle budget

The server thins particles out with distance and under lag (see `particle-budget` in
[Configuration](commands-and-permissions.md#configuration)). Two keys opt an effect out, either at the
top of the file (every layer) or on one layer:

```yaml
budget: exempt   # ignore the particle limits and auto-quality (boss telegraphs, cutscenes)
lod: false       # full density at any distance up to view-distance
```

Use `exempt` sparingly: exempt particles are sent even when a player or the server is over budget.

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

### Spawning other effects

A scripted effect can start other effects on its timeline. `emitters:` fire while the show runs,
`then:` fires after its layers end:

```yaml
id: firework
duration: 40
layers:
  - effect: helix
    duration: 40
emitters:
  - effect: sphere
    start: 10                 # first emission at tick 10
    every: 5                  # then every 5 ticks...
    count: 4                  # ...4 times in total
    offset: {x: 0, y: 3, z: 0}
    scale: 0.5
    duration: 20
then:
  - effect: cosmic_gate
    delay: 5                  # 5 ticks after the layers end
```

| Key | Default | Description |
|---|---|---|
| `effect` | required | Any registered effect, scripted ones included |
| `start` (`emitters`) / `delay` (`then`) | `0` | Tick of the first emission, from the start or from the end of the layers |
| `every` | - | Repeat period in ticks. Without it the emitter fires once, `count` alone repeats every tick |
| `count` | unlimited | Number of emissions. Without it, repeating emitters fire until the layers end |
| `offset` | `0, 0, 0` | Position relative to the effect, scaled with it |
| `scale` | `1` | Multiplies the parent's scale |
| `duration` | effect's own | Ticks the spawned effect plays. [Built-in primitives](built-in-primitives.md) have no duration of their own and play for 60 ticks |

A shorthand `emitters: sphere` (or a list of names) fires each effect once at tick 0.

- The parent counts as running until everything it spawned has finished, and `/ve stop` on the
  parent stops its children as well. Spawned effects are not listed separately in `/ve list`.
- Children inherit the parent's receivers and placeholder player. When the parent
  [follows an entity](following-entities.md), the children follow it too.
- Effects that spawn themselves, directly or through other effects, are rejected at load time.
- `max-emitted-effects` in `config.yml` caps how many spawned effects play at once on the server,
  and emissions beyond it are skipped.

### Placeholders in values

With [PlaceholderAPI](placeholders.md) installed, a few values can be formulas that contain
placeholders. They are evaluated once, when the effect starts:

```yaml
duration: "40 + %player_level% * 2"     # top-level duration of a scripted effect
layers:
  - effect: helix
    scale: "0.5 + %player_level% / 50"
    parameters:
      turns: "2 + %statistic_deaths% % 5"
```

The keys are the top-level `duration` of a scripted effect, a layer's `scale` and a layer's
`parameters.<name>`. Placeholders are read for the player who played the effect: the sender of
`/ve play`, the followed player, the actor of a [trigger](triggers.md) or the target of a
[MythicMobs](mythicmobs.md) mechanic. Server-wide placeholders such as `%server_online%` work
without a player. Values that are not numbers count as `0`, with one warning in the console.
`%` between numbers is still the modulo operator.

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
| `Skipping effect '<file>': no valid layers were found` | Every layer failed to parse, look for the layer warnings just above |
| `Skipping layer for unknown primitive effect '<name>'` | Layer references an effect name that does not exist (typo, or the atom file failed to load) |
| `Skipping parametric effect '<file>': one of the x/y/z formulas is invalid` | Formula syntax error - often a missing `*` or unbalanced parenthesis |
| Nothing visible, no warnings | `duration` too short (YAML default is 60 ticks, `/ve play` lets you override), particle typo, or you are beyond `view-distance` in `config.yml` |
| `Unknown display type '<name>'` | `display.type` must be `item`, `block` or `text`, the layer falls back to particles |
| Display layer shows fewer models than points | A layer is capped at 256 display entities |
| Effect plays but does not move | Motion legs need `ticks > 0`, and the layer must live long enough to reach the waypoint |
