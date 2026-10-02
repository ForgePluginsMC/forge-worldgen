package com.forgeplugins.worldgen.gen;

import com.forgeplugins.worldgen.noise.SimplexNoise;

/**
 * The heart of ForgeWorldGen: turns world coordinates into terrain height,
 * volcano placement, and climate values.
 *
 * <p>All methods are allocation-free and thread-safe after construction, so a
 * single instance can be shared by parallel chunk-generation threads.
 */
public final class TerrainModel {

    /** Size of one volcano cell in blocks; at most one volcano per cell. */
    private static final int VOLCANO_CELL = 512;
    /** Volcano cone radius in blocks. */
    private static final double VOLCANO_RADIUS = 96.0;
    /** Extra height added at the centre of a volcano cone. */
    private static final double VOLCANO_HEIGHT = 78.0;

    private final long seed;
    private final int seaLevel;
    private final double volcanoRarity;
    private final double mountainScale;
    private final double mountainRarity;

    private final SimplexNoise continent;
    private final SimplexNoise mountains;
    private final SimplexNoise detail;
    private final SimplexNoise climate;

    public TerrainModel(long seed, int seaLevel, double volcanoRarity, double mountainScale,
                        double mountainRarity) {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.volcanoRarity = volcanoRarity;
        this.mountainScale = mountainScale;
        this.mountainRarity = mountainRarity;
        this.continent = new SimplexNoise(seed ^ 0xC0717E37L);
        this.mountains = new SimplexNoise(seed ^ 0xA017A15L);
        this.detail = new SimplexNoise(seed ^ 0xD37A11L);
        this.climate = new SimplexNoise(seed ^ 0xC11AA73L);
    }

    public long seed() {
        return seed;
    }

    public int seaLevel() {
        return seaLevel;
    }

    /**
     * Terrain height (top solid block Y) for a world column. Combines a
     * continental base, masked ridged mountains, rolling hills, river carving
     * and volcano cones. Result is clamped to sane world bounds by the caller.
     */
    public int heightAt(int x, int z) {
        double cont = continent.fbm(x * 0.00055, z * 0.00055, 4, 2.02, 0.5);
        double base = seaLevel + 4.0 + cont * 28.0;

        // Mountain ranges: rare regional bands, not the norm (Earth-like
        // distribution). Higher rarity lowers the mask threshold toward the
        // old always-mountainous behaviour; the default keeps most land
        // mellow and reserves high relief for occasional ranges.
        double threshold = 0.05 + (1.0 - mountainRarity) * 0.45;
        double maskField = mountains.fbm(x * 0.00011 + 100.0, z * 0.00011 - 100.0, 3, 2.0, 0.5);
        double mask = smoothstep(threshold, threshold + 0.4, maskField);

        double mountainH = 0.0;
        if (mask > 0.001) {
            // Sharp jagged ridgelines, not smooth domes: a strong primary
            // ridge plus a high-frequency crag octave cubed for knife-edge
            // crests (Growth reference terrain).
            double ridge = 1.0 - Math.abs(mountains.noise(x * 0.0016, z * 0.0016));
            double crag = 1.0 - Math.abs(mountains.noise(x * 0.006 + 40.0, z * 0.006 - 40.0));
            double sharp = Math.pow(ridge, 2.2) * 0.75 + Math.pow(crag, 3.0) * 0.35;
            mountainH = mask * sharp * 185.0 * mountainScale;
        }

        double hills = detail.fbm(x * 0.004, z * 0.004, 3, 2.1, 0.5) * 11.0 * (1.0 - mask * 0.7);

        // Rivers: V-shaped water-cut gorges. Narrow band, deep parabolic
        // carve so valleys read as carved by water, not just low noise.
        double riverBand = 1.0 - Math.abs(detail.fbm(x * 0.0009 + 500.0, z * 0.0009 - 500.0, 2, 2.0, 0.5));
        double carveT = 1.0 - smoothstep(0.008, 0.06, riverBand); // 1 at the gorge centreline
        double valley = 1.0 - smoothstep(6.0, 42.0, Math.abs(base - seaLevel));
        double carve = carveT * carveT * 22.0 * valley;

        double volcano = volcanoCone(x, z);

        return (int) Math.round(base + mountainH + hills - carve + volcano);
    }

