package com.forgeplugins.worldgen.terrain;

import com.forgeplugins.worldgen.config.GenConfig;
import com.forgeplugins.worldgen.noise.SimplexNoise;
import com.forgeplugins.worldgen.seed.SeedManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ForgeWorldGen v3 heightfield — pure functions of world coordinates and
 * seed. Chunk borders always agree; output is deterministic per seed.
 *
 * <p>Layers, applied in order:
 * <ol>
 *   <li>continental base + hills (rolling plains)</li>
 *   <li>mountain ranges — ridged noise masked to rare regions</li>
 *   <li>volcanoes — jittered-grid cones with crater bowls and lava lakes</li>
 *   <li>rivers — zero-crossing valley carve</li>
 *   <li>scabland coulees — channels carved into plateau country</li>
 * </ol>
 */
public final class TerrainEngine {

    /** Volcano placement grid cell, in blocks. */
    private static final double VOLCANO_CELL = 1536.0;

    private final long seed;
    private final GenConfig config;
    private final long volcanoSeed;

    private final SimplexNoise continental;
    private final SimplexNoise hills;
    private final SimplexNoise relief;
    private final SimplexNoise rivers;
    private final SimplexNoise scab;
    private final SimplexNoise plateau;

    public TerrainEngine(long seed, @NotNull GenConfig config) {
        SeedManager seeds = new SeedManager(seed);
        this.seed = seed;
        this.config = config;
        this.volcanoSeed = seeds.derive("terrain.volcano");
        this.continental = new SimplexNoise(seeds.derive("terrain.continental"));
        this.hills = new SimplexNoise(seeds.derive("terrain.hills"));
        this.relief = new SimplexNoise(seeds.derive("terrain.relief"));
        this.rivers = new SimplexNoise(seeds.derive("terrain.rivers"));
        this.scab = new SimplexNoise(seeds.derive("terrain.scabland"));
        this.plateau = new SimplexNoise(seeds.derive("terrain.plateau"));
    }

    public int seaLevel() {
        return config.seaLevel();
    }

    /** Full terrain height including the volcano cones. */
    public int heightAt(int x, int z) {
        double h = baseHeightAt(x, z);
        Volcano v = strongestVolcano(x, z);
        if (v != null) {
            h += v.height * Math.pow(v.factor, 1.5);
            if (v.factor > 0.78) {
                h -= (v.factor - 0.78) * v.radius * 1.1; // crater bowl
            }
        }
        return (int) Math.round(h);
    }

    /** Height without volcanoes — the ground the cones sit on. */
    double baseHeightAt(int x, int z) {
        double cont = continental.fbm(x / 4096.0, z / 4096.0, 4, 2.0, 0.5);
        double hill = hills.fbm(x / 768.0, z / 768.0, 4, 2.0, 0.5);
        double h = config.seaLevel() + 3.0 + cont * 16.0 + hill * 13.0;

        // Mountain ranges where the relief mask runs high. The ridge profile
        // is deliberately rounded (pow 0.75) — sharp knife-edge crests looked
        // wrong — and the massif has a solid base lift, not just thin peaks.
        double mask = relief.fbm(x / 2048.0, z / 2048.0, 3, 2.0, 0.5);
        double m = sstep(0.22, 0.60, mask);
        double ridge = 1.0 - Math.abs(relief.noise(x / 420.0, z / 420.0));
        ridge = Math.pow(ridge, 0.75);
        h += m * m * (0.30 + 0.70 * ridge) * 150.0 * config.mountainAmp();

        // Fine detail.
        h += hills.noise(x / 24.0, z / 24.0) * 2.5;

        // Rugged medium-frequency detail — gated to land so the ocean cliffs
        // keep their character. This is what breaks the "too smooth" look.
        double rugged = relief.fbm(x / 96.0, z / 96.0, 3, 2.0, 0.5);
        double landGate = sstep(config.seaLevel() - 6.0, config.seaLevel() + 2.0, h);
        h += rugged * 9.0 * config.ruggedness() * (0.15 + 0.85 * Math.max(m, landGate));

        // Rivers carve valleys.
        if (config.rivers()) {
            double carve = riverCarveAt(x, z);
            h -= carve * carve * 10.0 * (1.0 - 0.6 * m);
        }

        // Scabland coulees on plateau country.
        if (config.scablands()) {
            h -= scablandFactorAt(x, z) * 26.0;
        }
        return h;
    }

