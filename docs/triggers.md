---
title: Triggers
section: Server owners
lede: "Play effects when something happens in the game: hits, deaths, joins, teleports and item use."
---

`plugins/VEngine/triggers.yml` connects game events to effects without any other plugin. Every
top-level key is one trigger. The bundled file only contains commented-out examples, so nothing
fires until you write or uncomment a trigger and run `/ve reload`.

```yaml
crit_spark:
  event: hit
  effect: sphere
  at: victim
  cooldown: 10
  filter:
    attacker: PLAYER
  options:
    scale: "0.4 + damage / 20"
    duration: 10
```

## Keys

| Key | Default | Description |
|---|---|---|
| `event` | required | `hit`, `death`, `join`, `teleport` or `item_use`. `on:` works as an alias |
| `effect` | - | Any name from `/ve list` |
| `skill` | - | A MythicMobs skill to cast as well as, or instead of, the effect. See [MythicMobs](mythicmobs.md#skills-from-triggers) |
| `at` | first target of the event | Where the effect plays, see [Targets](#targets) |
| `permission` | everyone | `true` means `vengine.trigger.<id>`, any other string is used as the permission node |
| `cooldown` | `0` | Ticks before the same trigger may fire again for the same player or entity |
| `chance` | `1` | Probability from `0` to `1` |
| `delay` | `0` | Ticks to wait before playing, handy for `join` |
| `filter` | - | Conditions, see [Filters](#filters) |
| `options` | - | `scale`, `duration` and `parameters.<name>`, numbers or formulas, see [Options](#options) |

A trigger needs `effect`, `skill` or both. A trigger that names an effect which does not exist
yet is kept with a warning and starts working once the effect is loaded.

## Targets

| Event | `at` values (first is the default) | Fires on |
|---|---|---|
| `hit` | `victim`, `attacker` | A living entity takes damage from another entity |
| `death` | `victim`, `killer` | A living entity dies |
| `join` | `player` | A player joins |
| `teleport` | `to`, `from` | A player teleports |
| `item_use` | `player`, `block` | A player right-clicks with an item in hand |

- `death` + `victim` plays at the spot where the entity died, since the body is gone.
- `item_use` + `block` plays on top of the clicked block, clicking air does nothing for that target.
- When `at` is an entity and the effect has a [`follow:` block](following-entities.md), the
  effect follows that entity, exactly as with `/ve play`.

## Filters

All filters are optional and take a single value or a list. An event fires the trigger only when
every filter matches.

| Filter | Events | Matches |
|---|---|---|
| `entity` | `hit`, `death` | Victim type, e.g. `[ZOMBIE, PLAYER]` |
| `attacker` | `hit`, `death` | Attacker or killer type. Arrows and other projectiles count as their shooter |
| `cause` | `hit`, `death`, `teleport` | Damage cause (`ENTITY_ATTACK`, `PROJECTILE`, ...) or teleport cause (`ENDER_PEARL`, `COMMAND`, ...) |
| `item` | `item_use` | Material in hand, e.g. `BLAZE_ROD` |
| `mythic` | all | MythicMobs mob type of the victim or player, e.g. `[SkeletonKing]` |
| `mythic_attacker` | `hit`, `death` | MythicMobs mob type of the attacker or killer |

Names are case-insensitive. Unknown entity types or materials are reported in the console and
ignored.

## Who the trigger belongs to

Permission, cooldown and placeholders are checked against one entity of the event, the *actor*:

- `hit` and `death`: the attacker or killer when it is a player, otherwise the victim.
- `join`, `teleport`, `item_use`: the player.

So `permission: true` on a `hit` trigger asks whether the attacking player may produce the
effect, and `cooldown: 20` limits each attacker to one effect per second. A permission that is
not granted anywhere defaults to operators only.

## Options

`options` override the effect's size, length and parameters for this one play. Each value is a
number or a formula over these variables:

| Variable | Events | Value |
|---|---|---|
| `random` | all | A new random number from `0` to `1` for every event |
| `damage` | `hit` | Final damage of the hit |
| `health` | `hit` | Victim health before the hit |
| `max_health` | `hit`, `death` | Victim max health |
| `distance` | `teleport` | Blocks travelled, `0` across worlds |

PlaceholderAPI placeholders work inside formulas and are read for the actor, for example
`scale: "0.5 + %player_level% / 50"`. Non-numeric values count as `0`. Formula syntax is the
same as in effects, see [Parametric effects](parametric-effects.md).

```yaml
options:
  scale: "0.6 + random * 0.4"
  duration: 40
  parameters:
    radius: "1 + damage / 10"
```

Without `duration`, a formula atom (a single-shape effect) plays for 60 ticks, while a layered
effect keeps its own duration.

## Examples

```yaml
mob_soul:
  event: death
  effect: helix
  filter:
    entity: [ZOMBIE, SKELETON, CREEPER]
    attacker: PLAYER
  options:
    duration: 40

vip_join:
  event: join
  effect: cosmic_gate
  permission: true          # vengine.trigger.vip_join
  delay: 20

pearl_trail:
  event: teleport
  effect: cosmic_gate
  at: from
  filter:
    cause: ENDER_PEARL
  options:
    scale: 0.6
    duration: 40

staff_cast:
  event: item_use
  effect: rk4_comet
  cooldown: 40
  filter:
    item: BLAZE_ROD
```

## Notes

- Triggers react after other plugins: a hit or teleport that another plugin cancelled does not
  fire anything.
- `/ve reload` re-reads `triggers.yml` and resets all cooldowns.
- Triggered effects are normal running effects: they appear in `/ve list` and count towards the
  [particle budget](commands-and-permissions.md#configuration).
