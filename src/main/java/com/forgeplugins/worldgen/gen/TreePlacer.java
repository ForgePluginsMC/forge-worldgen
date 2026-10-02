package com.forgeplugins.worldgen.gen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Deterministic flora placement. Trees use a hash-driven poisson-ish
 * scatter — grove clumping plus local-minimum thinning — so forests read as
 * natural woodland, never rows, columns or a lattice. Trunks stay far enough
 * inside the chunk border (per their canopy radius) that no tree ever writes
 * outside its own chunk, and everything is a pure function of world
 * coordinates, so placement is stable across restarts and chunk borders.
 */
public final class TreePlacer {

    private final BlockData oakLeaves;
    private final BlockData spruceLeaves;
    private final BlockData acaciaLeaves;

    public TreePlacer(@Nullable BlockData oakLeaves, @Nullable BlockData spruceLeaves,
                      @Nullable BlockData acaciaLeaves) {
        this.oakLeaves = oakLeaves;
        this.spruceLeaves = spruceLeaves;
        this.acaciaLeaves = acaciaLeaves;
    }

    /**
     * Attempts to grow flora at a surface column. Chunk-local x/z, y is the
     * air block above the surface.
     */
    public void tryPlace(@NotNull TerrainModel terrain, @NotNull ChunkGenerator.ChunkData data,
                         int x, int y, int z, int worldX, int worldZ,
                         @NotNull ForgeBiome biome, double densityMultiplier) {
        ForgeBiome.TreeType type = biome.trees();
        int maxY = data.getMaxHeight();

        if (type == ForgeBiome.TreeType.CACTUS) {
            double roll = terrain.columnRandom(worldX, worldZ, 0x7EE05L);
            double chance = biome.treeChance() * densityMultiplier;
            if (roll < chance && y + 3 < maxY) {
                int h = 2 + (int) (roll * 997.0 % 2);
                for (int i = 0; i < h; i++) {
                    data.setBlock(x, y + i, z, Material.CACTUS);
                }
            } else if (roll < chance * 4.0) {
                data.setBlock(x, y, z, Material.DEAD_BUSH);
            }
            return;
        }

        double roll = terrain.columnRandom(worldX, worldZ, 0x7EE05L);
        if (type == ForgeBiome.TreeType.NONE
                || !acceptTree(terrain, worldX, worldZ, biome, densityMultiplier, roll)) {
            // Ground flora: occasional grass tufts / flowers on grass biomes.
            double flora = terrain.columnRandom(worldX, worldZ, 0xF10AAAL);
            if (biome.surface() == Material.GRASS_BLOCK && flora < 0.06 && y < maxY) {
                data.setBlock(x, y, z, flora < 0.012 ? Material.POPPY : Material.SHORT_GRASS);
            }
            return;
        }

        // Scatter the trunk off the column grid with a hash jitter, bury the
        // base two blocks so trees never float on slopes, and vary the
        // canopy: one in four trees grows a grander "elder" crown.
        long hj = TerrainModel.hash2(terrain.seed() ^ 0xE1DE2L, worldX, worldZ);
        int canopyR = switch (type) {
            case OAK -> 2;
            case PINE -> 3;
            case ACACIA -> 2;
            default -> 2;
        };
        boolean elder = (hj & 3) == 0;
        if (elder) {
            canopyR += 1;
        }
        int jx = (int) ((hj >>> 32) % 3) - 1;
        int jz = (int) ((hj >>> 40) % 3) - 1;
        int tx = clamp(x + jx, canopyR, 15 - canopyR);
        int tz = clamp(z + jz, canopyR, 15 - canopyR);
        int variant = (int) (roll * 7919.0 % 4);

        switch (type) {
            case OAK -> placeOak(data, tx, y - 2, tz, maxY, variant, elder);
            case PINE -> placePine(data, tx, y - 2, tz, maxY, variant, elder);
            case ACACIA -> placeAcacia(data, tx, y - 2, tz, maxY);
            default -> {
            }
        }
    }

