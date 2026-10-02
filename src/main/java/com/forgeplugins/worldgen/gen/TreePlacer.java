package com.forgeplugins.worldgen.gen;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Deterministic flora placement. Trees use a hash-driven poisson-ish
 * scatter — grove clumping plus local-minimum thinning — so forests read as
 * natural woodland, never rows, columns or a lattice. Every design keeps its
 * trunk far enough inside the chunk border (per its canopy radius) that it
 * never writes outside its own chunk, and everything is a pure function of
 * world coordinates, so placement is stable across restarts and chunk
 * borders.
 */
public final class TreePlacer {

    private final BlockData oakLeaves;
    private final BlockData spruceLeaves;
    private final BlockData acaciaLeaves;
    private final BlockData jungleLeaves;

    public TreePlacer(@Nullable BlockData oakLeaves, @Nullable BlockData spruceLeaves,
                      @Nullable BlockData acaciaLeaves, @Nullable BlockData jungleLeaves) {
        this.oakLeaves = oakLeaves;
        this.spruceLeaves = spruceLeaves;
        this.acaciaLeaves = acaciaLeaves;
        this.jungleLeaves = jungleLeaves;
    }

    /**
     * Attempts to grow flora at a surface column. Chunk-local x/z, y is the
     * air block above the surface, sea is the world sea level.
     */
    public void tryPlace(@NotNull TerrainModel terrain, @NotNull ChunkGenerator.ChunkData data,
                         int x, int y, int z, int worldX, int worldZ,
                         @NotNull ForgeBiome biome, double densityMultiplier, int sea) {
        ForgeBiome.TreeType type = biome.trees();
        int maxY = data.getMaxHeight();
        int groundY = y - 1;

        // Glacial-erratic boulders: big irregular rock formations, partially
        // buried, never floating. Origins stay a full radius inside the
        // border so no boulder ever crosses into a neighbour chunk.
        double broll = terrain.columnRandom(worldX, worldZ, 0xB011DE2L);
        if (biome.boulderChance() > 0.0 && broll < biome.boulderChance() * densityMultiplier) {
            placeBoulder(data, x, groundY, z, maxY, terrain.seed(), worldX, worldZ);
        }

        // Ancient mega-trees: rare 2x2-trunk landmarks in the deep forests.
        if ((biome == ForgeBiome.MISTWOOD || biome == ForgeBiome.VERDANT_VALE)
                && x >= 5 && x <= 9 && z >= 5 && z <= 9 && y + 28 < maxY) {
            double mega = terrain.columnRandom(worldX, worldZ, 0x9E9A71L);
            if (mega < 0.006 * densityMultiplier) {
                placeMegaTree(data, x, y - 2, z, maxY, terrain.seed(), worldX, worldZ);
                return;
            }
        }

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

        // Palms root at the waterline; every other tree needs dry land.
        boolean beach = groundY <= sea + 1;
        if (type == ForgeBiome.TreeType.NONE || (beach && type != ForgeBiome.TreeType.PALM)
                || (!beach && type == ForgeBiome.TreeType.PALM)) {
            groundFlora(terrain, data, x, y, z, worldX, worldZ, biome, maxY);
            return;
        }

        double roll = terrain.columnRandom(worldX, worldZ, 0x7EE05L);
        if (!acceptTree(terrain, worldX, worldZ, biome, densityMultiplier, roll)) {
            groundFlora(terrain, data, x, y, z, worldX, worldZ, biome, maxY);
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
            case WINDSWEPT_PINE -> 3;
            case DEAD_SNAG -> 1;
            case PALM -> 2;
            default -> 2;
        };
        boolean elder = (hj & 3) == 0;
        if (elder && (type == ForgeBiome.TreeType.OAK || type == ForgeBiome.TreeType.PINE)) {
            canopyR += 1;
        }
        int jx = (int) ((hj >>> 32) % 3) - 1;
        int jz = (int) ((hj >>> 40) % 3) - 1;
        int tx = clamp(x + jx, canopyR, 15 - canopyR);
        int tz = clamp(z + jz, canopyR, 15 - canopyR);
        int variant = (int) (roll * 7919.0 % 4);

        // Mistwood hides the occasional jungle giant among its oaks.
        if (biome == ForgeBiome.MISTWOOD && type == ForgeBiome.TreeType.OAK
                && terrain.columnRandom(worldX, worldZ, 0x6E6174L) < 0.10
                && tx >= 4 && tx <= 11 && tz >= 4 && tz <= 11) {
            placeJungleGiant(data, tx, y - 2, tz, maxY, variant);
            return;
        }

        switch (type) {
            case OAK -> placeOak(data, tx, y - 2, tz, maxY, variant, elder);
            case PINE -> placePine(data, tx, y - 2, tz, maxY, variant, elder);
            case ACACIA -> placeAcacia(data, tx, y - 2, tz, maxY);
            case WINDSWEPT_PINE -> placeWindsweptPine(data, tx, y - 2, tz, maxY, hj);
            case DEAD_SNAG -> placeDeadSnag(data, tx, y - 2, tz, maxY, hj);
            case PALM -> placePalm(data, tx, y - 2, tz, maxY, hj);
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

    /** Ground flora: grass tufts, wildflowers and low bushes on grass. */
    private void groundFlora(@NotNull TerrainModel terrain, @NotNull ChunkGenerator.ChunkData data,
                             int x, int y, int z, int wx, int wz,
                             @NotNull ForgeBiome biome, int maxY) {
        if (biome.surface() != Material.GRASS_BLOCK || y >= maxY) {
            return;
        }
        double flora = terrain.columnRandom(wx, wz, 0xF10AAAL);
        if (flora >= 0.10) {
            return;
        }
        if (flora < 0.018) {
            // Low bush: a couple of leaf blocks, persistent so they never rot.
            setLeaves(data, x, y, z, oakLeaves, Material.OAK_LEAVES);
            if (((wx * 31 + wz) & 1) == 0) {
                setLeaves(data, x, y + 1, z, oakLeaves, Material.OAK_LEAVES);
            }
            return;
        }
        if (flora < 0.055) {
            Material[] flowers = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER,
                    Material.ALLIUM, Material.OXEYE_DAISY, Material.LILY_OF_THE_VALLEY};
            long fh = TerrainModel.hash2(terrain.seed() ^ 0xF10E2L, wx, wz);
            data.setBlock(x, y, z, flowers[(int) ((fh >>> 16) % flowers.length)]);
            return;
        }
        data.setBlock(x, y, z, Material.SHORT_GRASS);
    }

    /**
     * Glacial-erratic boulder: an irregular rock mass 5-12 blocks across,
     * sunk ~40% into the ground. Origin must clear a full radius from the
     * chunk border; the shape jitters per block so no two match.
     */
    private void placeBoulder(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z,
                              int maxY, long seed, int wx, int wz) {
        long hb = TerrainModel.hash2(seed ^ 0xB011DE2L, wx, wz);
        int r = 2 + (int) ((hb >>> 20) % 5); // 2..6 → 5..13 across
        if (x - r < 0 || x + r > 15 || z - r < 0 || z + r > 15) {
            return;
        }
        int ry = Math.max(2, (int) (r * 0.7));
        int cy = y - (int) (r * 0.4); // partially buried
        if (cy - ry < 1 || cy + ry >= maxY) {
            return;
        }
        for (int dy = -ry; dy <= ry; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double nx = (double) dx / (r + 0.5);
                    double ny = (double) dy / (ry + 0.5);
                    double nz = (double) dz / (r + 0.5);
                    long jb = TerrainModel.hash2(seed ^ 0xB011DE2L ^ (dx * 131L) ^ (dz * 17L),
                            wx + dy, wz);
                    double jitter = (((jb >>> 8) & 7) - 3.5) / 9.0;
                    if (nx * nx + ny * ny + nz * nz + jitter > 1.0) {
                        continue;
                    }
                    Material rock;
                    long pick = (jb >>> 32) & 15;
                    if (pick < 9) {
                        rock = Material.STONE;
                    } else if (pick < 12) {
                        rock = Material.COBBLESTONE;
                    } else {
                        rock = Material.MOSSY_COBBLESTONE;
                    }
                    int bx = x + dx;
                    int by = cy + dy;
                    int bz = z + dz;
                    Material cur = data.getType(bx, by, bz);
                    if (cur == Material.AIR || cur == Material.STONE || cur == Material.DIRT
                            || cur == Material.GRASS_BLOCK || cur == Material.SAND
                            || cur == Material.GRAVEL || cur == Material.COARSE_DIRT) {
                        data.setBlock(bx, by, bz, rock);
                    }
                }
            }
        }
    }

    /** Tall rainforest giant: 2x2 trunk, broad canopy, hanging leaf strands. */
    private void placeJungleGiant(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z,
                                  int maxY, int variant) {
        int trunk = 12 + variant % 5; // 12-16
        if (y + trunk + 6 >= maxY || x + 1 > 15 || z + 1 > 15) {
            return;
        }
        for (int i = 0; i < trunk; i++) {
            data.setBlock(x, y + i, z, Material.JUNGLE_LOG);
            data.setBlock(x + 1, y + i, z, Material.JUNGLE_LOG);
            data.setBlock(x, y + i, z + 1, Material.JUNGLE_LOG);
            data.setBlock(x + 1, y + i, z + 1, Material.JUNGLE_LOG);
        }
        int top = y + trunk;
        // Broad three-tier canopy.
        for (int dy = -3; dy <= 1; dy++) {
            int r = dy <= -2 ? 4 : (dy <= 0 ? 3 : 2);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r && Math.abs(dz) == r && ((dx + dz + dy) & 1) == 0) {
                        continue;
                    }
                    setLeaves(data, x + dx, top + dy, z + dz, jungleLeaves, Material.JUNGLE_LEAVES);
                }
            }
        }
        // Hanging leaf strands below the canopy rim.
        for (int s = 0; s < 8; s++) {
            int sx = x + (s * 5 + 1) % 9 - 4;
            int sz = z + (s * 3 + 2) % 9 - 4;
            int len = 2 + (s % 3);
            for (int i = 1; i <= len; i++) {
                if (data.getType(sx, top - 3 - i, sz) != Material.AIR) {
                    break;
                }
                if (jungleLeaves != null) {
                    data.setBlock(sx, top - 3 - i, sz, jungleLeaves);
                } else {
                    data.setBlock(sx, top - 3 - i, sz, Material.JUNGLE_LEAVES);
                }
            }
        }
    }

    /** Wind-shaped pine: short leaning trunk, canopy swept to the lee side. */
    private void placeWindsweptPine(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z,
                                    int maxY, long hj) {
        int trunk = 4 + (int) ((hj >>> 48) % 3); // 4-6
        int lx = (int) ((hj >>> 52) % 3) - 1;
        int lz = (int) ((hj >>> 56) % 3) - 1;
        if (lx == 0 && lz == 0) {
            lx = 1;
        }
        if (y + trunk + 3 >= maxY) {
            return;
        }
        int tx = x;
        int tz = z;
        for (int i = 0; i < trunk; i++) {
            data.setBlock(tx, y + i, tz, Material.SPRUCE_LOG);
            if (i >= 2) {
                tx = clamp(tx + lx, 3, 12);
                tz = clamp(tz + lz, 3, 12);
            }
        }
        // Swept canopy: discs offset twice as far as the lean.
        int cx = clamp(x + lx * 2, 3, 12);
        int cz = clamp(z + lz * 2, 3, 12);
        int top = y + trunk;
        for (int dy = -1; dy <= 1; dy++) {
            int r = dy == 1 ? 1 : 3;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) == r && Math.abs(dz) == r) {
                        continue;
                    }
                    setLeaves(data, cx + dx, top + dy, cz + dz, spruceLeaves,
                            Material.SPRUCE_LEAVES);
                }
            }
        }
    }

    /** Dead snag: bare trunk with a few branch stubs, dead bush at the base. */
    private void placeDeadSnag(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z,
                               int maxY, long hj) {
        int trunk = 5 + (int) ((hj >>> 48) % 4); // 5-8
        if (y + trunk >= maxY) {
            return;
        }
        for (int i = 0; i < trunk; i++) {
            data.setBlock(x, y + i, z, Material.OAK_LOG);
        }
        // Two or three branch stubs pointing in hash directions.
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int branches = 2 + (int) ((hj >>> 60) & 1);
        for (int b = 0; b < branches; b++) {
            int[] d = dirs[(int) ((hj >>> (44 + b * 2)) & 3)];
            int by = y + 2 + (int) ((hj >>> (52 + b * 3)) % (trunk - 2));
            int len = 1 + (int) ((hj >>> (36 + b)) & 1);
            for (int i = 1; i <= len; i++) {
                int bx = x + d[0] * i;
                int bz = z + d[1] * i;
                if (bx < 1 || bx > 14 || bz < 1 || bz > 14) {
                    break;
                }
                if (data.getType(bx, by, bz) != Material.AIR) {
                    break;
                }
                data.setBlock(bx, by, bz, Material.OAK_LOG);
            }
        }
        if (x + 1 <= 14 && data.getType(x + 1, y, z) == Material.AIR) {
            data.setBlock(x + 1, y, z, Material.DEAD_BUSH);
        }
    }

    /** Palm: curving trunk with radiating fronds, roots at the waterline. */
    private void placePalm(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z,
                           int maxY, long hj) {
        // Trunk 7-9: keeps every frond within leaf-decay range of a log,
        // and fronds are persistent anyway as belt-and-braces.
        int trunk = 7 + (int) ((hj >>> 48) % 3); // 7-9
        int lx = (int) ((hj >>> 52) % 3) - 1;
        int lz = (int) ((hj >>> 56) % 3) - 1;
        if (lx == 0 && lz == 0) {
            lx = 1;
        }
        if (y + trunk + 3 >= maxY) {
            return;
        }
        int tx = x;
        int tz = z;
        for (int i = 0; i < trunk; i++) {
            data.setBlock(tx, y + i, tz, Material.JUNGLE_LOG);
            // Curve steepens with height.
            if (i * 2 > trunk) {
                tx = clamp(tx + lx, 2, 13);
                tz = clamp(tz + lz, 2, 13);
            }
        }
        int top = y + trunk;
        int[][] fronds = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] f : fronds) {
            setLeaves(data, tx + f[0], top + 1, tz + f[1], jungleLeaves, Material.JUNGLE_LEAVES);
        }
        setLeaves(data, tx, top + 1, tz, jungleLeaves, Material.JUNGLE_LEAVES);
        setLeaves(data, tx, top + 2, tz, jungleLeaves, Material.JUNGLE_LEAVES);
    }

    /** Ancient mega-tree: 2x2 trunk, towering canopy, a true landmark. */
    private void placeMegaTree(ChunkGenerator.@NotNull ChunkData data, int x, int y, int z,
                               int maxY, long seed, int wx, int wz) {
        int trunk = 18 + (int) (TerrainModel.hash2(seed ^ 0x9E9A71L, wz, wx) >>> 56) % 7; // 18-24
        if (y + trunk + 7 >= maxY || x + 1 > 15 || z + 1 > 15) {
            return;
        }
        for (int i = 0; i < trunk; i++) {
            data.setBlock(x, y + i, z, Material.OAK_LOG);
            data.setBlock(x + 1, y + i, z, Material.OAK_LOG);
            data.setBlock(x, y + i, z + 1, Material.OAK_LOG);
            data.setBlock(x + 1, y + i, z + 1, Material.OAK_LOG);
        }
        int top = y + trunk;
        int[][] layers = {{5, 2}, {4, 2}, {3, 2}, {2, 1}};
        int ly = top - 3;
        for (int[] layer : layers) {
            int r = layer[0];
            for (int i = 0; i < layer[1]; i++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) == r && Math.abs(dz) == r && ((dx + dz + i) & 1) == 0) {
                            continue;
                        }
                        setLeaves(data, x + dx, ly, z + dz, oakLeaves, Material.OAK_LEAVES);
                    }
                }
                ly++;
            }
        }
        setLeaves(data, x, ly, z, oakLeaves, Material.OAK_LEAVES);
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

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
