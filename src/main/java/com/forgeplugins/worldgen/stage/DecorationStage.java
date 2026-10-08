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
                    underwater(ctx, x, wx, h, z, wz, roll, density);
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
            case BIRCH_FOREST -> 0.50;
            case DARK_FOREST -> 0.60;
            case JUNGLE -> 0.65;
            case TAIGA -> 0.45;
            case CHERRY_GROVE -> 0.55;
            case DESERT -> 0.08;
            case BEACH -> 0.06;
            case STONY_SHORE -> 0.04;
            case MOUNTAINS -> 0.12;
            case MEADOW -> 0.55;
            case VOLCANIC -> 0.03;
            case SCABLAND -> 0.12;
            case BADLANDS -> 0.08;
            case ERODED_BADLANDS -> 0.06;
            case SNOWY -> 0.18;
            case SNOWY_TAIGA -> 0.20;
            case GROVE -> 0.15;
            case FROZEN_PEAKS -> 0.05;
            case SWAMP -> 0.45;
            case MANGROVE_SWAMP -> 0.50;
            case RIVER -> 0.30;
            case MUSHROOM_FIELDS -> 0.40;
            case OCEAN, DEEP_OCEAN, WARM_OCEAN, LUKEWARM_OCEAN -> 0.0;
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
            case BIRCH_FOREST -> {
                if (pick < 0.55) put(ctx, x, y, z, Material.SHORT_GRASS);
                else if (pick < 0.65) put(ctx, x, y, z, Material.FERN);
                else if (pick < 0.75) put(ctx, x, y, z, Material.DANDELION);
                else if (pick < 0.85) put(ctx, x, y, z, Material.POPPY);
                else if (pick < 0.92) put(ctx, x, y, z, Material.LILY_OF_THE_VALLEY);
                else put(ctx, x, y, z, Material.SWEET_BERRY_BUSH);
            }
            case DARK_FOREST -> {
                boolean shady = ground == Material.PODZOL || ground == Material.MYCELIUM;
                if (pick < 0.45) put(ctx, x, y, z, Material.SHORT_GRASS);
                else if (pick < 0.60 && shady) put(ctx, x, y, z, Material.RED_MUSHROOM);
                else if (pick < 0.75 && shady) put(ctx, x, y, z, Material.BROWN_MUSHROOM);
                else if (pick < 0.85) put(ctx, x, y, z, Material.FERN);
                else put(ctx, x, y, z, Material.MOSS_CARPET);
            }
            case JUNGLE -> {
                if (pick < 0.40) put(ctx, x, y, z, Material.SHORT_GRASS);
                else if (pick < 0.55) put(ctx, x, y, z, Material.FERN);
                else if (pick < 0.65) column(ctx, x, y, z, Material.BAMBOO, 3);
                else if (pick < 0.75) put(ctx, x, y, z, Material.MELON);
                else if (pick < 0.85) put(ctx, x, y, z, Material.VINE);
                else put(ctx, x, y, z, Material.COCOA);
            }
            case TAIGA -> {
                if (pick < 0.50) put(ctx, x, y, z, Material.SHORT_GRASS);
                else if (pick < 0.65) put(ctx, x, y, z, Material.FERN);
                else if (pick < 0.80) put(ctx, x, y, z, Material.SWEET_BERRY_BUSH);
                else put(ctx, x, y, z, Material.LARGE_FERN);
            }
            case CHERRY_GROVE -> {
                if (pick < 0.45) put(ctx, x, y, z, Material.PINK_PETALS);
                else if (pick < 0.65) put(ctx, x, y, z, Material.SHORT_GRASS);
                else if (pick < 0.80) put(ctx, x, y, z, Material.DANDELION);
                else put(ctx, x, y, z, Material.POPPY);
            }
            case DESERT -> {
                if (pick < 0.75) put(ctx, x, y, z, Material.DEAD_BUSH);
                else column(ctx, x, y, z, Material.CACTUS, 2);
            }
            case BADLANDS, ERODED_BADLANDS -> {
                if (pick < 0.70) put(ctx, x, y, z, Material.DEAD_BUSH);
                else if (pick < 0.90) column(ctx, x, y, z, Material.CACTUS, 2);
                else put(ctx, x, y, z, Material.RED_SAND);
            }
            case MEADOW -> {
                if (pick < 0.40) put(ctx, x, y, z, Material.SHORT_GRASS);
                else if (pick < 0.55) put(ctx, x, y, z, Material.DANDELION);
                else if (pick < 0.70) put(ctx, x, y, z, Material.POPPY);
                else if (pick < 0.80) put(ctx, x, y, z, Material.CORNFLOWER);
                else if (pick < 0.90) put(ctx, x, y, z, Material.OXEYE_DAISY);
                else put(ctx, x, y, z, Material.ALLIUM);
            }
            case SNOWY_TAIGA, GROVE -> {
                if (pick < 0.70) put(ctx, x, y, z, Material.SHORT_GRASS);
                else if (pick < 0.90) put(ctx, x, y, z, Material.SWEET_BERRY_BUSH);
                else put(ctx, x, y, z, Material.FERN);
            }
            case FROZEN_PEAKS -> {
                if (pick < 0.80) put(ctx, x, y, z, Material.PACKED_ICE);
                else put(ctx, x, y, z, Material.BLUE_ICE);
            }
            case SWAMP -> {
                if (pick < 0.35) put(ctx, x, y, z, Material.LILY_PAD);
                else if (pick < 0.50) put(ctx, x, y, z, Material.BLUE_ORCHID);
                else if (pick < 0.65) put(ctx, x, y, z, Material.VINE);
                else if (pick < 0.80) put(ctx, x, y, z, Material.SHORT_GRASS);
                else put(ctx, x, y, z, Material.MOSS_CARPET);
            }
            case MANGROVE_SWAMP -> {
                if (pick < 0.40) put(ctx, x, y, z, Material.MANGROVE_PROPAGULE);
                else if (pick < 0.60) put(ctx, x, y, z, Material.LILY_PAD);
                else if (pick < 0.80) put(ctx, x, y, z, Material.VINE);
                else put(ctx, x, y, z, Material.MOSS_CARPET);
            }
            case MUSHROOM_FIELDS -> {
                if (pick < 0.50) put(ctx, x, y, z, Material.RED_MUSHROOM);
                else if (pick < 0.80) put(ctx, x, y, z, Material.BROWN_MUSHROOM);
                else put(ctx, x, y, z, Material.MYCELIUM);
            }
            case STONY_SHORE -> put(ctx, x, y, z, Material.DEAD_BUSH);
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

    /**
     * Underwater vegetation. Kelp and sea pickles are ocean-only — rivers
     * and lakes getting kelp towers reads as "ocean under land". Seagrass
     * is dense in oceans, sparse in rivers. Warm oceans get coral reefs.
     */
    private void underwater(@NotNull ChunkContext ctx, int x, int wx, int h, int z, int wz,
                            double roll, double density) {
        ForgeBiome biome = ctx.biomes.biomeAt(wx, wz);
        boolean warm = biome == ForgeBiome.WARM_OCEAN;
        boolean ocean = warm || biome == ForgeBiome.OCEAN || biome == ForgeBiome.DEEP_OCEAN
                || biome == ForgeBiome.LUKEWARM_OCEAN;
        double chance = (ocean ? 0.55 : 0.18) * density;
        if (roll >= chance) {
            return;
        }
        if (ctx.data.getType(x, h + 1, z) != Material.WATER) {
            return;
        }
        double pick = SeedManager.toUnit(SeedManager.hash2(ctx.seed ^ SEA_XOR, x, z));
        if (!ocean) {
            // Rivers and lakes: just a tuft of seagrass.
            put(ctx, x, h + 1, z, Material.SEAGRASS);
            return;
        }
        if (warm && pick < 0.35) {
            // Coral reef cluster.
            Material coral = pickCoral(pick);
            put(ctx, x, h + 1, z, coral);
            if (pick < 0.15) {
                put(ctx, x, h + 2, z, Material.SEA_PICKLE);
            }
            return;
        }
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

    private @NotNull Material pickCoral(double pick) {
        // Deterministic coral type from the pick value.
        int i = (int) (pick * 1000) % 5;
        return switch (i) {
            case 0 -> Material.TUBE_CORAL;
            case 1 -> Material.BRAIN_CORAL;
            case 2 -> Material.BUBBLE_CORAL;
            case 3 -> Material.FIRE_CORAL;
            default -> Material.HORN_CORAL;
        };
    }

    private void put(@NotNull ChunkContext ctx, int x, int y, int z, @NotNull Material material) {
        // Plants must be on acceptable ground (vanilla survival rules).
        // If the ground is wrong, don't place — a cactus on grass or a
        // flower on stone breaks the illusion.
        Material ground = ctx.data.getType(x, y - 1, z);
        if (!canSurviveOn(material, ground)) {
            return;
        }
        ctx.data.setBlock(x, y, z, material);
    }

    /**
     * Vanilla plant survival rules (simplified for worldgen). Returns true
     * if the plant can be placed on the given ground block.
     */
    private static boolean canSurviveOn(@NotNull Material plant, @NotNull Material ground) {
        return switch (plant) {
            // Grasses and ferns: soil-like blocks.
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN ->
                ground == Material.GRASS_BLOCK || ground == Material.DIRT
                    || ground == Material.COARSE_DIRT || ground == Material.PODZOL
                    || ground == Material.ROOTED_DIRT || ground == Material.MOSS_BLOCK
                    || ground == Material.MUD || ground == Material.MYCELIUM;
            // Flowers: grass/dirt family.
            case POPPY, DANDELION, CORNFLOWER, OXEYE_DAISY, ALLIUM, AZURE_BLUET,
                 BLUE_ORCHID, LILY_OF_THE_VALLEY, PINK_PETALS, TORCHFLOWER ->
                ground == Material.GRASS_BLOCK || ground == Material.DIRT
                    || ground == Material.COARSE_DIRT || ground == Material.PODZOL
                    || ground == Material.ROOTED_DIRT;
            // Berry bushes and pumpkins/melons: soil.
            case SWEET_BERRY_BUSH, PUMPKIN, MELON ->
                ground == Material.GRASS_BLOCK || ground == Material.DIRT
                    || ground == Material.COARSE_DIRT || ground == Material.PODZOL;
            // Dead bushes: arid ground.
            case DEAD_BUSH ->
                ground == Material.SAND || ground == Material.RED_SAND
                    || ground == Material.TERRACOTTA || ground == Material.DIRT
                    || ground == Material.COARSE_DIRT || ground == Material.RED_TERRACOTTA
                    || ground == Material.ORANGE_TERRACOTTA || ground == Material.YELLOW_TERRACOTTA;
            // Cacti: sand only.
            case CACTUS ->
                ground == Material.SAND || ground == Material.RED_SAND;
            // Sugar cane: soil/sand (water adjacency checked by vanilla on tick).
            case SUGAR_CANE ->
                ground == Material.SAND || ground == Material.RED_SAND
                    || ground == Material.GRASS_BLOCK || ground == Material.DIRT
                    || ground == Material.COARSE_DIRT || ground == Material.PODZOL;
            // Mushrooms: mycelium/podzol (or dark — we assume shade).
            case RED_MUSHROOM, BROWN_MUSHROOM ->
                ground == Material.MYCELIUM || ground == Material.PODZOL
                    || ground == Material.GRASS_BLOCK || ground == Material.DIRT;
            // Bamboo: soil/sand.
            case BAMBOO ->
                ground == Material.GRASS_BLOCK || ground == Material.DIRT
                    || ground == Material.COARSE_DIRT || ground == Material.PODZOL
                    || ground == Material.SAND || ground == Material.MUD;
            // Mangrove propagule: mud/soil.
            case MANGROVE_PROPAGULE ->
                ground == Material.MUD || ground == Material.DIRT
                    || ground == Material.GRASS_BLOCK || ground == Material.CLAY;
            // Lily pads: on water.
            case LILY_PAD -> ground == Material.WATER;
            // Vines, moss carpet: attach to solid (we're lenient).
            case VINE, MOSS_CARPET, COCOA -> ground.isSolid();
            // Snow: on solid.
            case SNOW -> ground.isSolid();
            // Underwater plants: seafloor.
            case SEAGRASS, KELP, SEA_PICKLE,
                 TUBE_CORAL, BRAIN_CORAL, BUBBLE_CORAL, FIRE_CORAL, HORN_CORAL ->
                ground.isSolid();
            // Default: allow (for blocks we didn't categorize).
            default -> true;
        };
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
