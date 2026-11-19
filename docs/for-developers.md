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

Use `compileOnly` in Gradle. After the first Maven Central release, connect the API with:

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.github.utophii:vengine-api:0.1.2")
    compileOnly("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
}
```

Paper API is supplied by the server and must be declared separately for compilation.
Use Maven scope `provided` for both dependencies in a Maven project.
The VEngine plugin supplies the API at runtime, so do not shade it into your plugin.
Declare `depend: [VEngine]` in your Bukkit `plugin.yml` when your plugin requires VEngine.

Before the first Central release, or when working on local changes, run
`./gradlew :vengine-api:publishToMavenLocal` in this repository and add `mavenLocal()` to
your consuming project's repositories. This publishes only `vengine-api`.
The `vengine-core` module is still consumed as a jar with
`compileOnly(files("libs/vengine-core-<version>.jar"))`, alongside the API dependency.
Calling the DSL or `FXEngine.registerProvider` requires this core jar as well.

Maintainers can follow [Publishing the API](publishing.md) to release it to Maven Central.

## Playing effects (DSL)

```kotlin
import com.utophii.api.playEffect

player.location.playEffect("basic") {
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
