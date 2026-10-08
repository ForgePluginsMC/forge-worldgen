package com.forgeplugins.worldgen.stage;

import com.forgeplugins.worldgen.biome.ForgeBiome;
import com.forgeplugins.worldgen.pipeline.ChunkContext;
import com.forgeplugins.worldgen.pipeline.GenStage;
import com.forgeplugins.worldgen.seed.SeedManager;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Custom tree stage — huge oaks, pines, redwoods and palms grown from
 * deterministic tree shapes (no vanilla tree code involved).
 *
 * <p>Border safety without continuation state: tree spots live on a fixed
 * world grid, and each chunk independently grows every tree whose canopy
 * can touch it (cells overlapped with a margin). Same inputs ⇒ same tree,
 * whichever chunk asks — so borders never cut trees.
 */
public final class TreeStage implements GenStage {

    private static final int CELL = 40;
    private static final int MARGIN = 20;
    private static final long TREE_XOR = 0x7CEE5EEDL;
    private static final long POS_XOR = 0xB0517107L;

    private enum TreeType { OAK, HUGE_OAK, PINE, REDWOOD, PALM }

    private record TreeSpot(int x, int z, int baseY, TreeType type, int trunkH,
                            int leanX, int leanZ, long bits) {}

    @Override
    public @NotNull String name() {
        return "trees";
    }

    @Override
    public void generate(@NotNull ChunkContext ctx) {
        if (!ctx.config.customTrees()) {
            return;
        }
        double densityScale = ctx.config.treeDensity();
        int baseX = ctx.chunkX << 4;
        int baseZ = ctx.chunkZ << 4;
        long ccx0 = Math.floorDiv(baseX - MARGIN, CELL);
        long ccx1 = Math.floorDiv(baseX + 16 + MARGIN, CELL);
        long ccz0 = Math.floorDiv(baseZ - MARGIN, CELL);
        long ccz1 = Math.floorDiv(baseZ + 16 + MARGIN, CELL);
        for (long ccx = ccx0; ccx <= ccx1; ccx++) {
            for (long ccz = ccz0; ccz <= ccz1; ccz++) {
                TreeSpot spot = treeInCell(ctx, ccx, ccz, densityScale);
                if (spot != null) {
                    growTree(ctx, baseX, baseZ, spot);
                }
            }
        }
    }

    private TreeSpot treeInCell(@NotNull ChunkContext ctx, long ccx, long ccz,
                                double densityScale) {
        int cx = (int) ccx;
        int cz = (int) ccz;
        long h1 = SeedManager.hash2(ctx.seed ^ TREE_XOR, cx, cz);
        double roll = SeedManager.toUnit(h1);
        int centerX = (int) (ccx * CELL + CELL / 2);
        int centerZ = (int) (ccz * CELL + CELL / 2);
        ForgeBiome biome = ctx.biomes.biomeAt(centerX, centerZ);
        double density = treeDensity(biome) * densityScale;
        if (density <= 0.0 || roll >= density) {
            return null;
        }
        long h2 = SeedManager.hash2(ctx.seed ^ POS_XOR, cx * 31 + 7, cz * 31 + 13);
        double jx = (SeedManager.toUnit(h2) - 0.5) * CELL * 0.7;
        double jz = (SeedManager.toUnit(h2 ^ 0x9E3779B9L) - 0.5) * CELL * 0.7;
        int tx = (int) (ccx * CELL + CELL / 2 + jx);
        int tz = (int) (ccz * CELL + CELL / 2 + jz);
        int baseY = ctx.terrain.heightAt(tx, tz);
        if (baseY <= ctx.terrain.seaLevel() || baseY + 30 >= ctx.maxY()) {
            return null;
        }
        double typeRoll = SeedManager.toUnit(h1 ^ 0x51AB3F9CL);
        TreeType type = pickType(biome, typeRoll);
        int trunkH = switch (type) {
            case OAK -> 5 + (int) (typeRoll * 97) % 3;
            case HUGE_OAK -> 10 + (int) (typeRoll * 131) % 5;
            case PINE -> 9 + (int) (typeRoll * 57) % 5;
            case REDWOOD -> 22 + (int) (typeRoll * 211) % 9;
            case PALM -> 6 + (int) (typeRoll * 37) % 4;
        };
        long bits = h2 ^ (h1 >>> 17);
        int leanX = (int) (bits % 3) - 1;
        int leanZ = (int) ((bits >> 4) % 3) - 1;
        return new TreeSpot(tx, tz, baseY, type, trunkH, leanX, leanZ, bits);
    }