    /** 0..1 mountain-massif strength at a column (the relief mask that builds the ranges). */
    public double mountainFactorAt(int x, int z) {
        double mask = relief.fbm(x / 2048.0, z / 2048.0, 3, 2.0, 0.5);
        return sstep(0.22, 0.60, mask);
    }

    /**
     * True if this column is in a true ocean (continental base below sea
     * level), as opposed to an inland lake (a hill depression in land).
     * Lakes get land biomes; only oceans get ocean biomes.
     */
    public boolean isOceanAt(int x, int z) {
        double cont = continental.fbm(x / 4096.0, z / 4096.0, 4, 2.0, 0.5);
        return config.seaLevel() + 3.0 + cont * 16.0 < config.seaLevel();
    }

    /** Fine-detail noise for surface variation (ash fields, scree, basalt flats). */
    public double surfaceVariationAt(int x, int z) {
        return hills.noise(x / 24.0, z / 24.0);
    }

    /** 0..1 river-valley carve strength at a column. */
    public double riverCarveAt(int x, int z) {
        double r = Math.abs(rivers.noise(x / 900.0, z / 900.0));
        return sstep(0.0, 1.0, 1.0 - r * 9.0);
    }

    /** 0..1 scabland-channel strength at a column. */
    public double scablandFactorAt(int x, int z) {
        double plat = sstep(0.15, 0.45, plateau.fbm(x / 4096.0, z / 4096.0, 3, 2.0, 0.5));
        if (plat <= 0.0) {
            return 0.0;
        }
        double c = 1.0 - Math.abs(scab.noise(x / 1400.0, z / 1400.0));
        return plat * sstep(0.90, 0.97, c);
    }

    /** 0..1 volcano proximity at a column (1 at a cone's center). */
    public double volcanoFactorAt(int x, int z) {
        Volcano v = strongestVolcano(x, z);
        return v == null ? 0.0 : v.factor;
    }

    /**
     * Lava-lake surface Y for a crater column, or -1 when there is no lava
     * here. Always below the crater rim (see height math above).
     */
    public int lavaLevelAt(int x, int z) {
        Volcano v = strongestVolcano(x, z);
        if (v == null || v.factor <= 0.80) {
            return -1;
        }
        return (int) Math.round(baseHeightAt(x, z) + v.height * 0.55);
    }

    private record Volcano(double factor, double radius, double height) {}

    private @Nullable Volcano strongestVolcano(int x, int z) {
        if (config.volcanoRarity() <= 0.0) {
            return null;
        }
        long ccx = (long) Math.floor(x / VOLCANO_CELL);
        long ccz = (long) Math.floor(z / VOLCANO_CELL);
        Volcano best = null;
        for (long dx = -1; dx <= 1; dx++) {
            for (long dz = -1; dz <= 1; dz++) {
                double[] c = volcanoCenterInCell(ccx + dx, ccz + dz);
                if (c == null) {
                    continue;
                }
                double d = Math.hypot(x - c[0], z - c[1]);
                if (d < c[2]) {
                    double f = 1.0 - d / c[2];
                    if (best == null || f > best.factor) {
                        best = new Volcano(f, c[2], c[3]);
                    }
                }
            }
        }
        return best;
    }

    /**
     * Volcano cone center for a grid cell, or null when the cell holds none.
     * Returns {x, z, radius, height}.
     */
    private @Nullable double[] volcanoCenterInCell(long cx, long cz) {
        long h1 = cellHash(cx, cz, 1);
        if (toUnit(h1) >= config.volcanoRarity()) {
            return null;
        }
        long h2 = cellHash(cx, cz, 2);
        long h3 = cellHash(cx, cz, 3);
        double jx = (toUnit(h2) - 0.5) * VOLCANO_CELL * 0.6;
        double jz = (toUnit(h3) - 0.5) * VOLCANO_CELL * 0.6;
        double radius = 150.0 + toUnit(h2 ^ h3) * 90.0;
        double height = 85.0 + toUnit(h3 ^ h1) * 65.0;
        return new double[] {
            cx * VOLCANO_CELL + VOLCANO_CELL / 2.0 + jx,
            cz * VOLCANO_CELL + VOLCANO_CELL / 2.0 + jz,
            radius, height
        };
    }

    private long cellHash(long cx, long cz, long salt) {
        long h = volcanoSeed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xBF58476D1CE4E5B9L) ^ (salt * 0x94D049BB133111EBL);
        return SeedManager.mix64(h);
    }

    private static double toUnit(long h) {
        return (h >>> 11) * 0x1p-53;
    }

    private static double sstep(double e0, double e1, double x) {
        double t = Math.clamp((x - e0) / (e1 - e0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
