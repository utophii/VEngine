# YAML effect goldens

These small fixtures cover points, curves, surfaces, RK4 integration, deterministic
iteration, field projection, and inline layers with motion and keyframe animation.
They load through `YamlEffectLoader` without a server or global primitive registry.

Each committed snapshot contains every particle in order, with rows of
`[x, y, z, value, age]`. `null` means the optional value or age is absent.
`hasValues` also records whether the frame supplies spatial gradient values.
Layered effects expose only coordinates through `calculate`, so their appearance
columns are absent. These tests do not cover particle rendering or Bukkit scheduling.

`YamlEffectFixtures.inputs` defines the center, times, parameter overrides and
transforms. Frames cover initial and animated output, overridden parameters,
layer start/end boundaries, and a field period wrapping. Tests revisit inputs
in reverse order to check cache and pooled state reuse. Comparisons use an
absolute tolerance of `1e-8` and report the fixture, frame, particle and axis.

Run the golden and concurrency tests from the repository root:

```sh
./gradlew :vengine-core:test --tests '*YamlEffectGoldenTest' --tests '*YamlEffectConcurrencyTest'
```

After an intentional behavior change, regenerate snapshots explicitly:

```sh
./gradlew :vengine-core:updateEffectGoldens
git diff -- vengine-core/src/test/resources/golden/snapshots
```

Review every changed coordinate and particle count before accepting new goldens.
Ordinary tests never create or overwrite snapshots, and missing snapshots fail.
Initial snapshots capture the current implementation as regression baselines,
they complement the existing analytic geometry tests.

The concurrency test shares one freshly loaded effect across four workers.
Each round starts at a barrier and uses different centers, transforms and inputs,
including omitted parameters after overrides. Immutable reference frames come from isolated
sequential instances. One hundred rounds exercise cold caches, cache replacement,
expression state reuse and the shared particle buffer pool. Barrier and future
waits are bounded, and the executor is shut down even on failure. This is a
repeatable regression stress test, not a proof of thread safety.