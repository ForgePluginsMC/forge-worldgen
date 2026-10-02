package com.forgeplugins.worldgen.gen;

import com.forgeplugins.worldgen.noise.SimplexNoise;

/**
 * ForgeWorldGen v2 terrain core — deliberately vanilla in character.
 *
 * <p>Honest design note: a Paper plugin cannot reach inside Mojang's
 * generator to "smooth" it, so v2 implements its own heightfield tuned to
 * vanilla's look and proportions instead: mostly mellow rolling plains and
 * hills, occasional mountain ranges, oceans and rivers. A single gentle
 * low-pass smooths the result, controlled by one config value
 * ({@code terrain.smoothing}).
 *
 * <p>Everything here is a pure function of world coordinates and the seed:
 * chunk borders always agree and output is deterministic per seed. The whole
 * class is small enough to read in one sitting — that is the point of v2.
 */
public final class TerrainModel {

    private final long seed;
    private final int seaLevel;
    private final double smoothing;

    private final SimplexNoise continental;
    private final SimplexNoise hills;
    private final SimplexNoise relief;
    private final SimplexNoise rivers;

    public TerrainModel(long seed, int seaLevel, double smoothing) {
        this.seed = seed;
        this.seaLevel = seaLevel;
        this.smoothing = Math.clamp(smoothing, 0.0, 1.0);
        this.continental = new SimplexNoise(seed ^ 0xC0714E71L);
        this.hills = new SimplexNoise(seed ^ 0xB1115EEDL);
        this.relief = new SimplexNoise(seed ^ 0x2E11EF7L);
        this.rivers = new SimplexNoise(seed ^ 0x21E25L);
    }

    public long seed() {
        return seed;
    }

    public int seaLevel() {
        return seaLevel;
    }

    /**
     * Raw (unsmoothed) terrain height at a world column: continental base
     * plus gentle hills, rare mountain ranges, and carved rivers.
     */
    public int rawHeightAt(int x, int z) {
        return (int) Math.round(rawHeightD(x, z));
    }

    /** Unrounded raw height; the smoothing stage works on doubles so the
     * blur can actually move the needle on gentle slopes. */
    private double rawHeightD(int x, int z) {
        double cont = continental.fbm(x / 4096.0, z / 4096.0, 4, 2.0, 0.5);
        double hill = hills.fbm(x / 768.0, z / 768.0, 4, 2.0, 0.5);

        // Mountains only where a low-frequency mask runs high: rare regional
        // ranges, mellow land everywhere else.
        double mask = relief.fbm(x / 2048.0, z / 2048.0, 3, 2.0, 0.5);
        double m = Math.clamp((mask - 0.45) / 0.55, 0.0, 1.0);
        double ridge = 1.0 - Math.abs(relief.noise(x / 320.0, z / 320.0));
        double mountainH = m * m * (0.35 + 0.65 * ridge) * 130.0;

        double h = seaLevel + 3.0 + cont * 16.0 + hill * 13.0 + mountainH;

        // Fine detail: small-scale roughness the smoothing stage can tame.
        h += hills.noise(x / 24.0, z / 24.0) * 2.5;

        // Rivers: a shallow channel carved where the river noise crosses zero.
        double r = Math.abs(rivers.noise(x / 900.0, z / 900.0));
        double carve = Math.clamp(1.0 - r * 9.0, 0.0, 1.0);
        h -= carve * carve * 9.0 * (1.0 - 0.7 * m);

        return h;
    }

    /**
     * Smoothed height: the raw height blended with a 5x5 box blur.
     * {@code smoothing} 0.0 disables it; the default is subtle.
     */
    public int heightAt(int x, int z) {
        double raw = rawHeightD(x, z);
        if (smoothing <= 0.0) {
            return (int) Math.round(raw);
        }
        double blur = 0.0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                blur += rawHeightD(x + dx, z + dz);
            }
        }
        blur /= 25.0;
        return (int) Math.round(raw * (1.0 - smoothing) + blur * smoothing);
    }
}
