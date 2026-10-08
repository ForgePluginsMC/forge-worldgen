package com.forgeplugins.worldgen.stage;

import com.forgeplugins.worldgen.biome.ForgeBiome;
import com.forgeplugins.worldgen.pipeline.ChunkContext;
import com.forgeplugins.worldgen.pipeline.GenStage;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Surface stage — runs in {@code generateSurface}, after vanilla's surface
 * step (API contract), and repaints the top layers from each biome's
 * palette.
 *
 * <p>Two things keep it natural: biome borders are <em>dithered</em> (no
 * hard transitions — nearby columns blend over ~14 blocks), and every biome
 * paints warm/cold patch variants from its palette instead of flat color.
 */
public final class SurfaceStage implements GenStage {

    @Override
    public @NotNull String name() {
        return "surface";
    }

    @Override
    public void generate(@NotNull ChunkContext ctx) {
        int minY = ctx.minY();
        int maxY = ctx.maxY();
        int baseX = ctx.chunkX << 4;
        int baseZ = ctx.chunkZ << 4;

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = baseX + x;
                int wz = baseZ + z;
                int h = Math.clamp(ctx.terrain.heightAt(wx, wz), minY + 1, maxY - 1);
                ForgeBiome biome = blendedBiome(ctx, wx, wz);

                Material top = biome.surface();
                double var = ctx.terrain.surfaceVariationAt(wx, wz);
                if (var > 0.55) {
                    top = biome.patchWarm();
                } else if (var < -0.55) {
                    top = biome.patchCold();
                }
                // Gravel beaches: coarser patches of gravel among the sand.
                if (biome == ForgeBiome.BEACH && var > 0.25 && var <= 0.55) {
                    top = Material.GRAVEL;
                }

                ctx.data.setBlock(x, h, z, top);
                int depth = biome.subDepth();
                for (int i = 1; i <= depth && h - i > minY; i++) {
                    ctx.data.setBlock(x, h - i, z, biome.subsurface());
                }

                if (biome == ForgeBiome.SNOWY && h + 1 < maxY) {
                    ctx.data.setBlock(x, h + 1, z, Material.SNOW);
                }
            }
        }
    }

    /**
     * Dithered biome blend. Samples the column and four neighbors 7 blocks
     * out; where they agree the biome is pure, where they differ the winner
     * is picked per 4x4 cell by hash — a noisy natural transition instead
     * of a hard border.
     */
    private @NotNull ForgeBiome blendedBiome(@NotNull ChunkContext ctx, int x, int z) {
        ForgeBiome center = ctx.biomes.biomeAt(x, z);
        ForgeBiome a = ctx.biomes.biomeAt(x + 7, z);
        ForgeBiome b = ctx.biomes.biomeAt(x - 7, z);
        ForgeBiome c = ctx.biomes.biomeAt(x, z + 7);
        ForgeBiome d = ctx.biomes.biomeAt(x, z - 7);
        if (a == center && b == center && c == center && d == center) {
            return center;
        }
        ForgeBiome[] distinct = new ForgeBiome[5];
        int n = 0;
        for (ForgeBiome candidate : new ForgeBiome[] {center, a, b, c, d}) {
            boolean seen = false;
            for (int i = 0; i < n; i++) {
                if (distinct[i] == candidate) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                distinct[n++] = candidate;
            }
        }
        long hash = hash2(ctx.seed, x >> 2, z >> 2);
        double unit = (hash >>> 11) * 0x1p-53;
        return distinct[(int) (unit * n)];
    }

    /** Deterministic 64-bit mix of seed and column coordinates. */
    private static long hash2(long seed, int x, int z) {
        long h = seed ^ (x * 0x9E3779B1L) ^ (z * 0x85EBCA6BL);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h;
    }
}
