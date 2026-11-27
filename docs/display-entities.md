---
title: Display entities
section: Server owners
lede: "Render item, block and text models instead of particles, with vanilla display entities."
---

Particles are tiny camera-facing sprites. A layer can render **display entities** instead -
real item, block or text models placed at every point the layer's atom calculates. They are
moved and re-transformed every frame with client-side interpolation, so the shape reads as solid
geometry: orbiting crystals, tumbling rune stones, floating glyphs.

No resource pack or client mod is needed - display entities are vanilla.

```yaml
layers:
  - curve:
      samples: 6
      x: '1.4 * cos(t)'
      y: '0'
      z: '1.4 * sin(t)'
    parameters: {points: 6}
    display:
      type: item
      material: AMETHYST_CLUSTER
      size: 0.7
      spin: {x: 0, y: 0.12, z: 0}
      glow: "#B26BFF"
```

The atom decides **where** the entities go (6 points on a ring here), the `display:` block decides
**what** stands there. Everything else about the layer - `scale`, rotation, modifiers, motion paths,
`start`/`duration`, keyframe animation - works exactly as with particles.

The bundled `display.yml` is the reference build: a ring of glowing amethyst crystals, a
counter-rotating ring of runes and a tumbling obsidian core, framed by an `END_ROD` particle aura.
Try it with `/ve play display`.

