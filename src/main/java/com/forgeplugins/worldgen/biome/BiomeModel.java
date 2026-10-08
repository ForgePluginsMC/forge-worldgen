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
    private final SimplexNoise mushroom;

    public BiomeModel(long seed, @NotNull TerrainEngine terrain) {
        SeedManager seeds = new SeedManager(seed);
        this.terrain = terrain;
        this.temperature = new SimplexNoise(seeds.derive("biome.temperature"));
        this.humidity = new SimplexNoise(seeds.derive("biome.humidity"));
        this.mushroom = new SimplexNoise(seeds.derive("biome.mushroom"));
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
        double temp = temperature.fbm(x / 2048.0, z / 2048.0, 3, 2.0, 0.5);
        double humid = humidity.fbm(x / 2048.0, z / 2048.0, 3, 2.0, 0.5);
        // Oceans vs lakes: only true oceans (continental base below sea)
        // get ocean biomes. Inland lakes get the surrounding land biome.
        if (h < sea) {
            if (terrain.isOceanAt(x, z)) {
                if (h < sea - 10) {
                    return ForgeBiome.DEEP_OCEAN;
                }
                if (temp > 0.5) {
                    return ForgeBiome.WARM_OCEAN;
                }
                if (temp > 0.2) {
                    return ForgeBiome.LUKEWARM_OCEAN;
                }
                return ForgeBiome.OCEAN;
            }
            // Lake — fall through to land biome classification.
        } else if (h <= sea + 1) {
            double mf = terrain.mountainFactorAt(x, z);
            if (mf > 0.3 || temp < -0.2) {
                return ForgeBiome.STONY_SHORE;
            }
            return ForgeBiome.BEACH;
        }
        if (terrain.scablandFactorAt(x, z) > 0.5) {
            return ForgeBiome.SCABLAND;
        }
        // Mountains follow the relief mask that BUILDS them, not absolute
        // height. Height-based classification put plains biomes on mountain
        // terrain — "two different maps in one".
        double mountainFactor = terrain.mountainFactorAt(x, z);
        if (mountainFactor > 0.35) {
            if (temp < -0.3) {
                return ForgeBiome.FROZEN_PEAKS;
            }
            if (temp < -0.1) {
                return ForgeBiome.GROVE;
            }
            return ForgeBiome.MOUNTAINS;
        }
        // Rare mushroom island: isolated noise peak.
        if (mushroom.fbm(x / 1024.0, z / 1024.0, 2, 2.0, 0.5) > 0.62) {
            return ForgeBiome.MUSHROOM_FIELDS;
        }
        // Wet lowlands.
        if (temp > 0.2 && humid > 0.6 && h < sea + 8) {
            return ForgeBiome.SWAMP;
        }
        if (temp > 0.3 && humid > 0.5 && h <= sea + 2) {
            return ForgeBiome.MANGROVE_SWAMP;
        }
        // Hot & wet → jungle.
        if (temp > 0.6 && humid > 0.5) {
            return ForgeBiome.JUNGLE;
        }
        // Hot & dry → badlands/desert.
        if (temp > 0.6 && humid < -0.4) {
            return ForgeBiome.ERODED_BADLANDS;
        }
        if (temp > 0.5 && humid < -0.3) {
            return ForgeBiome.BADLANDS;
        }
        if (temp > 0.45 && humid < -0.1) {
            return ForgeBiome.DESERT;
        }
        // Cold.
        if (temp < -0.45) {
            return ForgeBiome.SNOWY;
        }
        if (temp < -0.2 && humid > 0.1) {
            return ForgeBiome.SNOWY_TAIGA;
        }
        // Cool forest.
        if (temp > -0.1 && temp < 0.3 && humid > 0.1) {
            return ForgeBiome.TAIGA;
        }
        // Temperate forests.
        if (temp > 0.0 && temp < 0.4 && humid > 0.55) {
            return ForgeBiome.DARK_FOREST;
        }
        if (temp > 0.1 && temp < 0.5 && humid > 0.2) {
            return ForgeBiome.BIRCH_FOREST;
        }
        // High meadows.
        if (h > 90 && h < 115 && temp > 0.0 && temp < 0.4) {
            return ForgeBiome.CHERRY_GROVE;
        }
        if (h > 85 && temp > -0.1 && temp < 0.3) {
            return ForgeBiome.MEADOW;
        }
        // Default forest/plains.
        if (humid > 0.25) {
            return ForgeBiome.FOREST;
        }
        return ForgeBiome.PLAINS;
    }
}
