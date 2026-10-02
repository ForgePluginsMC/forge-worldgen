package com.forgeplugins.worldgen.gen;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ForgeWorldGen's chunk generator. The entire terrain pass — stone fill,
 * surface mosaic and decoration, water, alpine lakes, volcano craters, lava
 * pools, geysers, waterfalls and trees — happens in {@link #generateNoise}, a
 * single sweep over the chunk's 256 columns, so no work is ever repeated.
 * Noise fields are allocation-free and the instance is safe for parallel
 * chunk generation.
 */
public final class ForgeChunkGenerator extends ChunkGenerator {

    private final Object modelLock = new Object();
    private volatile TerrainModel model;
    private volatile GenSettings settings;

    /** Crater bowl radius in blocks; inside this the summit is carved concave. */
    private static final double CRATER_RADIUS = 12.0;
    /** How deep the crater bowl is carved at the summit centre. */
    private static final int CRATER_DEPTH = 22;
    /** Basalt rim ring width past the crater edge. */
    private static final double RIM_WIDTH = 5.0;
    /** Extra height of the basalt rim ring. */
    private static final int RIM_LIFT = 2;

    private final TreePlacer trees;
    private final ForgeBiomeProvider biomeProvider;

    public ForgeChunkGenerator(@NotNull GenSettings settings,
                               @Nullable BlockData oakLeaves,
                               @Nullable BlockData spruceLeaves,
                               @Nullable BlockData acaciaLeaves) {
        this.settings = settings;
        this.trees = new TreePlacer(oakLeaves, spruceLeaves, acaciaLeaves);
        this.biomeProvider = new ForgeBiomeProvider(new TerrainModel(0L, settings.seaLevel(),
                settings.volcanoRarity(), settings.mountainScale(), settings.mountainRarity()));
    }

    /** Applies new settings (e.g. after /fgen reload); models rebuild lazily. */
    public void updateSettings(@NotNull GenSettings next) {
        synchronized (modelLock) {
            this.settings = next;
            this.model = null;
        }
    }

    private @NotNull TerrainModel modelFor(@NotNull WorldInfo info) {
        TerrainModel current = model;
        long seed = info.getSeed();
        GenSettings snap = settings;
        if (current == null || current.seed() != seed) {
            synchronized (modelLock) {
                current = model;
                if (current == null || current.seed() != seed) {
                    current = new TerrainModel(seed, snap.seaLevel(), snap.volcanoRarity(),
                            snap.mountainScale(), snap.mountainRarity());
                    model = current;
                }
            }
        }
        return current;
    }

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        TerrainModel terrain = modelFor(worldInfo);
        GenSettings snap = settings;
        long seed = terrain.seed();
        int sea = terrain.seaLevel();
        int minY = chunkData.getMinHeight();
        int maxY = chunkData.getMaxHeight();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        // Height grid with a 3-block border, measured once per chunk so the
        // slope and biome pick reuse it instead of re-sampling noise.
        int[][] hg = new int[22][22];
        for (int bz = -3; bz <= 18; bz++) {
            for (int bx = -3; bx <= 18; bx++) {
                hg[bx + 3][bz + 3] = terrain.heightAt(baseX + bx, baseZ + bz);
            }
        }

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = baseX + x;
                int wz = baseZ + z;
                int h0 = hg[x + 3][z + 3];
                int h = h0;
                double volcanoDist = terrain.volcanoDistance(wx, wz);

                // Volcano crater: carve a concave bowl into the summit with a
                // basalt rim ring; lava pools flat inside the depression.
                boolean inCrater = false;
                boolean inRim = false;
                int lavaY = -1;
                if (volcanoDist >= 0.0) {
                    if (volcanoDist < CRATER_RADIUS) {
                        double t = volcanoDist / CRATER_RADIUS;
                        h -= (int) Math.round(CRATER_DEPTH * (1.0 - t * t));
                        inCrater = true;
                        long center = terrain.volcanoCenter(wx, wz);
                        int peakH = Math.min(terrain.heightAt((int) (center >> 32), (int) center),
                                maxY - 24);
                        lavaY = peakH - CRATER_DEPTH + 3;
                    } else if (volcanoDist < CRATER_RADIUS + RIM_WIDTH) {
                        h += RIM_LIFT;
                        inRim = true;
                    }
                }

                if (h < minY + 6) {
                    h = minY + 6;
                } else if (h > maxY - 24) {
                    h = maxY - 24;
                }

                int stoneStart = minY + 1;
                chunkData.setBlock(x, minY, z, Material.BEDROCK);
                long bedHash = TerrainModel.hash2(seed ^ 0xBED0C4L, wx, wz);
                if ((bedHash & 1) == 0) {
                    chunkData.setBlock(x, minY + 1, z, Material.BEDROCK);
                    stoneStart = minY + 2;
                }

                int stoneTop = Math.max(stoneStart, h - 3);
                if (stoneTop > stoneStart) {
                    chunkData.setRegion(x, stoneStart, z, x + 1, stoneTop, z + 1, Material.STONE);
                }
                if (h > stoneTop) {
                    chunkData.setRegion(x, stoneTop, z, x + 1, h, z + 1, Material.DIRT);
                }

                double slope = (Math.abs(hg[x + 6][z + 3] - h0) + Math.abs(hg[x + 3][z + 6] - h0)) / 6.0;
                ForgeBiome biome = biomeProvider.pick(worldInfo, wx, wz, h0, slope);
                TerrainModel.Region region = terrain.regionAt(wx, wz);
                int lake = terrain.lakeLevelAt(wx, wz);
                double moist = terrain.moistureAt(wx, wz);
                double temp = terrain.temperatureAt(wx, wz, h);

                Material surface = surfaceBlock(terrain, biome, region, slope, h, sea, moist,
                        temp, wx, wz, volcanoDist, lake);
                if (inCrater || inRim) {
                    surface = Material.BASALT; // crater floor, walls and rim ring
                }
                chunkData.setBlock(x, h, z, surface);

                if (h < sea) {
                    chunkData.setRegion(x, h + 1, z, x + 1, sea + 1, z + 1, Material.WATER);
                }
                // Alpine lakes: valley basins above the waterline pool up.
                if (lake > 0 && h < lake && h > sea) {
                    chunkData.setRegion(x, h + 1, z, x + 1, lake + 1, z + 1, Material.WATER);
                }

                // Lava lake pooled flat inside the crater bowl.
                if (inCrater && h < lavaY) {
                    chunkData.setRegion(x, h + 1, z, x + 1, lavaY + 1, z + 1, Material.LAVA);
                }

                // Lava lakes pooled in wasteland depressions (per-cell level,
                // so shores line up across chunk borders).
                if (region == TerrainModel.Region.BARREN_WASTELAND && h > sea) {
                    int lavaLake = terrain.wastelandPoolAt(wx, wz);
                    if (lavaLake > 0 && h < lavaLake) {
                        chunkData.setRegion(x, h + 1, z, x + 1, lavaLake + 1, z + 1, Material.LAVA);
                    }
                }

                // Geyser basin: hot-spring pools and travertine geyser cones.
                if (biome == ForgeBiome.GEYSER_BASIN && h > sea + 1 && h + 4 < maxY
                        && x >= 1 && x <= 14 && z >= 1 && z <= 14) {
                    double pool = terrain.columnRandom(wx, wz, 0x6E75E2L);
                    if (pool < 0.06) {
                        // 1-deep pool wherever the neighbours sit level.
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (hg[x + 3 + dx][z + 3 + dz] == h0) {
                                    chunkData.setBlock(x + dx, h - 1, z + dz, Material.WATER);
                                    chunkData.setBlock(x + dx, h, z + dz, Material.AIR);
                                }
                            }
                        }
                    } else if (pool < 0.075) {
                        // Geyser cone with a water cap.
                        for (int i = 1; i <= 3; i++) {
                            chunkData.setBlock(x, h + i, z, Material.CALCITE);
                        }
                        chunkData.setBlock(x, h + 4, z, Material.WATER);
                    }
                }

                // Granite valley waterfalls: water chutes cut into sheer faces.
                if (region == TerrainModel.Region.NONE
                        && terrain.graniteMaskAt(wx, wz) > 0.45 && slope > 1.15 && h > sea + 10
                        && (TerrainModel.hash2(seed ^ 0x9A7E2FAL, wx, wz) & 15) == 0) {
                    for (int i = 0; i <= 8 && h - i > minY + 1; i++) {
                        chunkData.setBlock(x, h - i, z, Material.WATER);
                    }
                }

                // Trees stay 2 blocks inside the border so they never cross chunks.
                if (x >= 2 && x <= 13 && z >= 2 && z <= 13 && h >= sea && volcanoDist < 0.0) {
                    trees.tryPlace(terrain, chunkData, x, h + 1, z, wx, wz, biome,
                            snap.treeDensity(), sea);
                }
            }
        }
    }

    /**
     * Slope-driven surfacing (Growth reference terrain): steep faces expose
     * rock, gentle slopes grow grass or hold snow; the snow line follows
     * slope AND altitude. Special territories paint their own palettes.
     */
    private static @NotNull Material surfaceBlock(TerrainModel terrain, ForgeBiome biome,
            TerrainModel.Region region, double slope, int h, int sea, double moist, double temp,
            int wx, int wz, double volcanoDist, int lake) {
        long seed = terrain.seed();
        if (volcanoDist >= 0.0 && volcanoDist < 110.0) {
            if (volcanoDist < 20.0) {
                return Material.MAGMA_BLOCK;
            }
            return (TerrainModel.hash2(seed ^ 0xBA5A17L, wx, wz) & 3) == 0
                    ? Material.BLACKSTONE : Material.BASALT;
        }
        // Alpine lake shores: rocky, snowy when high.
        if (lake > 0 && h <= lake + 1 && h >= lake - 2) {
            return h > sea + 40 ? Material.SNOW_BLOCK : Material.GRAVEL;
        }
        if (h < sea - 2) {
            // Seabed: sand near shore, gravel in the deep.
            return h > sea - 7 ? Material.SAND : Material.GRAVEL;
        }
        if (h <= sea + 1) {
            return Material.SAND; // beach band
        }
        // Snow line follows slope and altitude: steep faces shed snow.
        int snowY = sea + 78 - (int) (slope * 55.0);
        if (h > snowY && slope < 1.05) {
            return Material.SNOW_BLOCK;
        }
        if (temp < 0.30 && h > sea + 34 && slope < 0.9) {
            return Material.SNOW_BLOCK;
        }
        // Sheer cliffs: exposed rock.
        if (slope > 1.05) {
            return cliffRock(terrain, region, wx, wz, h);
        }
        // Special territories paint their own palettes.
        if (region == TerrainModel.Region.PAINTED_CANYON) {
            return paintedStrata(terrain, wx, wz, h);
        }
        if (region == TerrainModel.Region.BARREN_WASTELAND) {
            return wasteMottle(terrain, wx, wz);
        }
        if (region == TerrainModel.Region.DUNE_SEA) {
            return slope > 0.5 ? Material.SANDSTONE : Material.SAND; // slip faces
        }
        if (region == TerrainModel.Region.GEYSER_BASIN) {
            Material mat = geyserMat(terrain, wx, wz);
            if (mat != null) {
                return mat; // colourful mineral mats ringing the hot springs
            }
        }
        double gm = terrain.graniteMaskAt(wx, wz);
        if (gm > 0.45 && slope > 0.7) {
            return graniteStrata(terrain, wx, wz, h); // pale Yosemite walls
        }
        // Grass grows on gentle AND moderate slopes now (v1.2: more grass).
        if (slope <= 0.85) {
            if (biome.surface() == Material.GRASS_BLOCK) {
                // Grass mosaic: dirt patches break up the carpet, plus the
                // mottled speckle from the Growth refs.
                long patch = TerrainModel.hash2(seed ^ 0x9A7C4L, wx >> 2, wz >> 2);
                if ((patch & 7) == 0) {
                    return Material.COARSE_DIRT;
                }
                if ((patch & 3) == 0 && moist < 0.45) {
                    return Material.DIRT;
                }
                long speck = TerrainModel.hash2(seed ^ 0x5DEC41L, wx, wz);
                if ((speck & 31) == 0) {
                    return Material.GRAVEL;
                }
                if ((speck & 63) == 0) {
                    return Material.DIRT;
                }
                return Material.GRASS_BLOCK;
            }
            long speck = TerrainModel.hash2(seed ^ 0x5DEC41L, wx, wz);
            if (biome.surface() == Material.SAND && (speck & 7) == 0) {
                return Material.SANDSTONE;
            }
            return biome.surface();
        }
        // Scree transition: loose rock at the foot of cliffs.
        long scree = TerrainModel.hash2(seed ^ 0x5CEEE1L, wx, wz);
        if ((scree & 3) == 0) {
            return Material.GRAVEL;
        }
        if ((scree & 11) == 0) {
            return Material.COBBLESTONE;
        }
        // Pale erosion streaks running down steep faces (Growth ref).
        if ((TerrainModel.hash2(seed ^ 0xE20510L, wx >> 1, wz >> 3) & 7) == 0) {
            return Material.CALCITE;
        }
        return strataRock(terrain, wx, wz, h);
    }

    /** Rock for sheer cliff faces: painted strata in canyon country. */
    private static @NotNull Material cliffRock(TerrainModel terrain, TerrainModel.Region region,
                                               int wx, int wz, int h) {
        if (region == TerrainModel.Region.PAINTED_CANYON) {
            return paintedStrata(terrain, wx, wz, h);
        }
        return strataRock(terrain, wx, wz, h);
    }

    /**
     * Sedimentary strata for cliffs and steep faces: horizontal bands warped
     * by low-frequency noise so they undulate naturally, alternating light
     * and dark rock with an occasional tuff seam (Growth reference terrain).
     * Cheap: one 2-octave noise sample per steep column.
     */
    private static @NotNull Material strataRock(TerrainModel terrain, int wx, int wz, int h) {
        double warp = terrain.strataWarp(wx, wz);
        long band = Math.floorDiv((int) Math.floor(h + warp), 4);
        if (Math.floorMod(band, 10) == 0) {
            return Material.TUFF;
        }
        return Math.floorMod(band, 2) == 0 ? Material.STONE : Material.ANDESITE;
    }

    /** Painted canyon strata: banded red, cream and ochre (Grand Canyon). */
    private static @NotNull Material paintedStrata(TerrainModel terrain, int wx, int wz, int h) {
        double warp = terrain.strataWarp(wx, wz);
        long band = Math.floorDiv((int) Math.floor(h + warp), 5);
        return switch ((int) Math.floorMod(band, 4)) {
            case 0 -> Material.RED_SANDSTONE;
            case 1 -> Material.SANDSTONE;
            case 2 -> Material.TERRACOTTA;
            default -> Material.YELLOW_TERRACOTTA;
        };
    }

    /** Pale granite wall banding (Yosemite): diorite, granite, calcite. */
    private static @NotNull Material graniteStrata(TerrainModel terrain, int wx, int wz, int h) {
        double warp = terrain.strataWarp(wx, wz);
        long band = Math.floorDiv((int) Math.floor(h + warp), 6);
        return switch ((int) Math.floorMod(band, 3)) {
            case 0 -> Material.DIORITE;
            case 1 -> Material.GRANITE;
            default -> Material.CALCITE;
        };
    }

    /** Red-gray mottled badlands rock (Growth ref 3: volcanic waste). */
    private static @NotNull Material wasteMottle(TerrainModel terrain, int wx, int wz) {
        long m = (TerrainModel.hash2(terrain.seed() ^ 0xAA571EL, wx, wz) >>> 8) & 15;
        if (m < 7) {
            return Material.BASALT;
        }
        if (m < 10) {
            return Material.BLACKSTONE;
        }
        if (m < 12) {
            return Material.NETHERRACK;
        }
        if (m < 14) {
            return Material.RED_SAND;
        }
        return Material.GRAVEL;
    }

    /**
     * Colourful mineral mats ringing geyser-basin hot springs, or null away
     * from pools. Pure function of world coordinates — the ring is painted by
     * each column independently, so chunk borders always agree.
     */
    private static @Nullable Material geyserMat(TerrainModel terrain, int wx, int wz) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (terrain.columnRandom(wx + dx, wz + dz, 0x6E75E2L) < 0.06) {
                    long mh = TerrainModel.hash2(terrain.seed() ^ 0x9A71CAL, wx, wz);
                    return switch ((int) ((mh >>> 16) & 3)) {
                        case 0 -> Material.CYAN_TERRACOTTA;
                        case 1 -> Material.WHITE_TERRACOTTA;
                        case 2 -> Material.YELLOW_TERRACOTTA;
                        default -> Material.ORANGE_TERRACOTTA;
                    };
                }
            }
        }
        return null;
    }

    private static double slopeAt(TerrainModel terrain, int x, int z, int h) {
        int hx = terrain.heightAt(x + 3, z);
        int hz = terrain.heightAt(x, z + 3);
        return (Math.abs(hx - h) + Math.abs(hz - h)) / 6.0;
    }

    @Override
    public @NotNull BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        modelFor(worldInfo);
        return biomeProvider;
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return true;
    }

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        // Find a pleasant low-slope valley spot near the origin for first join.
        TerrainModel terrain = modelFor(world);
        int sea = terrain.seaLevel();
        for (int ring = 0; ring < 12; ring++) {
            for (int dx = -ring * 8; dx <= ring * 8; dx += 8) {
                for (int dz = -ring * 8; dz <= ring * 8; dz += 8) {
                    int x = 8 + dx;
                    int z = 8 + dz;
                    int h = terrain.heightAt(x, z);
                    if (h > sea + 1 && h < sea + 40
                            && slopeAt(terrain, x, z, h) < 0.5
                            && terrain.volcanoDistance(x, z) < 0.0) {
                        return new Location(world, x + 0.5, h + 1.0, z + 0.5);
                    }
                }
            }
        }
        return null; // fall back to vanilla spawn search
    }

    // NOTE: ChunkGenerator.isParallelCapable() and shouldGenerateBedrock()
    // are deprecated in Paper 26.3 with no replacement. We do not override
    // them: bedrock is placed inside generateNoise's single pass, and the
    // generator is stateless/thread-safe regardless of the old parallel hint.
    //
    // We paint the surface inside generateNoise's single pass, so the separate
    // surface step is disabled. Caves/decorations/structures/mobs run through
    // the vanilla pipeline on top of our terrain (toggled by config).
    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return settings.caves();
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return settings.decorations();
    }

    @Override
    public boolean shouldGenerateStructures() {
        return settings.structures();
    }

    @Override
    public boolean shouldGenerateMobs() {
        return settings.mobs();
    }
}
