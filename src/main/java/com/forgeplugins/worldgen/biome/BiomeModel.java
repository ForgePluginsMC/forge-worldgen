package com.forgeplugins.worldgen.biome;

import com.forgeplugins.worldgen.noise.SimplexNoise;
import com.forgeplugins.worldgen.seed.SeedManager;
import com.forgeplugins.worldgen.terrain.TerrainEngine;
import org.jetbrains.annotations.NotNull;

/**
 * Selects a {@link ForgeBiome} for a world column — a pure function of
 * coordinates and seed (temperature/humidity noise plus the terrain engine's
 * height, volcano, river and scabland fields).
 */
public final class BiomeModel {

    private final TerrainEngine terrain;
    private final SimplexNoise temperature;
    private final SimplexNoise humidity;

    public BiomeModel(long seed, @NotNull TerrainEngine terrain) {
        SeedManager seeds = new SeedManager(seed);
        this.terrain = terrain;
        this.temperature = new SimplexNoise(seeds.derive("biome.temperature"));
        this.humidity = new SimplexNoise(seeds.derive("biome.humidity"));
    }

    public @NotNull ForgeBiome biomeAt(int x, int z) {
        int h = terrain.heightAt(x, z);
        int sea = terrain.seaLevel();

        if (terrain.volcanoFactorAt(x, z) > 0.45) {
            return ForgeBiome.VOLCANIC;
        }
        // Rivers before oceans — a deep river channel is still a river,
        // not a deep ocean (otherwise river canyons get ocean water/kelp).
        if (terrain.riverCarveAt(x, z) > 0.55) {
            return ForgeBiome.RIVER;
        }
        // Oceans vs lakes: only true oceans (continental base below sea)
        // get ocean biomes. Inland lakes get the surrounding land biome —
        // otherwise lakes get deep-ocean water color ("oceans under land").
        if (h < sea) {
            if (terrain.isOceanAt(x, z)) {
                return h < sea - 10 ? ForgeBiome.DEEP_OCEAN : ForgeBiome.OCEAN;
            }
            // Lake — fall through to land biome classification.
        } else if (h <= sea + 1) {
            return ForgeBiome.BEACH;
        }
        if (terrain.scablandFactorAt(x, z) > 0.5) {
            return ForgeBiome.SCABLAND;
        }
        double temp = temperature.fbm(x / 2048.0, z / 2048.0, 3, 2.0, 0.5);
        double humid = humidity.fbm(x / 2048.0, z / 2048.0, 3, 2.0, 0.5);
        // Mountains follow the relief mask that BUILDS them, not absolute
        // height. Height-based classification put plains biomes on mountain
        // terrain — "two different maps in one".
        if (terrain.mountainFactorAt(x, z) > 0.35) {
            return temp < -0.2 ? ForgeBiome.SNOWY : ForgeBiome.MOUNTAINS;
        }
        if (temp < -0.45) {
            return ForgeBiome.SNOWY;
        }
        if (temp > 0.45 && humid < -0.1) {
            return ForgeBiome.DESERT;
        }
        if (humid > 0.25) {
            return ForgeBiome.FOREST;
        }
        return ForgeBiome.PLAINS;
    }
}
