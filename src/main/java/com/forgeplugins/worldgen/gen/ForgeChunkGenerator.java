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
 * surface mosaic and decoration, water, mountain ponds, volcano craters,
 * lava pools, geysers, sulfur ponds, waterfalls, wheat fields and trees —
 * happens in {@link #generateNoise}, a single sweep over the chunk's 256
 * columns, so no work is ever repeated. Noise fields are allocation-free
 * and the instance is safe for parallel chunk generation.
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
    private final FarmKit farms;

    public ForgeChunkGenerator(@NotNull GenSettings settings,
                               @Nullable BlockData oakLeaves,
                               @Nullable BlockData spruceLeaves,
                               @Nullable BlockData acaciaLeaves,
                               @Nullable BlockData jungleLeaves,
                               @NotNull FarmKit farms) {
        this.settings = settings;
        this.trees = new TreePlacer(oakLeaves, spruceLeaves, acaciaLeaves, jungleLeaves);
        this.farms = farms;
        this.biomeProvider = new ForgeBiomeProvider(new TerrainModel(0L, settings.seaLevel(),
                settings.snowMinElevation(), settings.volcanoRarity(), settings.mountainScale(),
                settings.mountainRarity()));
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
                    current = new TerrainModel(seed, snap.seaLevel(), snap.snowMinElevation(),
                            snap.volcanoRarity(), snap.mountainScale(), snap.mountainRarity());
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

                // Mountain ponds: small bowls (5-15 blocks across) pooling
                // flat, never the old lake floods. The bowl is carved here so
                // the stone fill below follows it; water depth stays <= 4.
                TerrainModel.@Nullable Pond pond = terrain.pondAt(wx, wz);
                double pondDist = -1.0;
                if (pond != null) {
                    pondDist = Math.hypot(wx - pond.cx, wz - pond.cz);
                    if (pondDist <= pond.r + 1) {
                        int depth = 1 + (int) (2.0 * (1.0 - pondDist / (pond.r + 1)));
                        int target = pond.level - depth;
                        if (h > target) {
                            h = target;
                        }
                        if (h < pond.level - 4) {
                            h = pond.level - 4;
                        }
                    }
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
                // Fine slope over 2 blocks: grass colonizes by local flatness,
                // not just the coarse 6-block slope.
                double slopeFine = (Math.abs(hg[x + 4][z + 3] - h0)
                        + Math.abs(hg[x + 3][z + 4] - h0)) / 2.0;
                ForgeBiome biome = biomeProvider.pick(worldInfo, wx, wz, h0, slope);
                TerrainModel.Region region = terrain.regionAt(wx, wz);
                double moist = terrain.moistureAt(wx, wz);
                double temp = terrain.temperatureAt(wx, wz, h);

                Material surface = surfaceBlock(terrain, biome, region, slope, slopeFine, h, sea,
                        moist, temp, wx, wz, volcanoDist);
                if (inCrater || inRim) {
                    surface = Material.BASALT; // crater floor, walls and rim ring
                }
                chunkData.setBlock(x, h, z, surface);

                if (h < sea) {
                    chunkData.setRegion(x, h + 1, z, x + 1, sea + 1, z + 1, Material.WATER);
                }
                // Pond water: pooled flat at the pond level, depth <= 4.
                if (pond != null && pondDist >= 0.0 && pondDist <= pond.r + 1
                        && h < pond.level && h > sea && h + 1 < pond.level + 1) {
                    chunkData.setRegion(x, h + 1, z, x + 1, pond.level + 1, z + 1, Material.WATER);
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

                // Sulfur ponds: small acidic pools staining volcanic rock
                // yellow, plus the geyser basin. Yellow rims, shallow hearts.
                // Same border-safe pattern as the geyser pools.
                if ((biome == ForgeBiome.ASHEN_CALDERA || biome == ForgeBiome.BRIMSTONE_FLATS
                            || biome == ForgeBiome.GEYSER_BASIN)
                        && h > sea + 1 && h + 4 < maxY && x >= 1 && x <= 14 && z >= 1 && z <= 14) {
                    double sroll = terrain.columnRandom(wx, wz, 0x51F02L);
                    if (sroll < 0.022) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                if (hg[x + 3 + dx][z + 3 + dz] != h0) {
                                    continue;
                                }
                                if (dx == 0 && dz == 0) {
                                    chunkData.setBlock(x, h - 1, z, Material.WATER);
                                    chunkData.setBlock(x, h, z, Material.AIR);
                                } else {
                                    long sh = TerrainModel.hash2(seed ^ 0x51F03L, wx + dx, wz + dz);
                                    chunkData.setBlock(x + dx, h, z + dz,
                                            (sh & 1) == 0 ? Material.YELLOW_TERRACOTTA
                                                    : Material.SANDSTONE);
                                }
                            }
                        }
                    }
                }

                // Waterfalls: thin segmented chutes at genuine cliff lips —
                // never curtains. Only where the ground truly falls away
                // beside the column, so the water reads as falling instead
                // of a blue wall embedded in rock.
                if (region == TerrainModel.Region.NONE
                        && terrain.graniteMaskAt(wx, wz) > 0.45 && slope > 1.15 && h > sea + 10
                        && (TerrainModel.hash2(seed ^ 0x9A7E2FL, wx, wz) & 63) == 0) {
                    int lip = h0 - Math.min(Math.min(hg[x + 2][z + 3], hg[x + 4][z + 3]),
                            Math.min(hg[x + 3][z + 2], hg[x + 3][z + 4]));
                    if (lip >= 7) {
                        for (int i = 0; i <= 6 && h - i > minY + 1; i++) {
                            // Broken segments: dry gaps where the hash says so.
                            if ((TerrainModel.hash2(seed ^ 0xFA11L, wx * 31 + i, wz * 17 - i) & 3)
                                    != 0) {
                                chunkData.setBlock(x, h - i, z, Material.WATER);
                            }
                        }
                    }
                }

                // Wheat fields / crop lands: neat farmed rectangles in the
                // plains. Border-agnostic: every column derives the same
                // field from world coordinates, so fields cross chunk
                // borders seamlessly.
                TerrainModel.@Nullable Field field = null;
                boolean inField = false;
                if ((biome == ForgeBiome.ROLLING_PLAINS || biome == ForgeBiome.VERDANT_VALE
                            || biome == ForgeBiome.GOLDEN_SAVANNA
                            || biome == ForgeBiome.HIGH_PLAINS)
                        && h > sea + 1 && h + 2 < maxY) {
                    field = terrain.fieldAt(wx, wz);
                    inField = field != null;
                }
                if (inField) {
                    assert field != null;
                    int lx = wx - field.cx;
                    int lz = wz - field.cz;
                    boolean ditch = ((lz + field.hd) % 5 == 4);
                    if (ditch) {
                        // Shallow irrigation channel; keeps the farmland wet.
                        chunkData.setBlock(x, h, z, Material.WATER);
                    } else {
                        chunkData.setBlock(x, h, z, farms.farmland());
                        long ch2 = TerrainModel.hash2(seed ^ 0xC2075L, wx, wz);
                        BlockData crop = pickCrop(farms, field.crop, (ch2 & 7) < 6);
                        if (h + 1 < maxY) {
                            chunkData.setBlock(x, h + 1, z, crop);
                        }
                    }
                    // Fence posts on the four corners, hay bale inside.
                    if (!ditch && Math.abs(lx) == field.hw && Math.abs(lz) == field.hd
                            && h + 1 < maxY) {
                        chunkData.setBlock(x, h + 1, z, Material.OAK_FENCE);
                    }
                    if (!ditch) {
                        long fhh = TerrainModel.hash2(seed ^ 0xBA75L, field.cx, field.cz);
                        if ((fhh & 3) == 0) {
                            int bx = field.cx + (int) ((fhh >>> 8) % (2 * field.hw + 1)) - field.hw;
                            int bz = field.cz + (int) ((fhh >>> 16) % (2 * field.hd + 1)) - field.hd;
                            if (wx == bx && wz == bz && h + 1 < maxY) {
                                chunkData.setBlock(x, h + 1, z, Material.HAY_BLOCK);
                            }
                        }
                    }
                }

                // Pond shores: a few wildflowers on the grass ring.
                if (pond != null && pondDist > pond.r + 1 && pondDist <= pond.r + 4
                        && surface == Material.GRASS_BLOCK && h + 1 < maxY) {
                    long fh = TerrainModel.hash2(seed ^ 0xF10E2L, wx, wz);
                    if ((fh & 7) == 0) {
                        Material[] flowers = {Material.POPPY, Material.DANDELION,
                                Material.CORNFLOWER, Material.ALLIUM, Material.OXEYE_DAISY,
                                Material.LILY_OF_THE_VALLEY};
                        chunkData.setBlock(x, h + 1, z, flowers[(int) ((fh >>> 16) % 6)]);
                    }
                }

                // Trees stay 2 blocks inside the border so they never cross chunks.
                // Lava country stays barren: no trees, flowers or grass where
                // basalt and lava rule. Fields grow crops, not trees.
                boolean barren = biome == ForgeBiome.ASHEN_CALDERA
                        || biome == ForgeBiome.BRIMSTONE_FLATS
                        || region == TerrainModel.Region.BARREN_WASTELAND
                        || region == TerrainModel.Region.SULFUR_FLATS;
                if (x >= 2 && x <= 13 && z >= 2 && z <= 13 && h >= sea && volcanoDist < 0.0
                        && !barren && !inField) {
                    double dens = snap.treeDensity();
                    if (pond != null && pondDist <= pond.r + 5) {
                        dens *= 1.6; // a few trees gather at the pond shore
                    }
                    trees.tryPlace(terrain, chunkData, x, h + 1, z, wx, wz, biome, dens, sea);
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
            TerrainModel.Region region, double slope, double slopeFine, int h, int sea,
            double moist, double temp, int wx, int wz, double volcanoDist) {
        long seed = terrain.seed();
        if (volcanoDist >= 0.0 && volcanoDist < 110.0) {
            if (volcanoDist < 20.0) {
                return Material.MAGMA_BLOCK;
            }
            return (TerrainModel.hash2(seed ^ 0xBA5A17L, wx, wz) & 3) == 0
                    ? Material.BLACKSTONE : Material.BASALT;
        }
        if (h < sea - 2) {
            // Seabed: sand near shore, gravel in the deep.
            return h > sea - 7 ? Material.SAND : Material.GRAVEL;
        }
        if (h <= sea + 1) {
            return Material.SAND; // beach band
        }
        // Snow line follows slope and altitude, but only above a hard
        // elevation floor: alpine and lowland stay green/rocky below it.
        int snowMin = terrain.snowMinElevation();
        int snowY = sea + 78 - (int) (slope * 55.0);
        if (h > snowY && slope < 1.05 && h >= snowMin) {
            return Material.SNOW_BLOCK;
        }
        if (temp < 0.30 && h > sea + 34 && slope < 0.9 && h >= snowMin) {
            return Material.SNOW_BLOCK;
        }
        // Bare rock stays on true cliffs and the steepest faces only.
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
        if (biome == ForgeBiome.BRIMSTONE_FLATS) {
            return sulfurCrust(terrain, wx, wz); // brimstone-stained flats
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
        // Grass colonizes by local flatness and pocket noise, not one global
        // cutoff: hills read as grassy with rocky outcrops, only true cliffs
        // and the steepest faces stay bare rock.
        double grassLine = 0.55 + terrain.pocketAt(wx, wz) * 0.80;
        double flat = Math.min(slope, slopeFine);
        if (flat <= grassLine) {
            if (biome == ForgeBiome.AMBERWOOD) {
                return amberwoodFloor(terrain, wx, wz); // fallen-leaf mosaic
            }
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

    /** Yellow brimstone-stained rock for the sulfur flats. */
    private static @NotNull Material sulfurCrust(TerrainModel terrain, int wx, int wz) {
        long m = (TerrainModel.hash2(terrain.seed() ^ 0x5B17C2L, wx, wz) >>> 8) & 15;
        if (m < 6) {
            return Material.YELLOW_TERRACOTTA;
        }
        if (m < 9) {
            return Material.SANDSTONE;
        }
        if (m < 11) {
            return Material.BASALT;
        }
        if (m < 13) {
            return Material.ORANGE_TERRACOTTA;
        }
        return Material.GRAVEL;
    }

    /** Amberwood floor: fallen-leaf mosaic of orange, yellow and leaf litter. */
    private static @NotNull Material amberwoodFloor(TerrainModel terrain, int wx, int wz) {
        long patch = TerrainModel.hash2(terrain.seed() ^ 0xA97BE2L, wx >> 2, wz >> 2);
        return switch ((int) ((patch >>> 8) & 7)) {
            case 3, 4 -> Material.ORANGE_TERRACOTTA; // fallen leaves
            case 5 -> Material.YELLOW_TERRACOTTA;
            case 6 -> Material.COARSE_DIRT;
            case 7 -> Material.DIRT;
            default -> Material.GRASS_BLOCK;
        };
    }

    /** Picks the mature or young crop template for a wheat-field column. */
    private static @NotNull BlockData pickCrop(@NotNull FarmKit farms, int crop, boolean mature) {
        return switch (crop) {
            case 1 -> mature ? farms.carrotOld() : farms.carrotYoung();
            case 2 -> mature ? farms.potatoOld() : farms.potatoYoung();
            case 3 -> mature ? farms.beetOld() : farms.beetYoung();
            default -> mature ? farms.wheatOld() : farms.wheatYoung();
        };
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
