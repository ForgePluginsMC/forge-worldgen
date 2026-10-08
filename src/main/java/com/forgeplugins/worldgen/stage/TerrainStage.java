package com.forgeplugins.worldgen.stage;

import com.forgeplugins.worldgen.pipeline.ChunkContext;
import com.forgeplugins.worldgen.pipeline.GenStage;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * First noise stage: bedrock floor, rock body (basalt inside volcano cones),
 * ocean fill, and crater lava lakes. Everything placed here is a pure
 * function of coordinates and seed.
 */
public final class TerrainStage implements GenStage {

    @Override
    public @NotNull String name() {
        return "terrain";
    }

    @Override
    public void generate(@NotNull ChunkContext ctx) {
        int minY = ctx.minY();
        int maxY = ctx.maxY();
        int sea = ctx.terrain.seaLevel();
        long seed = ctx.seed;
        int baseX = ctx.chunkX << 4;
        int baseZ = ctx.chunkZ << 4;

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = baseX + x;
                int wz = baseZ + z;
                int h = Math.clamp(ctx.terrain.heightAt(wx, wz), minY + 6, maxY - 1);

                // Bedrock floor with a ragged second layer.
                ctx.data.setBlock(x, minY, z, Material.BEDROCK);
                int rockStart = minY + 1;
                if ((hash2(seed, wx, wz) & 1) == 0) {
                    ctx.data.setBlock(x, minY + 1, z, Material.BEDROCK);
                    rockStart = minY + 2;
                }

                // Rock body: basalt inside the volcano cones, stone elsewhere.
                Material rock = ctx.terrain.volcanoFactorAt(wx, wz) > 0.4
                        ? Material.BASALT : Material.STONE;
                if (h >= rockStart) {
                    ctx.data.setRegion(x, rockStart, z, x + 1, h + 1, z + 1, rock);
                }

                // Crater lava lakes, else ocean fill.
                int lava = ctx.terrain.lavaLevelAt(wx, wz);
                if (lava > h && lava < maxY) {
                    ctx.data.setRegion(x, h + 1, z, x + 1, lava + 1, z + 1, Material.LAVA);
                } else if (h < sea) {
                    ctx.data.setRegion(x, h + 1, z, x + 1, sea + 1, z + 1, Material.WATER);
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
}
