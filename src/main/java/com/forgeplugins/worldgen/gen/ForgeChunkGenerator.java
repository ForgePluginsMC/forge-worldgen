package com.forgeplugins.worldgen.gen;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ForgeWorldGen v2 generator: our own heightfield, vanilla everything else.
 *
 * <p>The single change v2 makes over a normal world: terrain heights come
 * from {@link TerrainModel} (vanilla-proportioned, gently smoothed) instead
 * of Mojang's noise router. Biomes, surface painting, caves, decorations,
 * structures and mobs are 100% vanilla — we deliberately do not override
 * {@code getDefaultBiomeProvider}, so vanilla's biome source applies, and the
 * vanilla surface step paints dirt/grass/sand on top of our stone.
 *
 * <p>The instance is stateless apart from the lazily-built, immutable
 * {@link TerrainModel}, so it is safe for parallel chunk generation.
 */
public final class ForgeChunkGenerator extends ChunkGenerator {

    private final Object modelLock = new Object();
    private volatile TerrainModel model;
    private volatile GenSettings settings;

    public ForgeChunkGenerator(@NotNull GenSettings settings) {
        this.settings = settings;
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
                    current = new TerrainModel(seed, snap.seaLevel(), snap.smoothing());
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
        long seed = terrain.seed();
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
                } else if (h > maxY - 1) {
                    h = maxY - 1;
                }

                // Bedrock floor with a ragged second layer.
                chunkData.setBlock(x, minY, z, Material.BEDROCK);
                int stoneStart = minY + 1;
                if ((hash2(seed, wx, wz) & 1) == 0) {
                    chunkData.setBlock(x, minY + 1, z, Material.BEDROCK);
                    stoneStart = minY + 2;
                }

                // Solid stone up to the surface. The vanilla surface step
                // (left enabled) paints dirt/grass/sand per vanilla biome.
                if (h >= stoneStart) {
                    chunkData.setRegion(x, stoneStart, z, x + 1, h + 1, z + 1, Material.STONE);
                }

                // Ocean fill.
                if (h < sea) {
                    chunkData.setRegion(x, h + 1, z, x + 1, sea + 1, z + 1, Material.WATER);
                }
            }
        }
    }

    /** Deterministic 64-bit mix of seed and column coordinates. */
    private static long hash2(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B1L) ^ (z * 0x85EBCA6BL);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return true;
    }

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        // Find pleasant low land near the origin for first join.
        TerrainModel terrain = modelFor(world);
        int sea = terrain.seaLevel();
        for (int ring = 0; ring < 12; ring++) {
            for (int dx = -ring * 8; dx <= ring * 8; dx += 8) {
                for (int dz = -ring * 8; dz <= ring * 8; dz += 8) {
                    int x = 8 + dx;
                    int z = 8 + dz;
                    int h = terrain.heightAt(x, z);
                    if (h > sea + 1 && h < sea + 40) {
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
    // We also do not override getDefaultBiomeProvider (vanilla biomes apply)
    // and we leave shouldGenerateSurface() at its default true, so the
    // vanilla surface step paints our stone. Caves, decorations, structures
    // and mobs run through the vanilla pipeline, toggled by config.
    @Override
    public boolean shouldGenerateNoise() {
        return true;
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
