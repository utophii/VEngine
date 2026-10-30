---
title: Home
section: Overview
heading: "Particle effects, <em>written in YAML</em>"
lede: "An animated particle effect engine for Minecraft servers."
hero_media: true
---

VEngine is an animated particle effect engine for Minecraft servers. It plays visual effects
(spirals, rings, comets, beams, portals) anywhere in the world and lets you author your own
effects as plain YAML files - no Java, no restart, no client mods. Besides particles, effects
can render solid item, block and text models through vanilla display entities.

These docs are split by audience:

| You are | Read |
|---|---|
| Server owner / admin | [Commands and permissions](commands-and-permissions.md), [Triggers](triggers.md), then [Writing your own effects](writing-effects.md), [Following entities](following-entities.md) and [Display entities](display-entities.md) |
| Using MythicMobs | [MythicMobs](mythicmobs.md) |
| Plugin developer | [For developers](for-developers.md) |

## Requirements and installation

- Paper or Spigot **1.21.6 - 26.x**, Java 21+ (26.x servers need Java 25, the jar runs on both)
- Put `VEngine-<version>.jar` into `plugins/`, restart, run `/ve play traveling_comet`
- Optional plugins, picked up automatically:
  - [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi): `%vengine_*%` placeholders, and
    placeholders inside effect formulas - see [Placeholders](placeholders.md)
  - [MythicMobs](https://mythiccraft.io/index.php?pages/official-mythicmobs/): `vengine` skill
    mechanics - see [MythicMobs](mythicmobs.md)
  - [PacketEvents](https://modrinth.com/plugin/packetevents): client-side display models - see
    [Following entities](following-entities.md#display-models-and-packetevents)
- The `vengine-api` / `vengine-core` jars on the download page are for developers only
- VEngine sends anonymous usage statistics (number of worlds, plugins and online players) and
  plugin error reports to [FastStats](https://faststats.dev/)

After the first start you get:

```
plugins/VEngine/
├── config.yml        # view distance, particle budget, limits
├── triggers.yml      # effects played on game events
├── effects/          # one .yml per effect, hot-reloaded by /ve reload
└── shapes/           # pictures, fonts, OBJ and SVG files for imported shapes
```

## Deeper documentation

- Display entity rendering (`display:` block): [Display Entities](display-entities.md)
- Formula functions, variables and YAML schema: [Parametric Effects](parametric-effects.md)
- Implicit surfaces and plants: [SDF surfaces and L-systems](sdf-and-lsystems.md)
- Atoms built into the plugin and their parameters: [Built-in primitives](built-in-primitives.md)
- Kotlin DSL and SPI reference: [Developer Guide](developer-guide.md)