    /**
     * Volcano cone height contribution at a world column, 0 when no volcano
     * is near. Deterministic per world seed.
     */
    public double volcanoCone(int x, int z) {
        if (volcanoRarity <= 0.0) {
            return 0.0;
        }
        int cellX = Math.floorDiv(x, VOLCANO_CELL);
        int cellZ = Math.floorDiv(z, VOLCANO_CELL);
        double best = 0.0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cellHash = hash2(seed ^ 0x001CA401L, cellX + dx, cellZ + dz);
                double roll = (cellHash >>> 11) * 0x1p-53; // [0,1)
                if (roll >= volcanoRarity) {
                    continue;
                }
                int originX = (cellX + dx) * VOLCANO_CELL;
                int originZ = (cellZ + dz) * VOLCANO_CELL;
                double cx = originX + ((cellHash >>> 21) * 0x1p-43) * VOLCANO_CELL;
                double cz = originZ + ((cellHash >>> 42) * 0x1p-22) * VOLCANO_CELL;
                double dist = Math.hypot(x - cx, z - cz);
                if (dist < VOLCANO_RADIUS) {
                    double t = 1.0 - dist / VOLCANO_RADIUS;
                    double cone = t * t * VOLCANO_HEIGHT;
                    if (cone > best) {
                        best = cone;
                    }
                }
            }
        }
        return best;
    }

    /**
     * Distance in blocks to the nearest volcano centre, or -1 when none is
     * within cone range. Used to paint caldera biomes and lava craters.
     */
    public double volcanoDistance(int x, int z) {
        if (volcanoRarity <= 0.0) {
            return -1.0;
        }
        int cellX = Math.floorDiv(x, VOLCANO_CELL);
        int cellZ = Math.floorDiv(z, VOLCANO_CELL);
        double best = Double.MAX_VALUE;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cellHash = hash2(seed ^ 0x001CA401L, cellX + dx, cellZ + dz);
                double roll = (cellHash >>> 11) * 0x1p-53;
                if (roll >= volcanoRarity) {
                    continue;
                }
                int originX = (cellX + dx) * VOLCANO_CELL;
                int originZ = (cellZ + dz) * VOLCANO_CELL;
                double cx = originX + ((cellHash >>> 21) * 0x1p-43) * VOLCANO_CELL;
                double cz = originZ + ((cellHash >>> 42) * 0x1p-22) * VOLCANO_CELL;
                double dist = Math.hypot(x - cx, z - cz);
                if (dist < VOLCANO_RADIUS && dist < best) {
                    best = dist;
                }
            }
        }
        return best == Double.MAX_VALUE ? -1.0 : best;
    }

    /**
     * Packed centre of the volcano nearest a column:
     * {@code (cx << 32) | (cz & 0xffffffffL)}. Only valid when
     * {@link #volcanoDistance(int, int)} is non-negative; both methods pick
     * the same nearest volcano, so the pairing is consistent.
     */
    public long volcanoCenter(int x, int z) {
        int cellX = Math.floorDiv(x, VOLCANO_CELL);
        int cellZ = Math.floorDiv(z, VOLCANO_CELL);
        double best = Double.MAX_VALUE;
        long packed = 0L;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long cellHash = hash2(seed ^ 0x001CA401L, cellX + dx, cellZ + dz);
                double roll = (cellHash >>> 11) * 0x1p-53; // [0,1)
                if (roll >= volcanoRarity) {
                    continue;
                }
                int originX = (cellX + dx) * VOLCANO_CELL;
                int originZ = (cellZ + dz) * VOLCANO_CELL;
                double cx = originX + ((cellHash >>> 21) * 0x1p-43) * VOLCANO_CELL;
                double cz = originZ + ((cellHash >>> 42) * 0x1p-22) * VOLCANO_CELL;
                double dist = Math.hypot(x - cx, z - cz);
                if (dist < VOLCANO_RADIUS && dist < best) {
                    best = dist;
                    packed = (((long) (int) cx) << 32) | ((int) cz & 0xffffffffL);
                }
            }
        }
        return packed;
    }

    /** Air temperature 0..1 at a column: climate noise minus altitude lapse. */    public double temperatureAt(int x, int z, int height) {
        double t = 0.55 + 0.45 * climate.fbm(x * 0.0004 + 1000.0, z * 0.0004 - 1000.0, 2, 2.0, 0.5);
        t -= Math.max(0, height - seaLevel) * 0.0038;
        return clamp01(t);
    }

    /** Moisture 0..1 at a column. */
    public double moistureAt(int x, int z) {
        return clamp01(0.5 + 0.5 * climate.fbm(x * 0.0005 - 1000.0, z * 0.0005 + 1000.0, 2, 2.0, 0.5));
    }

    /**
     * Low-frequency warp (in blocks) applied to sedimentary strata bands so
     * the banding undulates naturally instead of slicing perfectly level.
     */
    public double strataWarp(int x, int z) {
        return detail.fbm(x * 0.0021 + 900.0, z * 0.0021 - 900.0, 2, 2.0, 0.5) * 3.0;
    }

    /**
     * Deterministic per-column random in [0, 1). Used for tree/flora
     * placement so the world is stable across restarts.
     */
    public double columnRandom(int x, int z, long salt) {
        long h = hash2(seed ^ salt, x, z);
        return (h >>> 11) * 0x1p-53;
    }

    /**
     * 64-bit deterministic hash of a seed and two ints (splitmix64-style).
     * Allocation-free.
     */
    public static long hash2(long seed, int x, int z) {
        long h = seed + (long) x * 0x9E3779B97F4A7C15L + (long) z * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    private static double smoothstep(double edge0, double edge1, double v) {
        double t = clamp01((v - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
