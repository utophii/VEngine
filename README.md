# VEngine

![Demo](assets/demo.gif)

  [![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.6--26.3-brightgreen)](https://www.minecraft.net/)
  [![Discord](https://img.shields.io/discord/1324946459526955029?color=7289DA&label=Discord&logo=discord)](https://discord.gg/NAWsxe3J3R)
  [![Modrinth](https://img.shields.io/modrinth/dt/vengine?logo=modrinth)](https://modrinth.com/plugin/vengine)
  [![Boosty](https://img.shields.io/badge/Support%20on-Boosty-orange)](https://boosty.to/nonxedy)

VEngine is an animated particle effect engine for Minecraft servers. It lets server admins play
visual effects - spirals, rings, portals, comets, beams - anywhere in the world, and create new
ones by editing plain text files, no coding required.

> **Before you download:** VEngine is an engine, not a plug-and-play plugin, and it is not trying
> to be user-friendly. The bundled effects work out of the box, but creating your own effects
> means writing math formulas (basic trigonometry, parametric curves, angles in radians) in YAML
> files, and reading the wiki when something is unclear. If you want a plugin with hundreds of
> ready-made effects and an in-game editor, VEngine is not the right tool for you.

It is:

* **easy to customize** - every effect is a small readable YAML file; change particles, colors,
  size, rotation, motion paths and animations, then reload without a restart.
* **alive** - effects can glide between coordinates, tilt and rotate over time, and react to
  keyframe animation tracks.
* **fast** - frame math runs off the main thread, and players beyond the view distance receive
  no particles at all.
* **free** - available for download and usage at no cost, and open source so it can remain free.

## Useful links
The plugin has [documentation available on the wiki](https://github.com/utophii/VEngine/wiki). Please use the resources there before coming to us directly for support.

Support for the plugin is provided on [Discord](https://discord.gg/NAWsxe3J3R). If you have a question which cannot be answered by reading the wiki, the best place to ask it is there.

If you would like to report a bug, please [open a ticket on GitHub](https://github.com/utophii/VEngine/issues).

## License
VEngine is open source software. Please see [`LICENSE`](LICENSE) for more info.