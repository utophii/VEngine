---
title: For developers
section: Developers
lede: "Play effects from your plugin through the DSL, or register new ones through the SPI."
---

VEngine can be driven from other plugins in two ways: play registered effects through the DSL,
or register entirely new effects through the SPI.

## Artifacts

| Artifact | Compile against it when | Install on the server? |
|---|---|---|
| `VEngine-<version>.jar` | - (it is the plugin itself) | yes |
| `vengine-api-<version>.jar` | implementing `EffectProvider` / passing VEngine types | no |
| `vengine-core-<version>.jar` | calling the DSL or `FXEngine` | no |

Use `compileOnly` in Gradle. Until artifacts are published to a repository, depend on the jar
files directly (`compileOnly(files("libs/vengine-api-<version>.jar"))`), or install them into
your local Maven repository with `./gradlew publishToMavenLocal`.

## Playing effects (DSL)

```kotlin
import com.utophii.api.playEffect

player.location.playEffect("helix") {
    radius(0.8)
    duration(200L)                                   // ticks
    orientation(pitch = Math.PI / 2)                 // lay it flat
    modifiers { vortex(strength = 0.4) }
    moveTo(targetLocation, ticks = 100L)             // glide to exact coordinates
}?.also { handle ->
    // handle.cancel() stops it early
}
```

Also available: `location.playParametric { ... }` for one-off formula effects defined in code,
and `display(DisplayOptions(...))` inside any config block to render item/block/text display
entities instead of particles.
The full option-by-option reference lives in
[Developer guide](developer-guide.md).

## Registering effects from your plugin (SPI)

```kotlin
import com.utophii.api.EffectProvider
import com.utophii.engine.FXEngine

override fun onEnable() {
    FXEngine.registerProvider(EffectProvider {
        listOf(MyEffect())   // any ParticleEffect
    })
}
```

Provider effects become first-class citizens: they show up in `/ve list`, work with the DSL,
and can be used as layer bases in scripted YAML effects.

## Notes

- Works on Paper and Spigot, 1.21.6 - 26.x. VEngine uses only the stable Bukkit API surface -
  no NMS, so your integration does not care about server versions either.
- Frame math runs async, `spawnParticle` is the only synchronous step. Your `ParticleEffect.play`
  implementation is called from scheduler threads - keep it allocation-light and avoid touching
  the world.
