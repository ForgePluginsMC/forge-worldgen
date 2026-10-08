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
        if (h < sea - 10) {
            return ForgeBiome.DEEP_OCEAN;
        }
        if (h < sea) {
            return ForgeBiome.OCEAN;
        }
        if (h <= sea + 1) {
            return ForgeBiome.BEACH;
        }
        if (terrain.scablandFactorAt(x, z) > 0.5) {
            return ForgeBiome.SCABLAND;
        }
        if (terrain.riverCarveAt(x, z) > 0.55) {
            return ForgeBiome.RIVER;
        }
        if (h > 115) {
            return ForgeBiome.MOUNTAINS;
        }
        double temp = temperature.fbm(x / 2048.0, z / 2048.0, 3, 2.0, 0.5);
        double humid = humidity.fbm(x / 2048.0, z / 2048.0, 3, 2.0, 0.5);
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
