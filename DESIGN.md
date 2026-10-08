# ForgeWorldGen v3 — Design

Fresh codebase, built from the Iris architecture study (Oct 7, 2026).
Original code throughout — Iris's *ideas*, never its source (GPL-3.0).

## The five steals (from `~/workspace/iris/NOTES.md`)

1. **Staged pipeline** — chunk generation as a chain of small stages, not one
   giant method. `GenStage` is a single-method interface; `GenPipeline` runs
   noise stages in `generateNoise` and surface stages in `generateSurface`.
2. **Determinism as a gate** — `SeedManager` derives every noise seed from the
   world seed via splitmix64 domains; `/fgen verify` hashes a heightmap grid
   so "same seed = same world" is provable, not asserted.
3. **Our own biome model** — `BiomeModel` maps temperature/humidity/height/
   volcano fields to `ForgeBiome`s, each with a vanilla *derivative* (Iris's
   trick: vanilla decorations, structures and mob spawning keep working
   because they see a vanilla biome) plus our own surface palette.
4. **Custom surfaces** — `SurfaceStage` repaints the top layers per biome
   palette in `generateSurface` (vanilla paints first per the API contract;
   we overwrite).
5. **Continuation-safe objects** — *v3.1*: multi-chunk structures placed via
   continuation bundles so chunk borders never cut them. (Architecture
   reserved; not in v3.0.)

## Terrain (the Riftline greatest hits)

`TerrainEngine` is a pure heightfield — every method is a pure function of
`(x, z, seed)`, so chunk borders always agree:

- **Continents & hills** — low-frequency fBm base + mid-frequency hills.
- **Mountain ranges** — ridged noise masked to rare regions, amplified.
- **Volcanoes** — jittered-grid placement (rare cells), radial cone profile,
  crater bowl, lava lake. Marks the `VOLCANIC` biome around it.
- **Rivers** — zero-crossing carve, carving valleys.
- **Scablands** — coulee channels carved into plateau country (the Dry Falls
  flavor: basalt flats, dry cataracts).

Vanilla keeps doing caves, decorations, structures and mobs on top of our
terrain (toggled by config), exactly like v2 did.

## v3.0 scope (this build)

- Staged pipeline: `TerrainStage` (bedrock/stone/water/basalt/lava) +
  `SurfaceStage` (per-biome palettes, snow, ash/scree patches).
- `ForgeBiomeProvider` backed by `BiomeModel` (11 biomes).
- `SeedManager`, validated `GenConfig`, `/fgen verify` + `/fgen biome`.
- Salvaged from v2: `/fgen create|tp|pregen|cancel|reload`, `WorldManager`
  (managed worlds + reattach), `PregenTask` (spiral pregen), spawn search.

## v3.1+ (reserved)

`CarveStage` (our own caves), `ObjectStage` with continuation-safe
multi-chunk placement, hot-reloadable terrain tuning (`/fgen reload`
already rebuilds engines).

## Constraints honored

- Paper 26.3, Java 25, `-Werror`, nullness annotations, zero deprecated APIs.
- Carvers/surface-rules are architecturally unavailable to custom generators
  (Iris gotcha) — we reimplement surfaces; caves stay vanilla for now.
- `generateSurface` runs *after* vanilla's surface step (API contract) —
  our stage repaints; slight double work, fully deterministic.
