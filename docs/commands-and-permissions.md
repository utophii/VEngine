---
title: Commands and permissions
section: Server owners
lede: "Every /ve command, the permission nodes behind them and the config.yml keys."
---

All commands live under `/vengine`, alias `/ve`. They default to operators.

## Commands

| Command | Description | Example |
|---|---|---|
| `/ve help` | In-game command overview | `/ve help` |
| `/ve list` | Registered effects, plus currently running instances with their IDs | `/ve list` |
| `/ve play <name> [x y z [world]] [scale] [duration]` | Plays an effect at your position, or at the given coordinates. `scale` multiplies the size, `duration` overrides the effect's duration in ticks (20 ticks = 1 s) | `/ve play basic 2.0 200` |
| `/ve stop [all\|<id>]` | Stops one running effect by ID, or everything with `all` | `/ve stop basic#3` |
| `/ve reload` | Reloads `config.yml`, every file in `plugins/VEngine/effects/` and `triggers.yml` | `/ve reload` |
| `/ve validate [effect]` | Checks effect YAML files on disk without reloading or playing them. Omit the name to check every file | `/ve validate basic` |
| `/ve info [effect]` | Shows engine statistics, or details of a loaded effect | `/ve info basic` |

Notes:

- Without coordinates the effect spawns at the caller (a player, an entity or a command block).
  Coordinates accept `~` relative values (`/ve play basic ~ ~2 ~`). The console has no position,
  so it needs absolute coordinates. Without a world name the first loaded world is used.
- Running effects finish by themselves, `/ve stop` is only needed to end them early.
  Finished effects disappear from `/ve list` automatically.

## Permissions

| Permission | Allows | Default |
|---|---|---|
| `vengine.use` | Using `/ve` at all | op |
| `vengine.effect.spawn` | `/ve play` | op |
| `vengine.effect.stop` | `/ve stop` | op |
| `vengine.list` | `/ve list` | op |
| `vengine.reload` | `/ve reload` | op |
| `vengine.validate` | `/ve validate` | op |
| `vengine.info` | `/ve info` | op |
| `vengine.trigger.<id>` | Firing a [trigger](triggers.md) that has `permission: true`. Registered for every such trigger on load and `/ve reload` | op |
| `vengine.trigger.*` | Every trigger with `permission: true` | op |

## Validation and inspection

`/ve validate` checks YAML syntax, duplicate keys, field names and types against the bundled
[JSON Schema](schemas/effect.schema.json), then builds effects with the normal parser to check
formulas, imported shape files, layer references and timing. It also reports duplicate effect IDs,
missing emitter targets and cycles in `emitters:` / `then:`. Each diagnostic names its file,
schema errors also identify the field path. YAML syntax errors include the parser's line and column.
Warnings identify settings the loader ignores or replaces with defaults, errors identify invalid
files or references. More than 20 diagnostics are written in full to the server log.

`/ve validate <effect>` accepts an effect ID such as `test:3d_heart` or a path relative
to `effects/` such as `test/3d_heart.yml`. The `.yml` extension is optional.
It reads the whole effect directory to resolve dependencies, then shows the matching files.
Validation uses the files currently on disk and primitives supplied by other plugins. It does
not reload configuration, change registered effects, stop instances, or play effects.
It does not evaluate every animation frame or resolve player-specific placeholder values.
`config.yml` and `triggers.yml` are outside this command's scope.

`/ve info` shows the plugin version, registered and running effect counts, view distance,
particles sent last tick, current quality percentage and emitted effect limit.
`/ve info <effect>` describes the loaded effect, including its type, layer count and default
duration when available, emitted effects, follow configuration and running instance count.
Disk edits appear in `info` only after `/ve reload`.

## Configuration

`plugins/VEngine/config.yml`:

| Key | Default | Description |
|---|---|---|
| `view-distance` | `64` | Particle view-distance culling radius in blocks. A player farther than this from a particle receives no packets for it. Raise for huge shows, lower to save bandwidth |
| `max-emitted-effects` | `256` | How many effects spawned by `emitters:` / `then:` may play at once across the server |
| `particle-budget.global-limit` | `0` | Particles per tick sent across the whole server, `0` = unlimited |
| `particle-budget.player-limit` | `3000` | Particles per tick one player receives, `0` = unlimited. Protects client FPS in crowded areas |
| `particle-budget.lod` | `16: 1.0`, `32: 0.6`, `64: 0.3` | Distance LOD as `blocks: keep fraction`, interpolated between steps. Empty = full density everywhere |
| `particle-budget.auto-quality.enabled` | `true` | Thin particles out while the server lags |
| `particle-budget.auto-quality.levels` | `40: 0.75`, `45: 0.5`, `50: 0.25` | `average tick ms: keep fraction`. Checked once a second |
| `particle-budget.auto-quality.recover-margin` | `3` | Milliseconds below a threshold before quality steps back up |

Over a limit, every effect is thinned out evenly instead of being cut off: each particle has a
fixed rank, and a player receives the particles whose rank is below the current keep fraction.
Shapes stay whole, and the kept points do not flicker from frame to frame. Limits use the previous
tick's demand, so they settle within a tick or two. Display entities are not budgeted. Effects
can opt out with `budget: exempt` or `lod: false`, see
[Writing your own effects](writing-effects.md#particle-budget).

Changes apply with `/ve reload`.

PlaceholderAPI tokens that read this value, and the rest of the engine state, are listed in
[Placeholders](placeholders.md).

## Effect files

- Each `.yml` file under `plugins/VEngine/effects/` defines one effect. A file in the root
  uses its filename or `id:`. A pack folder adds a namespace, so `test/3d_heart.yml`
  can be played with `/ve play test:3d_heart`.
- Bundled examples are copied only when missing - your edits are never overwritten, and new
  bundled files appear after updates.
- `/ve reload` reloads all effect files. Broken files are skipped with a clear warning in the
  console - see the troubleshooting section in
  [Writing your own effects](writing-effects.md#troubleshooting).
