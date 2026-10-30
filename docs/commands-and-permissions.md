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
| `/ve play <name> [x y z [world]] [scale] [duration]` | Plays an effect at your position, or at the given coordinates. `scale` multiplies the size, `duration` overrides the effect's duration in ticks (20 ticks = 1 s) | `/ve play torus 2.0 200` |
| `/ve stop [all\|<id>]` | Stops one running effect by ID, or everything with `all` | `/ve stop torus#3` |
| `/ve reload` | Reloads `config.yml`, every file in `plugins/VEngine/effects/` and `triggers.yml` | `/ve reload` |

Notes:

- Without coordinates the effect spawns at the caller (a player, an entity or a command block).
  Coordinates accept `~` relative values (`/ve play helix ~ ~2 ~`). The console has no position,
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
| `vengine.trigger.<id>` | Firing a [trigger](triggers.md) that has `permission: true`. Registered for every such trigger on load and `/ve reload` | op |
| `vengine.trigger.*` | Every trigger with `permission: true` | op |

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

- Every file in `plugins/VEngine/effects/*.yml` is one effect, and the file name (or its `id:` field)
  is the name used with `/ve play`.
- Bundled examples are copied only when missing - your edits are never overwritten, and new
  bundled files appear after updates.
- `/ve reload` reloads all effect files. Broken files are skipped with a clear warning in the
  console - see the troubleshooting section in
  [Writing your own effects](writing-effects.md#troubleshooting).
