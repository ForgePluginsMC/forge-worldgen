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
 * surface mosaic, water, volcano craters and trees — happens in
 * {@link #generateNoise}, a single sweep over the chunk's 256 columns, so no
 * work is ever repeated. Noise fields are allocation-free and the instance is
 * safe for parallel chunk generation.
 */
public final class ForgeChunkGenerator extends ChunkGenerator {

    private final Object modelLock = new Object();
    private volatile TerrainModel model;
    private volatile GenSettings settings;

    private final TreePlacer trees;
    private final ForgeBiomeProvider biomeProvider;

    public ForgeChunkGenerator(@NotNull GenSettings settings,
                               @Nullable BlockData oakLeaves,
                               @Nullable BlockData spruceLeaves,
                               @Nullable BlockData acaciaLeaves) {
        this.settings = settings;
        this.trees = new TreePlacer(oakLeaves, spruceLeaves, acaciaLeaves);
        this.biomeProvider = new ForgeBiomeProvider(new TerrainModel(0L, settings.seaLevel(),
                settings.volcanoRarity(), settings.mountainScale()));
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
                    current = new TerrainModel(seed, snap.seaLevel(), snap.volcanoRarity(), snap.mountainScale());
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
        int sea = terrain.seaLevel();
        int minY = chunkData.getMinHeight();
        int maxY = chunkData.getMaxHeight();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = baseX + x;
                int wz = baseZ + z;
                int h = terrain.heightAt(wx, wz);
                if (h < minY + 6) {
                    h = minY + 6;
                } else if (h > maxY - 24) {
                    h = maxY - 24;
                }

                int stoneStart = minY + 1;
                chunkData.setBlock(x, minY, z, Material.BEDROCK);
                long bedHash = TerrainModel.hash2(worldInfo.getSeed() ^ 0xBED0C4L, wx, wz);
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

                ForgeBiome biome = biomeProvider.pick(worldInfo, wx, wz);
                double slope = slopeAt(terrain, wx, wz, h);
                double moist = terrain.moistureAt(wx, wz);
                double volcanoDist = terrain.volcanoDistance(wx, wz);

                Material surface = surfaceBlock(terrain, biome, slope, h, sea, moist, wx, wz, volcanoDist);
                chunkData.setBlock(x, h, z, surface);

                if (h < sea) {
                    chunkData.setRegion(x, h + 1, z, x + 1, sea + 1, z + 1, Material.WATER);
                }

                // Volcano crater: lava pool with a magma rim.
                if (volcanoDist >= 0.0 && volcanoDist < 9.0) {
                    chunkData.setBlock(x, h, z, Material.LAVA);
                    chunkData.setBlock(x, h - 1, z, Material.BASALT);
                }

                // Trees stay 2 blocks inside the border so they never cross chunks.
                if (x >= 2 && x <= 13 && z >= 2 && z <= 13 && h > sea && volcanoDist < 0.0) {
                    trees.tryPlace(terrain, chunkData, x, h + 1, z, wx, wz, biome, snap.treeDensity());
                }
            }
        }
    }

    private static double slopeAt(TerrainModel terrain, int x, int z, int h) {
        int hx = terrain.heightAt(x + 3, z);
        int hz = terrain.heightAt(x, z + 3);
        return (Math.abs(hx - h) + Math.abs(hz - h)) / 6.0;
    }

    private static @NotNull Material surfaceBlock(TerrainModel terrain, ForgeBiome biome,
                                                  double slope, int h, int sea, double moist,
                                                  int wx, int wz, double volcanoDist) {
        if (volcanoDist >= 0.0 && volcanoDist < 110.0) {
            if (volcanoDist < 20.0) {
                return Material.MAGMA_BLOCK;
            }
            return (TerrainModel.hash2(terrain.seed() ^ 0xBA5A17L, wx, wz) & 3) == 0
                    ? Material.BLACKSTONE : Material.BASALT;
        }
        if (h < sea - 2) {
            // Seabed: sand near shore, gravel in the deep.
            return h > sea - 7 ? Material.SAND : Material.GRAVEL;
        }
        if (h <= sea + 1) {
            return Material.SAND; // beach band
        }
        if (slope > 0.95) {
            return Material.STONE;
        }
        if (slope > 0.62) {
            return ((TerrainModel.hash2(terrain.seed() ^ 0x9A9E1L, wx, wz) & 1) == 0)
                    ? Material.GRAVEL : Material.STONE;
        }
        switch (biome) {
            case FROSTCAP_PEAKS -> {
                return Material.SNOW_BLOCK;
            }
            case SUNBAKED_DUNES -> {
                return ((TerrainModel.hash2(terrain.seed() ^ 0xD00E5L, wx, wz) & 3) == 0)
                        ? Material.SANDSTONE : Material.SAND;
            }
            case ASHEN_CALDERA -> {
                return Material.BASALT;
            }
            default -> {
                // Grass mosaic: patches of dirt/coarse dirt break up the carpet.
                long patch = TerrainModel.hash2(terrain.seed() ^ 0x9A7C4L, wx >> 2, wz >> 2);
                if ((patch & 7) == 0) {
                    return Material.COARSE_DIRT;
                }
                if ((patch & 3) == 0 && moist < 0.45) {
                    return Material.DIRT;
                }
                return biome.surface();
            }
        }
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
