package com.forgeplugins.worldgen.stage;

import com.forgeplugins.worldgen.biome.ForgeBiome;
import com.forgeplugins.worldgen.pipeline.ChunkContext;
import com.forgeplugins.worldgen.pipeline.GenStage;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Surface stage — runs in {@code generateSurface}, after vanilla's surface
 * step (API contract), and repaints the top layers from each biome's
 * palette: topsoil, subsurface, snow caps, volcanic ash and mountain scree.
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
                ForgeBiome biome = ctx.biomes.biomeAt(wx, wz);

                Material top = biome.surface();
                double var = ctx.terrain.surfaceVariationAt(wx, wz);
                if (biome == ForgeBiome.VOLCANIC && var > 0.45) {
                    top = Material.GRAVEL; // ash fields
                } else if (biome == ForgeBiome.MOUNTAINS && var > 0.55) {
                    top = Material.GRAVEL; // scree slopes
                } else if (biome == ForgeBiome.SCABLAND && var > 0.50) {
                    top = Material.BASALT; // exposed basalt flats
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
}
