---
title: Placeholders
section: Server owners
lede: "PlaceholderAPI tokens that expose registered and running effects."
---

If [PlaceholderAPI](https://placeholderapi.com/) is installed, VEngine registers the `vengine`
expansion itself. There is nothing to download from the eCloud, and the plugin runs the same
without it.

```
/papi parse me %vengine_effects%
```

## Tokens

| Placeholder | Meaning |
|---|---|
| `%vengine_effects%` | Number of registered effects |
| `%vengine_effects_list%` | Registered effect names, comma-separated |
| `%vengine_active%` | Spawned effects that are still running |
| `%vengine_active_list%` | Their ids (`torus#3`), sorted |
| `%vengine_active_names%` | Unique names of those effects |
| `%vengine_view_distance%` | Particle view distance from `config.yml` |
| `%vengine_nearest%` | Name of the running effect closest to the player |
| `%vengine_nearest_id%` | Id of that effect |
| `%vengine_nearest_distance%` | Distance to it, in blocks |
| `%vengine_nearby%` | Running effects in the player's world, within view distance |
| `%vengine_quality%` | Current auto-quality in percent (`100` = full) |
| `%vengine_particles_tick%` | Particles sent to players during the last tick |

## Notes

- `%vengine_nearest%`, `%vengine_nearest_id%`, `%vengine_nearest_distance%` and `%vengine_nearby%`
  need an online player. From console they stay unresolved.
- Distance is measured to the spawn point, not to particles a motion path has already moved.
- Internal layers of a scripted effect are not counted. One `/ve play` is one running effect.
- An empty `%vengine_nearest%` means nothing is running in that world.
- `/papi reload` does not unload this expansion.