Use `display` at the root of an atom or on an individual layer of a scripted effect.
The Kotlin DSL accepts `display(...)`. [Developer guide](developer-guide.md#display-entities).

## The `display:` block

| Key | Default | Notes |
|---|---|---|
| `type` | `item` | `item`, `block`, `text`, `pixel` or `ribbon` |
| `material` | `AMETHYST_SHARD` (item) / `AMETHYST_BLOCK` (block) | Item model or block state. A non-item / non-block material falls back to the default |
| `text` | - | `text` only. A string or a list of [MiniMessage](https://docs.advntr.dev/minimessage/format.html) lines, point `i` shows `text[i % size]` |
| `size` | `0.5` (pixel `0.15`, ribbon `1`) | Model scale in blocks: a number, or `{x, y, z}` per axis. Multiplied by the layer `scale` |
| `rotation` | `{x: 0, y: 0, z: 0}` | Base local rotation of each model, radians (applied X, then Y, then Z) |
| `spin` | `{x: 0, y: 0, z: 0}` | Local angular velocity, radians per tick around each axis |
| `phase` | `0` | Spin offset in ticks between consecutive points, so the pieces do not turn in lockstep |
| `billboard` | `fixed` (item/block/ribbon) / `center` (text/pixel) | `fixed`, `vertical`, `horizontal` or `center` - how the model faces the viewer |
| `brightness` | world light | A number `0..15` for both block and sky light, or `{block, sky}` (a missing one becomes 15) |
| `glow` | - | Outline glow color, `"#RRGGBB"` or `{r, g, b}` |
| `interpolation` | `2` | Client interpolation length for movement and transformation, ticks (`0..59`) |
| `viewRange` | engine `view-distance` | Render distance multiplier, `1.0` = 64 blocks |
| `shadow` | none | `{radius, strength}` ground shadow under each entity (`strength` defaults to `1.0`) |
| `background` | vanilla | Text background or fallback pixel/ribbon color. `"#AARRGGBB"` (or `"#RRGGBB"`, opaque). `"#00000000"` removes the grey plate |
| `textShadow` | `false` | `text` only. Drop shadow under the glyphs |
| `seeThrough` | `false` | `text` only. Visible through blocks |
| `particles` | `false` | Also spawn the layer's particles at the same points |

Tips:

- **Point models.** One entity per point, so set `points` (or `samples`) low - `4..32` is
  typical. A layer is hard-capped at **256** entities, extra points are ignored.
- **Blocks rotate around their center.** VEngine offsets block models so `spin` and `rotation`
  turn them in place instead of around a corner.
- **`spin` vs `angularSpeed`.** `angularSpeed` on the atom moves the points around the shape,
  `spin` turns each model in place. Combine them for orbiting, self-rotating pieces.
- **Mixing.** Use `particles: true` to draw particles on top of the same points, or add a separate
  particle layer (as `display` does) for an aura.
- **Facing each model its own way.** `rotation` and `spin` are shared by all points. To turn models
  individually, for example letters that face away from the center of a ring, give the `curve`,
  `surface` or `points` block a normal, per-point angles or `alignToNormal` and set
  `billboard: fixed`. See [orienting display models](parametric-effects.md#orienting-display-models).

## Ribbons along curves

A `ribbon` stretches the colored background of a single-space text display between each pair
of consecutive samples. The optional sword-swing recipe uses 32 samples to draw a crescent
with 31 rectangles. The first sample is at the tail and the last is near the blade.

```yaml
display:
  type: ribbon
  width: [0, 0.12, 0.45]
  opacity: [0, 0.25, 0.9]
  normal: {x: 0, y: 1, z: 0}
  brightness: 15
```

- `width` is in blocks (default `0.15`). Layer scale, `display.size.y` and `sizeOverLife` multiply it.
- `opacity` ranges from `0` (transparent) to `1` (opaque, the default). It multiplies the alpha of the resolved pixel color.
- Both keys accept a number or equally spaced stops along the curve's length. Each rectangle samples the linear profile at its midpoint. More samples make the taper smoother.
- `normal` sets the preferred facing direction in local effect coordinates. The default points up for a horizontal slash. Use `{x: 0, y: 0, z: 1}` for a curve in the XY plane. The tangent fixes the length axis, and the renderer chooses a perpendicular axis if tangent and normal align. Ribbons use a fixed billboard. The normal controls their orientation in place of model rotation and spin.
- Each layer draws an open strip of up to 256 nonzero segments in sample order. Use a separate layer for each disconnected curve. With `particles: true`, particles still spawn at the original samples.

Copy the recipe into `plugins/VEngine/effects/`, run `/ve reload`, then `/ve play sword_swing`. [Sword-swing recipe](https://github.com/utophii/VEngine/blob/main/vengine-core/src/test/resources/recipes/sword_swing.yml).

## Animating displays

Besides the usual tracks (`scale`, rotations, `parameters.<name>`, colors), a layer with a
`display:` block accepts four extra [keyframe](writing-effects.md#keyframe-animation)
targets:

| Target | Animates |
|---|---|
| `display.size` | Uniform model size (sets x, y and z) |
| `display.rotation.x` | Base rotation around X, radians |
| `display.rotation.y` | Base rotation around Y, radians |
| `display.rotation.z` | Base rotation around Z, radians |

A pop-in / pop-out, as in `display`:

```yaml
animation:
  tracks:
    - target: display.size
      keyframes:
        - {tick: 0,   value: 0.0}
        - {tick: 20,  value: 0.7, easing: ease_out_elastic}
        - {tick: 140, value: 0.7}
        - {tick: 160, value: 0.0}
```

These targets do nothing on a layer without `display:`.

## Lifecycle and visibility

- Entities are spawned lazily on the first frame and **removed when the effect ends**, is stopped
  with `/ve stop`, or the server shuts down. They are non-persistent: nothing is saved to the world,
  so a crash cannot leave stray models behind.
- If something kills an entity mid-effect, VEngine respawns it on the next frame.
- When an effect is played for an explicit audience (`receivers(...)` in the DSL), the entities are
  hidden from everyone else.
- Display entities are real entities. A few hundred are fine, but thousands across many concurrent
  effects will cost server ticks and client FPS. Prefer particles for dense clouds.
- With [PacketEvents](https://modrinth.com/plugin/packetevents) installed, models exist only on the
  clients as packets, with no server-side entities, and models of followed effects ride the entity.
  See [Following entities](following-entities.md#display-models-and-packetevents).
- Display models are not limited by the particle budget.
