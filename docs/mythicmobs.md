---
title: MythicMobs
section: Server owners
lede: "Play and stop VEngine effects from MythicMobs skills, and cast skills from triggers."
---

When [MythicMobs](https://mythiccraft.io/index.php?resources/mythicmobs.1/) is installed,
VEngine registers two skill mechanics. Nothing needs to be configured: the console prints
`MythicMobs found: vengine and vengine-stop mechanics are available.` on startup.

## Playing an effect

```yaml
SkeletonKingRoar:
  Skills:
  - vengine{effect=cosmic_gate;scale=1.5;duration=80} @self
  - vengine{effect=sphere;p.radius=3;p.points=240} @PlayersInRadius{r=10}
```

| Option | Alias | Default | Description |
|---|---|---|---|
| `effect` | `e` | required | Effect name from `/ve list` |
| `scale` | `s` | `1` | Size multiplier |
| `duration` | `d` | effect's own | Ticks. Formula atoms (single-shape effects) default to 60 |
| `follow` | `f` | `true` | Entity targets are followed when the effect has a [`follow:` block](following-entities.md). `false` plays it where the entity stood |
| `p.<name>` | - | - | Sets an effect parameter, e.g. `p.radius=3` |

The mechanic is also available as `vfx{...}`.

- It works with entity targeters (`@self`, `@target`, `@PlayersInRadius`) and location targeters
  (`@origin`, `@TargetLocation`, `@Ring`). An effect plays once per target.
- `effect`, `scale`, `duration` and `p.<name>` accept MythicMobs placeholders such as
  `<caster.level>` or `<skill.var.size>`. `scale`, `duration` and `p.<name>` also accept math,
  e.g. `scale=<caster.level>/10` or `p.radius=<caster.hp>/5`.
- Parameter names keep their case (`p.majorRadius=2` sets `majorRadius`).
- PlaceholderAPI placeholders inside the effect's YAML are read for the target player, or for the
  caster when the target is not a player.

## Stopping effects

```yaml
- vengine-stop{effect=cosmic_gate} @self
- vengine-stop @self
```

`vengine-stop` stops the effects that the `vengine` mechanic played on the target entity or that
the target cast. Without `effect` it stops all of them. Aliases: `venginestop`, `vfx-stop`.

A typical use is an aura for the lifetime of a phase:

```yaml
PhaseTwoStart:
  Skills:
  - vengine{effect=crystal_ward;duration=6000} @self
PhaseTwoEnd:
  Skills:
  - vengine-stop{effect=crystal_ward} @self
```

## Skills from triggers

[Triggers](triggers.md) can cast a MythicMobs skill when a game event happens, with or without a
VEngine effect, and can filter by MythicMobs mob type:

```yaml
king_fall:
  event: death
  effect: cosmic_gate
  skill: KingDeathCurse
  filter:
    mythic: SkeletonKing        # the victim's mob type
```

- The entity of `at` casts the skill. For a fixed place (death victim, teleport `from`, a block)
  the attacker, killer or player casts it at that place instead.
- `@trigger` in the skill is the other side of the event: the attacker when the victim casts,
  and the victim when the attacker casts.
- `filter.mythic` matches the victim or player, `filter.mythic_attacker` the attacker or killer.

Without MythicMobs, triggers that use `skill` or `mythic` filters load with a warning and those
parts do nothing.
