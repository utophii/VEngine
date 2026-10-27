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
| Server owner / admin | [Commands and permissions](commands-and-permissions.md), [Placeholders](placeholders.md), then [Writing your own effects](writing-effects.md) and [Display entities](display-entities.md) |
| Plugin developer | [For developers](for-developers.md) |

## Requirements and installation

- Paper or Spigot **1.21.6 - 26.x**, Java 21+ (26.x servers need Java 25; the jar runs on both)
- Put `VEngine-<version>.jar` into `plugins/`, restart, run `/ve play traveling_comet`
- [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) is optional. When it is installed, `%vengine_*%`
  placeholders are available - see [Placeholders](placeholders.md)
- The `vengine-api` / `vengine-core` jars on the download page are for developers only
- VEngine sends anonymous usage statistics (number of worlds, plugins and online players) and
  plugin error reports to [FastStats](https://faststats.dev/)

After the first start you get:

```
plugins/VEngine/
├── config.yml        # particle view distance
└── effects/          # one .yml per effect, hot-reloaded by /ve reload
```

## Deeper documentation

- Display entity rendering (`display:` block): [Display Entities](display-entities.md)
- Formula functions, variables and YAML schema: [Parametric Effects](parametric-effects.md)
- Kotlin DSL and SPI reference: [Developer Guide](developer-guide.md)
