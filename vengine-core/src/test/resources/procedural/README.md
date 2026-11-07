# Procedural regression data

`reference.yml` was captured from the original 14 Kotlin effects before their removal.
It contains 42 cases, at ticks 0, 17 and 90. Every case uses 31 requested points or steps,
a center of `(2, 3, 5)`, duration 100, scale 1.25 and roll 0.2. Tick 17 also changes several
geometry parameters. Coordinates and available gradient values are checked to an absolute
tolerance of `1e-8` by `ProceduralReferenceTest`.

The corresponding configurable definitions are in `../recipes/`. These resources, including
the old YAML examples and imported shape assets, are excluded from the distributable plugin.
They exercise the general generators without registering predefined effects in production.

A temporary comparison against the original classes used 60 warmup frames and 100 measured
frames per effect in one JVM. Frames cycled through ticks 0 to 19 with default parameters.
Representative final measurements in milliseconds per calculation were:

| Recipe | Original | General generator |
|---|---:|---:|
| Sphere | 0.021 | 0.035 |
| Lorenz | 0.021 | 0.023 |
| RK4 trajectory | 0.129 | 0.199 |
| Julia | 0.254 | 0.045 |
| Sierpinski tetrahedron | 0.057 | 0.059 |
| Menger outer faces | 0.101 | 0.065 |
| Square nodal field | 0.638 | 0.414 |
| Circular nodal field | 0.557 | 0.353 |

These are local diagnostic measurements, not timing assertions or a server benchmark.
They include cache reuse. Continuously changing equations invalidate cached geometry and
can cost substantially more than handwritten loops. Numerical regression tests also exercise
changing parameters, animation, transforms and concurrent calculations.