    private double treeDensity(@NotNull ForgeBiome biome) {
        return switch (biome) {
            case FOREST -> 0.50;
            case PLAINS -> 0.10;
            case SNOWY -> 0.22;
            case MOUNTAINS -> 0.05;
            case BEACH -> 0.16;
            default -> 0.0;
        };
    }

    private @NotNull TreeType pickType(@NotNull ForgeBiome biome, double r) {
        return switch (biome) {
            case FOREST -> r < 0.25 ? TreeType.HUGE_OAK : r < 0.55 ? TreeType.OAK
                    : r < 0.75 ? TreeType.PINE : r < 0.85 ? TreeType.REDWOOD : TreeType.OAK;
            case PLAINS -> r < 0.70 ? TreeType.OAK : TreeType.HUGE_OAK;
            case SNOWY, MOUNTAINS -> r < 0.85 ? TreeType.PINE : TreeType.OAK;
            case BEACH -> TreeType.PALM;
            default -> TreeType.OAK;
        };
    }

    private void growTree(@NotNull ChunkContext ctx, int baseX, int baseZ, @NotNull TreeSpot spot) {
        switch (spot.type) {
            case OAK -> growOak(ctx, baseX, baseZ, spot, Material.OAK_LOG, Material.OAK_LEAVES, false);
            case HUGE_OAK -> growOak(ctx, baseX, baseZ, spot, Material.OAK_LOG, Material.OAK_LEAVES, true);
            case PINE -> growPine(ctx, baseX, baseZ, spot);
            case REDWOOD -> growRedwood(ctx, baseX, baseZ, spot);
            case PALM -> growPalm(ctx, baseX, baseZ, spot);
        }
    }

