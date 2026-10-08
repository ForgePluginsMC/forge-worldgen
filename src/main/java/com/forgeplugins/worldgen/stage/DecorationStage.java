package com.forgeplugins.worldgen.stage;

import com.forgeplugins.worldgen.biome.ForgeBiome;
import com.forgeplugins.worldgen.pipeline.ChunkContext;
import com.forgeplugins.worldgen.pipeline.GenStage;
import com.forgeplugins.worldgen.seed.SeedManager;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Decoration stage — scatters vegetation and surface detail across the
 * world: grass tufts, flowers, mushrooms, moss, berry bushes, pumpkins,
 * dead bushes, cacti, sugar cane, seagrass, kelp and sea pickles.
 *
 * <p>Everything is a deterministic hash of world coordinates — no Random,
 * no chunk-border seams. Runs after the surface stage, so decor sits on
 * the finished terrain. Stateless and thread-safe.
 */
public final class DecorationStage implements GenStage {

    private static final long SEED_XOR = 0xDEC04A7CL;
    private static final long PICK_XOR = 0x9E3779B9L;
    private static final long SEA_XOR = 0x5EA911L;

    @Override
    public @NotNull String name() {
        return "decoration";
    }

    @Override
    public void generate(@NotNull ChunkContext ctx) {
        if (!ctx.config.decorations()) {
            return;
        }
        double density = ctx.config.decorDensity();
        int minY = ctx.minY();
        int maxY = ctx.maxY();
        int sea = ctx.terrain.seaLevel();
        long seed = ctx.seed ^ SEED_XOR;
        int baseX = ctx.chunkX << 4;
        int baseZ = ctx.chunkZ << 4;

        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = baseX + x;
                int wz = baseZ + z;
                int h = Math.clamp(ctx.terrain.heightAt(wx, wz), minY + 1, maxY - 3);
                double roll = SeedManager.toUnit(SeedManager.hash2(seed, wx, wz));

                if (h < sea - 1) {
                    underwater(ctx, x, h, z, roll, density);
                    continue;
                }

                ForgeBiome biome = ctx.biomes.biomeAt(wx, wz);
                double chance = baseChance(biome) * density;
                if (roll >= chance) {
                    continue;
                }
                double pick = SeedManager.toUnit(SeedManager.hash2(seed ^ PICK_XOR, wx, wz));
                placeLandDecor(ctx, x, h, z, biome, pick);
            }
        }
    }

    private double baseChance(@NotNull ForgeBiome biome) {
        return switch (biome) {
            case PLAINS -> 0.50;
            case FOREST -> 0.55;
            case DESERT -> 0.08;
            case BEACH -> 0.06;
            case MOUNTAINS -> 0.12;
            case VOLCANIC -> 0.03;
            case SCABLAND -> 0.12;
            case SNOWY -> 0.18;
            case RIVER -> 0.30;
            case OCEAN, DEEP_OCEAN -> 0.0;
        };
    }

    private void placeLandDecor(@NotNull ChunkContext ctx, int x, int h, int z,
                                @NotNull ForgeBiome biome, double pick) {
        // First free spot above the surface (snow layers sit at h+1).
        int y = h + 1;
        if (ctx.data.getType(x, y, z) != Material.AIR) {
            y = h + 2;
            if (y >= ctx.maxY() || ctx.data.getType(x, y, z) != Material.AIR) {
                return;
            }
        }
        Material ground = ctx.data.getType(x, h, z);

        switch (biome) {
            case PLAINS -> {
                if (pick < 0.55) put(ctx, x, y, z, Material.SHORT_GRASS);
                else if (pick < 0.67) put(ctx, x, y, z, Material.TALL_GRASS);
                else if (pick < 0.73) put(ctx, x, y, z, Material.POPPY);
                else if (pick < 0.79) put(ctx, x, y, z, Material.DANDELION);
                else if (pick < 0.84) put(ctx, x, y, z, Material.CORNFLOWER);
                else if (pick < 0.89) put(ctx, x, y, z, Material.OXEYE_DAISY);
                else if (pick < 0.93) put(ctx, x, y, z, Material.SWEET_BERRY_BUSH);
                else if (pick < 0.945) put(ctx, x, y, z, Material.PUMPKIN);
                else put(ctx, x, y, z, Material.FERN);
            }
            case FOREST -> {
                // Mushrooms only take on podzol/moss — anywhere else they'd pop off.
                boolean shady = ground == Material.PODZOL || ground == Material.MOSS_BLOCK;
                if (pick < 0.50) put(ctx, x, y, z, Material.SHORT_GRASS);
                else if (pick < 0.60) put(ctx, x, y, z, Material.FERN);
                else if (pick < 0.68 && shady) put(ctx, x, y, z, Material.RED_MUSHROOM);
                else if (pick < 0.76 && shady) put(ctx, x, y, z, Material.BROWN_MUSHROOM);
                else if (pick < 0.84) put(ctx, x, y, z, Material.MOSS_CARPET);
                else if (pick < 0.89) put(ctx, x, y, z, Material.LILY_OF_THE_VALLEY);
                else if (pick < 0.94) put(ctx, x, y, z, Material.ALLIUM);
                else if (pick < 0.97) put(ctx, x, y, z, Material.SWEET_BERRY_BUSH);
                else put(ctx, x, y, z, Material.SHORT_GRASS);
            }
            case DESERT -> {
                if (pick < 0.75) put(ctx, x, y, z, Material.DEAD_BUSH);
                else column(ctx, x, y, z, Material.CACTUS, 2);
            }
            case BEACH -> {
                if (pick < 0.60) column(ctx, x, y, z, Material.SUGAR_CANE, 2);
                else put(ctx, x, y, z, Material.DEAD_BUSH);
            }
            case MOUNTAINS -> {
                if (pick < 0.70) put(ctx, x, y, z, Material.SHORT_GRASS);
                else put(ctx, x, y, z, Material.FERN);
            }
            case VOLCANIC -> put(ctx, x, y, z, Material.DEAD_BUSH);
            case SCABLAND -> {
                if (pick < 0.60) put(ctx, x, y, z, Material.DEAD_BUSH);
                else put(ctx, x, y, z, Material.SHORT_GRASS);
            }
            case SNOWY -> {
                if (pick < 0.70) put(ctx, x, y, z, Material.SHORT_GRASS);
                else if (pick < 0.90) put(ctx, x, y, z, Material.SWEET_BERRY_BUSH);
                else put(ctx, x, y, z, Material.FERN);
            }
            case RIVER -> {
                if (pick < 0.65) column(ctx, x, y, z, Material.SUGAR_CANE, 3);
                else if (pick < 0.85) put(ctx, x, y, z, Material.SHORT_GRASS);
                else put(ctx, x, y, z, Material.CLAY);
            }
            case OCEAN, DEEP_OCEAN -> { /* handled as underwater */ }
        }
    }

    private void underwater(@NotNull ChunkContext ctx, int x, int h, int z,
                            double roll, double density) {
        if (roll >= 0.55 * density) {
            return;
        }
        if (ctx.data.getType(x, h + 1, z) != Material.WATER) {
            return;
        }
        double pick = SeedManager.toUnit(SeedManager.hash2(ctx.seed ^ SEA_XOR, x, z));
        if (pick < 0.62) {
            put(ctx, x, h + 1, z, Material.SEAGRASS);
        } else if (pick < 0.80) {
            int tall = 2 + (int) (pick * 100) % 3;
            for (int i = 1; i <= tall && h + i < ctx.maxY(); i++) {
                if (ctx.data.getType(x, h + i, z) == Material.WATER) {
                    put(ctx, x, h + i, z, Material.KELP);
                } else {
                    break;
                }
            }
        } else if (pick < 0.84) {
            put(ctx, x, h + 1, z, Material.SEA_PICKLE);
        } else {
            put(ctx, x, h + 1, z, Material.SEAGRASS);
        }
    }

    private void put(@NotNull ChunkContext ctx, int x, int y, int z, @NotNull Material material) {
        ctx.data.setBlock(x, y, z, material);
    }

    private void column(@NotNull ChunkContext ctx, int x, int y, int z,
                        @NotNull Material material, int height) {
        for (int i = 0; i < height && y + i < ctx.maxY(); i++) {
            if (ctx.data.getType(x, y + i, z) == Material.AIR) {
                ctx.data.setBlock(x, y + i, z, material);
            } else {
                break;
            }
        }
    }
}
