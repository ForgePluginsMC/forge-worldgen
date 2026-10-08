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
    private final SimplexNoise variant;

    public BiomeModel(long seed, @NotNull TerrainEngine terrain) {
        SeedManager seeds = new SeedManager(seed);
        this.terrain = terrain;
        this.temperature = new SimplexNoise(seeds.derive("biome.temperature"));
        this.humidity = new SimplexNoise(seeds.derive("biome.humidity"));
        this.mushroom = new SimplexNoise(seeds.derive("biome.mushroom"));
        this.variant = new SimplexNoise(seeds.derive("biome.variant"));
    }

    public @NotNull ForgeBiome biomeAt(int x, int z) {
        int h = terrain.heightAt(x, z);
        int sea = terrain.seaLevel();

        if (terrain.volcanoFactorAt(x, z) > 0.45) {
            return ForgeBiome.VOLCANIC;
        }
        double temp = temperature.fbm(x / 2048.0, z / 2048.0, 3, 2.0, 0.5);
        double humid = humidity.fbm(x / 2048.0, z / 2048.0, 3, 2.0, 0.5);
        double var = variant.fbm(x / 1024.0, z / 1024.0, 2, 2.0, 0.5);

        // Rivers (frozen in cold climates).
        if (terrain.riverCarveAt(x, z) > 0.55) {
            return temp < -0.3 ? ForgeBiome.FROZEN_RIVER : ForgeBiome.RIVER;
        }

        // Oceans: depth + temperature.
        if (h < sea) {
            if (terrain.isOceanAt(x, z)) {
                boolean deep = h < sea - 10;
                if (temp > 0.5) {
                    return deep ? ForgeBiome.DEEP_LUKEWARM_OCEAN : ForgeBiome.WARM_OCEAN;
                }
                if (temp > 0.2) {
                    return deep ? ForgeBiome.DEEP_LUKEWARM_OCEAN : ForgeBiome.LUKEWARM_OCEAN;
                }
                if (temp < -0.3) {
                    return deep ? ForgeBiome.DEEP_FROZEN_OCEAN : ForgeBiome.FROZEN_OCEAN;
                }
                if (temp < 0.0) {
                    return deep ? ForgeBiome.DEEP_COLD_OCEAN : ForgeBiome.COLD_OCEAN;
                }
                return deep ? ForgeBiome.DEEP_OCEAN : ForgeBiome.OCEAN;
            }
            // Lake — fall through to land biome classification.
        } else if (h <= sea + 1) {
            if (temp < -0.3) {
                return ForgeBiome.SNOWY_BEACH;
            }
            double mf = terrain.mountainFactorAt(x, z);
            if (mf > 0.3) {
                return ForgeBiome.STONY_SHORE;
            }
            return ForgeBiome.BEACH;
        }

        if (terrain.scablandFactorAt(x, z) > 0.5) {
            return ForgeBiome.SCABLAND;
        }

        // Mountains: elevation + temperature bands.
        double mountainFactor = terrain.mountainFactorAt(x, z);
        if (mountainFactor > 0.35) {
            if (h > 140) {
                if (temp < -0.2) return ForgeBiome.FROZEN_PEAKS;
                if (temp < 0.2) return ForgeBiome.JAGGED_PEAKS;
                return ForgeBiome.STONY_PEAKS;
            }
            if (h > 110) {
                if (temp < -0.2) return ForgeBiome.SNOWY_SLOPES;
                if (var > 0.3) return ForgeBiome.WINDSWEPT_FOREST;
                return ForgeBiome.WINDSWEPT_HILLS;
            }
            if (h > 85) {
                if (temp < -0.1) return ForgeBiome.GROVE;
                if (temp > 0.0 && temp < 0.4) return ForgeBiome.CHERRY_GROVE;
                return ForgeBiome.MEADOW;
            }
            // Foothills.
            if (var > 0.4) return ForgeBiome.WINDSWEPT_GRAVELLY_HILLS;
            return ForgeBiome.WINDSWEPT_HILLS;
        }

        // Rare special biomes.
        if (mushroom.fbm(x / 1024.0, z / 1024.0, 2, 2.0, 0.5) > 0.62) {
            return ForgeBiome.MUSHROOM_FIELDS;
        }
        if (temp < -0.4 && var > 0.55) {
            return ForgeBiome.ICE_SPIKES;
        }
        // Cave outcrops (rare surface exposures).
        if (var > 0.68 && h > sea + 5) {
            if (humid > 0.4) return ForgeBiome.LUSH_CAVES;
            if (temp > 0.3) return ForgeBiome.SULFUR_CAVES;
            return ForgeBiome.DRIPSTONE_CAVES;
        }
        if (var < -0.68 && h < sea - 5) {
            return ForgeBiome.DEEP_DARK;
        }

        // Wet lowlands.
        if (temp > 0.2 && humid > 0.6 && h < sea + 8) {
            return ForgeBiome.SWAMP;
        }
        if (temp > 0.3 && humid > 0.5 && h <= sea + 2) {
            return ForgeBiome.MANGROVE_SWAMP;
        }

        // Jungle family.
        if (temp > 0.6 && humid > 0.5) {
            if (var > 0.4) return ForgeBiome.BAMBOO_JUNGLE;
            if (var < -0.2) return ForgeBiome.SPARSE_JUNGLE;
            return ForgeBiome.JUNGLE;
        }

        // Badlands family.
        if (temp > 0.6 && humid < -0.4) {
            return ForgeBiome.ERODED_BADLANDS;
        }
        if (temp > 0.5 && humid < -0.3) {
            if (var > 0.3 && humid > -0.2) return ForgeBiome.WOODED_BADLANDS;
            return ForgeBiome.BADLANDS;
        }
        if (temp > 0.45 && humid < -0.1) {
            return ForgeBiome.DESERT;
        }

        // Savanna family.
        if (temp > 0.4 && humid > -0.2 && humid < 0.3) {
            if (h > 90) return ForgeBiome.SAVANNA_PLATEAU;
            if (var > 0.3) return ForgeBiome.WINDSWEPT_SAVANNA;
            return ForgeBiome.SAVANNA;
        }

        // Snowy.
        if (temp < -0.45) {
            return ForgeBiome.SNOWY_PLAINS;
        }

        // Taiga family.
        if (temp > -0.2 && temp < 0.3 && humid > 0.1) {
            if (var > 0.5) return ForgeBiome.OLD_GROWTH_PINE_TAIGA;
            if (var < -0.5) return ForgeBiome.OLD_GROWTH_SPRUCE_TAIGA;
            return ForgeBiome.TAIGA;
        }
        if (temp < -0.2 && humid > 0.1) {
            return ForgeBiome.SNOWY_TAIGA;
        }

        // Forest family.
        if (humid > 0.25) {
            if (temp > 0.0 && temp < 0.4 && humid > 0.55) {
                return ForgeBiome.DARK_FOREST;
            }
            if (temp > 0.1 && temp < 0.5) {
                if (var > 0.5) return ForgeBiome.OLD_GROWTH_BIRCH_FOREST;
                if (var < -0.3) return ForgeBiome.FLOWER_FOREST;
                return ForgeBiome.BIRCH_FOREST;
            }
            if (var > 0.6) return ForgeBiome.PALE_GARDEN;
            if (var < -0.6) return ForgeBiome.DAPPLED_FOREST;
            return ForgeBiome.FOREST;
        }

        // Plains family.
        if (var > 0.5 && temp > 0.1 && temp < 0.5) {
            return ForgeBiome.SUNFLOWER_PLAINS;
        }
        if (var > 0.3 && humid > 0.3) {
            return ForgeBiome.FLOWER_FOREST;
        }
        return ForgeBiome.PLAINS;
    }
}
