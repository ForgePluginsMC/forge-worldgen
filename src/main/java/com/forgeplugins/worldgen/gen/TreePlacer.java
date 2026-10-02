package com.forgeplugins.worldgen.gen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Deterministic flora placement. Trees are original low-block-count designs
 * placed during the noise pass with a per-column seeded hash, so they are
 * stable across restarts and cost no extra chunk passes.
 *
 * <p>Trunks stay at least two blocks from the chunk border so no tree ever
 * writes outside its own chunk.
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
        double chance = biome.treeChance() * densityMultiplier;
        double roll = terrain.columnRandom(worldX, worldZ, 0x7EE05L);
        int maxY = data.getMaxHeight();

        if (type == ForgeBiome.TreeType.CACTUS) {
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

        if (type == ForgeBiome.TreeType.NONE || roll >= chance) {
            // Ground flora: occasional grass tufts / flowers on grass biomes.
            double flora = terrain.columnRandom(worldX, worldZ, 0xF10AAAL);
            if (biome.surface() == Material.GRASS_BLOCK && flora < 0.06 && y < maxY) {
                data.setBlock(x, y, z, flora < 0.012 ? Material.POPPY : Material.SHORT_GRASS);
            }
            return;
        }

        switch (type) {
            case OAK -> placeOak(data, x, y, z, maxY, (int) (roll * 7919.0 % 3));
            case PINE -> placePine(data, x, y, z, maxY, (int) (roll * 7919.0 % 3));
            case ACACIA -> placeAcacia(data, x, y, z, maxY);
            default -> {
            }
        }
    }

    private void placeOak(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z, int maxY, int variant) {
        int trunk = 4 + variant; // 4-6
        if (y + trunk + 2 >= maxY) {
            return;
        }
        for (int i = 0; i < trunk; i++) {
            data.setBlock(x, y + i, z, Material.OAK_LOG);
        }
        BlockData leaves = oakLeaves;
        int top = y + trunk;
        // Canopy: 3x3x2 blob with corners trimmed, plus a cap.
        for (int dy = -2; dy <= 1; dy++) {
            int r = dy <= -1 ? 2 : 1;
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

    private void placePine(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z, int maxY, int variant) {
        int trunk = 6 + variant; // 6-8
        if (y + trunk + 1 >= maxY) {
            return;
        }
        for (int i = 0; i < trunk; i++) {
            data.setBlock(x, y + i, z, Material.SPRUCE_LOG);
        }
        BlockData leaves = spruceLeaves;
        // Three stacked discs shrinking toward the tip.
        int[][] layers = {{3, 2}, {2, 2}, {1, 1}};
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
