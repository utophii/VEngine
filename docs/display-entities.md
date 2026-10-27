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
  - effect: helix
    parameters: {radius: 1.4, height: 0.0, turns: 1.0, points: 6}
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

The bundled `crystal_ward.yml` is the reference build: a ring of glowing amethyst crystals, a
counter-rotating ring of runes and a tumbling obsidian core, framed by an `END_ROD` particle aura.
Try it with `/ve play crystal_ward`.

> In YAML, `display:` is a **layer** key of a scripted effect. To give a single atom a display
> look, wrap it in a one-layer scripted effect. From code, use `display(...)` in the DSL - see
> [Developer Guide](developer-guide.md#display-entities).

## The `display:` block

| Key | Default | Notes |
|---|---|---|
| `type` | `item` | `item`, `block` or `text` |
| `material` | `AMETHYST_SHARD` (item) / `AMETHYST_BLOCK` (block) | Item model or block state. A non-item / non-block material falls back to the default |
| `text` | - | `text` only. A string or a list of [MiniMessage](https://docs.advntr.dev/minimessage/format.html) lines; point `i` shows `text[i % size]` |
| `size` | `0.5` | Model scale in blocks: a number, or `{x, y, z}` per axis. Multiplied by the layer `scale` |
| `rotation` | `{x: 0, y: 0, z: 0}` | Base local rotation of each model, radians (applied X, then Y, then Z) |
| `spin` | `{x: 0, y: 0, z: 0}` | Local angular velocity, radians per tick around each axis |
| `phase` | `0` | Spin offset in ticks between consecutive points, so the pieces do not turn in lockstep |
| `billboard` | `fixed` (item/block) / `center` (text) | `fixed`, `vertical`, `horizontal` or `center` - how the model faces the viewer |
| `brightness` | world light | A number `0..15` for both block and sky light, or `{block, sky}` (a missing one becomes 15) |
| `glow` | - | Outline glow color, `"#RRGGBB"` or `{r, g, b}` |
| `interpolation` | `2` | Client interpolation length for movement and transformation, ticks (`0..59`) |
| `viewRange` | engine `view-distance` | Render distance multiplier, `1.0` = 64 blocks |
| `shadow` | none | `{radius, strength}` ground shadow under each entity (`strength` defaults to `1.0`) |
| `background` | vanilla | `text` only. `"#AARRGGBB"` (or `"#RRGGBB"`, opaque). `"#00000000"` removes the grey plate |
| `textShadow` | `false` | `text` only. Drop shadow under the glyphs |
| `seeThrough` | `false` | `text` only. Visible through blocks |
| `particles` | `false` | Also spawn the layer's particles at the same points |

Tips:

- **Count = atom points.** One entity per point, so set `points` (or `samples`) low - `4..32` is
  typical. A layer is hard-capped at **256** entities; extra points are ignored.
- **Blocks rotate around their center.** VEngine offsets block models so `spin` and `rotation`
  turn them in place instead of around a corner.
- **`spin` vs `angularSpeed`.** `angularSpeed` on the atom moves the points around the shape;
  `spin` turns each model in place. Combine them for orbiting, self-rotating pieces.
- **Mixing.** Use `particles: true` to draw particles on top of the same points, or add a separate
  particle layer (as `crystal_ward` does) for an aura.

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

A pop-in / pop-out, as in `crystal_ward`:

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
- Display entities are real entities. A few hundred are fine; thousands across many concurrent
  effects will cost server ticks and client FPS. Prefer particles for dense clouds.