    /**
     * Poisson-ish tree acceptance. A low-frequency grove field breaks uniform
     * coverage into natural groves and clearings; local-minimum thinning then
     * keeps a candidate only when no neighbouring candidate has a lower roll,
     * which enforces irregular spacing with no visible lattice. Pure function
     * of world coordinates — identical on both sides of every chunk border.
     */
    private static boolean acceptTree(@NotNull TerrainModel terrain, int wx, int wz,
                                      @NotNull ForgeBiome biome, double densityMultiplier,
                                      double roll) {
        double grove = terrain.columnRandom(wx >> 3, wz >> 3, 0x620E5L);
        double chance = biome.treeChance() * densityMultiplier * (0.25 + 1.5 * grove);
        if (chance <= 0.0 || roll >= chance) {
            return false;
        }
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                double nRoll = terrain.columnRandom(wx + dx, wz + dz, 0x7EE05L);
                if (nRoll < chance && nRoll < roll) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private void placeOak(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z, int maxY,
                          int variant, boolean elder) {
        int trunk = 4 + variant; // 4-7
        if (y + trunk + 2 >= maxY) {
            return;
        }
        for (int i = 0; i < trunk; i++) {
            data.setBlock(x, y + i, z, Material.OAK_LOG);
        }
        BlockData leaves = oakLeaves;
        int top = y + trunk;
        int bottomR = elder ? 3 : 2;
        // Canopy: 3x3x2 blob with corners trimmed, plus a cap; elders spread wider.
        for (int dy = -2; dy <= 1; dy++) {
            int r = dy <= -1 ? bottomR : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r && Math.abs(dz) == r && (dy == 1 || ((dx * dz + dy) & 1) == 0)) {
                        continue;
                    }
                    setLeaves(data, x + dx, top + dy, z + dz, leaves, Material.OAK_LEAVES);
                }
            }
        }
        setLeaves(data, x, top + 1, z, leaves, Material.OAK_LEAVES);
    }

    private void placePine(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z, int maxY,
                           int variant, boolean elder) {
        int trunk = 6 + variant % 3; // 6-8
        if (y + trunk + 1 >= maxY) {
            return;
        }
        for (int i = 0; i < trunk; i++) {
            data.setBlock(x, y + i, z, Material.SPRUCE_LOG);
        }
        BlockData leaves = spruceLeaves;
        // Stacked discs shrinking toward the tip; elders get a wider skirt.
        int[][] layers = elder
                ? new int[][]{{4, 2}, {3, 2}, {2, 1}, {1, 1}}
                : new int[][]{{3, 2}, {2, 2}, {1, 1}};
        int ly = y + 2;
        for (int[] layer : layers) {
            int r = layer[0];
            for (int i = 0; i < layer[1]; i++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) == r && Math.abs(dz) == r) {
                            continue;
                        }
                        setLeaves(data, x + dx, ly, z + dz, leaves, Material.SPRUCE_LEAVES);
                    }
                }
                ly++;
            }
        }
        setLeaves(data, x, ly, z, leaves, Material.SPRUCE_LEAVES);
    }

    private void placeAcacia(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z, int maxY) {
        int trunk = 4;
        if (y + trunk + 2 >= maxY) {
            return;
        }
        // Slight lean for character.
        int lean = (x + z) & 1;
        for (int i = 0; i < trunk; i++) {
            data.setBlock(x + (i > 1 ? lean : 0), y + i, z, Material.ACACIA_LOG);
        }
        BlockData leaves = acaciaLeaves;
        int topX = x + lean;
        int top = y + trunk;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    continue;
                }
                setLeaves(data, topX + dx, top, z + dz, leaves, Material.ACACIA_LEAVES);
                if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                    setLeaves(data, topX + dx, top + 1, z + dz, leaves, Material.ACACIA_LEAVES);
                }
            }
        }
    }

    private void setLeaves(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z,
                           @Nullable BlockData template, @NotNull Material fallback) {
        // Only fill air so canopies never overwrite trunks or neighbours.
        if (data.getType(x, y, z) != Material.AIR) {
            return;
        }
        if (template != null) {
            data.setBlock(x, y, z, template);
        } else {
            data.setBlock(x, y, z, fallback);
        }
    }
}