    private void growOak(@NotNull ChunkContext ctx, int baseX, int baseZ, @NotNull TreeSpot spot,
                         @NotNull Material log, @NotNull Material leaves, boolean huge) {
        int w = huge ? 2 : 1;
        int top = spot.baseY + spot.trunkH;
        for (int i = 1; i <= spot.trunkH; i++) {
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < w; dz++) {
                    wood(ctx, baseX, baseZ, spot.x + dx, spot.baseY + i, spot.z + dz, log);
                }
            }
        }
        int cr = huge ? 4 : 2;
        // Canopy: two full layers, then a smaller cap.
        disc(ctx, baseX, baseZ, spot.x + (w - 1) / 2, top - 1, spot.z + (w - 1) / 2, cr, leaves);
        disc(ctx, baseX, baseZ, spot.x + (w - 1) / 2, top, spot.z + (w - 1) / 2, cr, leaves);
        disc(ctx, baseX, baseZ, spot.x + (w - 1) / 2, top + 1, spot.z + (w - 1) / 2, cr - 1, leaves);
        if (huge) {
            disc(ctx, baseX, baseZ, spot.x, top + 2, spot.z, 1, leaves);
        }
    }

    private void growPine(@NotNull ChunkContext ctx, int baseX, int baseZ, @NotNull TreeSpot spot) {
        int top = spot.baseY + spot.trunkH;
        for (int i = 1; i <= spot.trunkH; i++) {
            wood(ctx, baseX, baseZ, spot.x, spot.baseY + i, spot.z, Material.SPRUCE_LOG);
        }
        int layers = 4;
        for (int i = 0; i < layers; i++) {
            int y = spot.baseY + 3 + i * 2;
            if (y > top) break;
            disc(ctx, baseX, baseZ, spot.x, y, spot.z, 3 - i, Material.SPRUCE_LEAVES);
        }
        leaf(ctx, baseX, baseZ, spot.x, top + 1, spot.z, Material.SPRUCE_LEAVES);
    }

    private void growRedwood(@NotNull ChunkContext ctx, int baseX, int baseZ, @NotNull TreeSpot spot) {
        int top = spot.baseY + spot.trunkH;
        for (int i = 1; i <= spot.trunkH; i++) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    wood(ctx, baseX, baseZ, spot.x + dx, spot.baseY + i, spot.z + dz, Material.SPRUCE_LOG);
                }
            }
        }
        disc(ctx, baseX, baseZ, spot.x, top - 2, spot.z, 5, Material.SPRUCE_LEAVES);
        disc(ctx, baseX, baseZ, spot.x, top - 1, spot.z, 5, Material.SPRUCE_LEAVES);
        disc(ctx, baseX, baseZ, spot.x, top, spot.z, 3, Material.SPRUCE_LEAVES);
        disc(ctx, baseX, baseZ, spot.x, top + 1, spot.z, 2, Material.SPRUCE_LEAVES);
        leaf(ctx, baseX, baseZ, spot.x, top + 2, spot.z, Material.SPRUCE_LEAVES);
    }

    private void growPalm(@NotNull ChunkContext ctx, int baseX, int baseZ, @NotNull TreeSpot spot) {
        int x = spot.x;
        int z = spot.z;
        for (int i = 1; i <= spot.trunkH; i++) {
            int ox = (i * spot.leanX) / 3;
            int oz = (i * spot.leanZ) / 3;
            wood(ctx, baseX, baseZ, x + ox, spot.baseY + i, z + oz, Material.JUNGLE_LOG);
            x = spot.x + ox;
            z = spot.z + oz;
        }
        int topX = x;
        int topY = spot.baseY + spot.trunkH;
        int topZ = z;
        // Fronds: 8 drooping arms.
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] d : dirs) {
            for (int k = 1; k <= 3; k++) {
                leaf(ctx, baseX, baseZ, topX + d[0] * k, topY - k / 2, topZ + d[1] * k, Material.OAK_LEAVES);
            }
        }
        leaf(ctx, baseX, baseZ, topX, topY + 1, topZ, Material.OAK_LEAVES);
    }

    /** Leaf disc of radius r at (cx, y, cz); only replaces air. */
    private void disc(@NotNull ChunkContext ctx, int baseX, int baseZ,
                      int cx, int y, int cz, int r, @NotNull Material leaves) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r + (r > 2 ? 1 : 0)) {
                    // Trim the very corners of big discs for a rounder canopy.
                    if (r > 2 && Math.abs(dx) == r && Math.abs(dz) == r) {
                        continue;
                    }
                    leaf(ctx, baseX, baseZ, cx + dx, y, cz + dz, leaves);
                }
            }
        }
    }

    private void wood(@NotNull ChunkContext ctx, int baseX, int baseZ,
                      int wx, int y, int wz, @NotNull Material log) {
        put(ctx, baseX, baseZ, wx, y, wz, log, false);
    }

    private void leaf(@NotNull ChunkContext ctx, int baseX, int baseZ,
                      int wx, int y, int wz, @NotNull Material leaves) {
        put(ctx, baseX, baseZ, wx, y, wz, leaves, true);
    }

    /**
     * Chunk-clipped block write. Wood wins (replaces snow/decor in its way);
     * leaves only fill air so canopies don't eat terrain or trunks.
     */
    private void put(@NotNull ChunkContext ctx, int baseX, int baseZ,
                     int wx, int y, int wz, @NotNull Material material, boolean onlyAir) {
        int rx = wx - baseX;
        int rz = wz - baseZ;
        if (rx < 0 || rx > 15 || rz < 0 || rz > 15) {
            return;
        }
        if (y < ctx.minY() || y >= ctx.maxY()) {
            return;
        }
        if (onlyAir && ctx.data.getType(rx, y, rz) != Material.AIR) {
            return;
        }
        ctx.data.setBlock(rx, y, rz, material);
    }
}
