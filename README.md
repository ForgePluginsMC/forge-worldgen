<div align="center">

![ForgeWorldGen logo](logo.png)

# ForgeWorldGen

[![Paper 26.3](https://img.shields.io/badge/Paper-26.3-blue)](https://papermc.io/)
[![Java 25](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net/)
[![Version](https://img.shields.io/badge/version-2.0.0-gold)](https://github.com/ForgePluginsMC/forge-worldgen)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

**Vanilla-character terrain, gently smoothed. One change at a time.**

</div>

## What it is

ForgeWorldGen v2 is a Paper plugin that generates terrain with the look and
proportions of vanilla Minecraft — rolling plains and hills, occasional
mountain ranges, oceans and rivers — with exactly one deliberate change: a
gentle smoothing pass over the heightfield, controlled by a single config
value (`terrain.smoothing`).

An honest note on the design: a Paper plugin cannot reach inside Mojang's
generator to "smooth" it. So v2 implements its own terrain core tuned to
vanilla's character, and leaves **everything else 100% vanilla**: biomes,
surface painting, trees, caves, ores, decorations, structures, villages and
mobs all come from the vanilla pipeline untouched.

v2 is a deliberate reset. The v1.x line grew every feature imaginable —
custom biomes, volcanoes, geysers, wheat fields, heat maps — and lost the
plot. v2 deletes all of it and changes one thing at a time.

## Features

- **Vanilla-look terrain core** — continental landmasses, gentle hills, rare
  mountain ranges, carved rivers, oceans at sea level 62
- **One smoothing control** — `terrain.smoothing` (0.0–1.0, default 0.35):
  a gentle low-pass over the heightfield that softens jagged edges while
  keeping the land's character
- **Fully vanilla everything else** — the plugin does not override the biome
  provider or the surface step, so vanilla biomes, trees, villages, caves
  and mobs behave exactly as in a normal world
- **Fast** — original allocation-free simplex/fBm noise; the whole terrain
  core is a pure function of coordinates and seed, chunk-border safe and
  deterministic
- **World management** — `/fgen create` spins up named worlds with the
  generator attached (and re-attached automatically after restarts)
- **Pre-generation with live speed readout** — `/fgen pregen` reports
  chunks/sec as it works

## Requirements

- Paper 26.3 (or a fork with the Paper 26.3 API)
- Java 25

## Installation

1. Drop `ForgeWorldGen-2.0.0.jar` into your server's `plugins/` folder.
2. Restart the server.
3. Create a world: `/fgen create myworld` (console or in-game).

## Commands

| Command | Permission | Description |
|---|---|---|
| `/fgen create <name> [seed]` | `fgen.admin` | Create a ForgeWorldGen world |
| `/fgen tp <world>` | `fgen.tp` | Teleport to a world |
| `/fgen pregen <world> <radius>` | `fgen.admin` | Pre-generate chunks with live chunks/sec readout (max 48) |
| `/fgen cancel` | `fgen.admin` | Stop a running pre-generation |
| `/fgen reload` | `fgen.admin` | Reload config.yml (applies to new chunks) |

Tab completion is provided for subcommands and world names.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `fgen.admin` | op | Create worlds, pre-generate, cancel, reload |
| `fgen.tp` | op | Teleport to ForgeWorldGen worlds |

## Configuration (config.yml)

```yaml
generation:
  sea-level: 62          # ocean level; terrain below this fills with water

terrain:
  smoothing: 0.35        # gentle low-pass on the heightfield, 0.0 (off) to 1.0

features:
  caves: true            # vanilla cave carvers
  decorations: true      # vanilla decorations (ores, trees, flowers…)
  structures: true       # vanilla structures (villages…)
  mobs: true             # mob spawning

world:
  auto-manage: true      # re-attach the generator to managed worlds on startup

managed-worlds: []       # maintained by the plugin; hands off

messages:
  prefix: "<gold>[ForgeWorldGen]</gold> "
```

## Building

The sandbox cannot run the Gradle daemon (loopback TCP is intercepted), so
`build.sh` compiles with `javac` directly against the Paper API jars:

```sh
./build.sh   # produces ForgeWorldGen-<version>.jar, -Werror clean
```

`build.gradle.kts` is the canonical build for machines with a working
Gradle (Gradle 9.7.1, Java 25). Keep both in sync.

## How the terrain core works

`TerrainModel` (about 100 lines, readable in one sitting) builds each
column's height from four noise fields:

- **continental** — very-low-frequency landmasses and oceans
- **hills** — rolling mid-frequency relief plus a fine detail octave
- **relief** — a low-frequency mask; mountains only appear where it runs
  high, so ranges are rare and regional
- **rivers** — shallow channels carved where the river noise crosses zero

`heightAt()` blends the raw height with a 5×5 box blur by `smoothing`.
Everything is deterministic per seed and identical on both sides of every
chunk border.

## Roadmap

- One change at a time. The next change is chosen by testing, not by
  brainstorming.
- Future idea: Nether generation with no bedrock roof — a black void sky
  (like the End's), lava lakes, basalt.

## License

MIT — see [LICENSE](LICENSE).

---

*ForgeWorldGen is a third-party Paper plugin. Not affiliated with
MinecraftForge, Mojang, or Microsoft.*
