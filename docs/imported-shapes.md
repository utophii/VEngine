---
title: Imported shapes
section: Server owners
lede: "Turn pictures, text, OBJ models and SVG outlines into effects, including SVG drawn by Fourier epicycles."
---

Not every shape is easy to write as a formula. With a `shape:` block an effect takes its geometry from a
file instead: a picture, a 3D model, a vector outline or plain text.

Source files go in `plugins/VEngine/shapes/`. Effect files name them relative to that folder, and
`/ve reload` reads them again. VEngine ships three examples there (`sun.png`, `emblem.svg`,
`icosahedron.obj`), along with the effects that use them: `sun_picture`, `text_banner`,
`wire_icosahedron`, `emblem_epicycles` and `fourier_emblem`.

An imported shape is an atom, just like a `curve:` or `lsystem:` effect. Play it on its own, or use it
as a layer of a layered effect.

## Common keys

Every `shape:` block names **exactly one** source (`image`, `text`, `obj` or `svg`). These keys work
with all of them:

| Key | Default | Meaning |
|---|---|---|
| `size` | `3` | Largest side of the shape, in blocks |
| `points` | `1200` | Particle cap (at most 4096) |
| `anchor` | `center` | `center`: centered on the effect position; `base`: standing on it |
| `growth` | `0` | Ticks until the whole shape has appeared (0: all at once) |
| `angularSpeed` | `0` | Spin around the vertical axis, rad/tick |
| `mode` | depends on the source | How the source turns into particles; see below |

2D shapes (pictures, text, SVG) stand upright in the vertical XY plane. Use `rotationYaw`,
`rotationPitch` or a tilt to lay them down.

Runtime `parameters` can override `growth` and `angularSpeed`, and can set `progress` (0..1) directly,
for example from an animation track.

## Pictures

```yaml
id: sun_picture
shape:
  image: sun.png
  size: 3
  points: 1500
  growth: 30
particle: DUST
dustSize: 0.6
```

- PNG, JPEG, GIF and BMP are read. Each visible pixel becomes one particle. A picture with more
  visible pixels than `points` is shrunk first, and the pixels it merges are averaged.
- `alpha` (default `0.5`): pixels at or below this opacity are left out.
- `background: "#ffffff"` and `tolerance` (0..1, default `0.1`) leave out a solid backdrop, which
  helps with JPEGs since they have no transparency.
- `colors: image` (the default) gives every particle its pixel's color. `colors: gradient` uses the
  pixel's brightness to pick a color from the layer's `gradient`.
- Pixel colors work with `DUST`, `DUST_COLOR_TRANSITION`, `ENTITY_EFFECT` and `pixel` displays.
- A growing picture fades in all over at once.

## Text

```yaml
id: text_banner
shape:
  text: "Welcome, %player_name%"
  font: SansSerif
  style: bold
  mode: outline
  size: 4
gradient: ["#4cc9f0", "#7209b7", "#f72585"]
```

- `font` is an installed font family (`Serif`, `SansSerif` and `Monospaced` always exist) or a
  `.ttf` / `.otf` file in the shapes folder. `style` is `plain`, `bold`, `italic` or `bold_italic`.
- `\n` starts a new line. Lines are centered on each other.
- PlaceholderAPI placeholders are filled in **for the player of every play**: the player who runs
  `/ve play`, or the followed player. Each distinct text is laid out once and then cached. Without
  PlaceholderAPI the text is drawn as written.
- `mode` is `outline`, `fill` or `fourier`; see [Outlines](#outlines-svg-and-text) below.

## OBJ models

```yaml
id: wire_icosahedron
shape:
  obj: icosahedron.obj
  mode: edges
  size: 2.5
  angularSpeed: 0.03
gradient: ["#06d6a0", "#118ab2"]
```

- `v`, `f` and `l` lines are read; materials and textures are ignored. Faces may have any number of
  corners, and negative indices are allowed.
- OBJ models are y-up, like Minecraft, so they stand the way they were modeled.
- `mode`:
  - `vertices`: one particle per vertex;
  - `edges`: a wireframe, with every shared edge drawn once;
  - `surface` (the default): points spread evenly over the faces, weighted by face area.
- Gradient values run from the bottom of the model (0) to its top (1).

## Outlines: SVG and text

```yaml
shape:
  svg: emblem.svg
  mode: outline      # or fill, fourier
```

- **SVG support** covers `<path>` (all commands, arcs included), `<rect>`, `<circle>`, `<ellipse>`,
  `<line>`, `<polyline>` and `<polygon>`. `transform` attributes are applied on shapes and on groups.
  Styles, `<use>`, `<text>`, clipping and masks are ignored. To keep text from an SVG editor, convert
  it to paths first ("Object to path" in Inkscape).
- `mode: outline` (the default) spaces particles evenly along every subpath. Gradient values run
  along the outline, so with `growth` the shape is drawn stroke by stroke.
- `mode: fill` fills the inside with a square grid of particles. `fillRule` is `nonzero` (the
  default) or `evenodd`.

### Fourier epicycles

`mode: fourier` draws the outline with a chain of rotating circles: the outline's
[Fourier series](https://en.wikipedia.org/wiki/Discrete_Fourier_transform). The pen at the end of the
chain leaves a trace.

```yaml
id: emblem_epicycles
shape:
  svg: emblem.svg
  mode: fourier
  size: 3.5
  samples: 1024
  epicycles: 100
  period: 200
  trail: 1
```

| Key / parameter | Default | Meaning |
|---|---|---|
| `samples` | `1024` | Points the outline is resampled at (64-4096); more keeps sharp corners |
| `epicycles` | `100` | Circles in the chain; fewer gives a smoother, rounder drawing |
| `period` | `200` | Ticks per full loop |
| `trail` | `1` | Share of the loop that stays visible behind the pen (1 keeps the whole drawing) |
| `spacing` | `0.12` | Blocks between the particles of arms and circles |
| `trace`, `arms`, `circles` | `1` | Set to `0` to hide that part |

- **One loop:** all subpaths are joined into a single loop, and the pen is lifted on the jumps
  between them.
- **Gradient values:** trace values run along the loop. Arm and circle values run from the largest
  circle (0) to the smallest (1).
- **Runtime parameters:** every key except `samples` can also be set or animated in a layer's
  `parameters`.
- **Separate colors:** use the same atom in two layers with different parts hidden, as
  `fourier_emblem.yml` does:

```yaml
id: fourier_emblem
duration: 400
layers:
  - effect: emblem_epicycles
    dustSize: 0.35
    gradient: ["#4361ee", "#4cc9f0"]
    parameters: { trace: 0 }
  - effect: emblem_epicycles
    dustSize: 0.7
    gradient: ["#ffbe0b", "#fb5607", "#ff006e"]
    parameters: { arms: 0, circles: 0 }
```

`mode: fourier` works for text too: `text: "Hi"` with `mode: fourier` writes the letters with
epicycles.
