---
title: Following entities
section: Server owners
lede: "Auras, wings and trails: effects that move and turn with a player or mob."
---

An effect with a `follow:` block sticks to whoever plays it. The block goes at the top level of
the effect file:

```yaml
id: halo
duration: 6000
follow:
  offset: {x: 0, y: 2.2, z: 0}
  rotation: body
  stopWithEntity: true
layers:
  - effect: torus
    particle: END_ROD
    scale: 0.4
```

`follow: true` follows with all defaults.

| Key | Default | Description |
|---|---|---|
| `offset` | `0, 0, 0` | Blocks from the entity's feet (or eyes, with `look`), in the entity's frame: +Z forward, +Y up, +X to its left |
| `rotation` | `none` | Which orientation the effect inherits, see below |
| `stopWithEntity` | `true` | Stop when the entity dies, despawns or the player leaves. `false` leaves the effect at the last position until its duration ends |

| `rotation` | Turns with | Use for |
|---|---|---|
| `none` | nothing, position only | Halos, ground circles |
| `body` | body yaw (head yaw for non-living entities) | Wings, back items, auras that face where the body points |
| `head` | head yaw, no pitch | Effects in front of the face that stay level |
| `look` | head yaw and pitch, pivoting at the eyes | Beams and sights that point where the player looks |
| `velocity` | direction of movement | Projectile and mount trails |

Author the shape as if the entity stood at the origin facing +Z: with `rotation: body`, a layer
at `offset: {z: -0.4}` stays behind the back as the player turns.

## Who is followed

- `/ve play <effect>` without coordinates: the player who ran it. With coordinates, the effect
  stays at that position.
- [Triggers](triggers.md): the entity of `at`, when `at` is an entity (victim, attacker, player).
- [MythicMobs](mythicmobs.md) `vengine{...}` mechanic: each entity target, unless `follow=false`.
- Plugins: `FXEngine.play(name, FollowOptions(entity))` or `entity.playEffect(...)`, see the
  [Developer guide](developer-guide.md). Code can override every key of the block, and keys it leaves
  out fall back to the effect file.

Effects without a `follow:` block never follow anything, so the same `/ve play` command can place
a fixed effect and attach an aura depending on the file.

## What moves

The whole effect is moved and turned every tick at render time, so modifiers, motion paths and
animation all work in the followed frame. `emitters:` and `then:` of a followed effect follow the
same entity, and their `offset` turns with it. Placeholders such as `%vengine_nearest%` measure
to the followed entity's current position.

## Display models and PacketEvents

Effects with a [`display:` block](display-entities.md) work too. Without extra plugins, the
models are real display entities moved by the server every tick, so they can trail slightly
behind a running player.

With [PacketEvents](https://modrinth.com/plugin/packetevents) installed, every display model
exists only on the clients, as packets. Models of a followed effect ride the entity as
client-side passengers, so the client moves them together with it, without delay. Nothing needs
to be configured: the console prints
`PacketEvents found: display models are sent to clients as packets.` on startup.
