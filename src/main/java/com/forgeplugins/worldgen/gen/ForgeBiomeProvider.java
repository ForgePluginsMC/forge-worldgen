package com.forgeplugins.worldgen.gen;

import java.util.List;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Climate-driven biome provider. Biomes are chosen from continuous
 * temperature/moisture/altitude fields plus large-scale region territories,
 * so borders follow smooth contour lines instead of hard chunk-aligned edges.
 */
public final class ForgeBiomeProvider extends BiomeProvider {

    private static final List<Biome> ALL = List.of(
            Biome.WINDSWEPT_HILLS,
            Biome.FROZEN_PEAKS,
            Biome.MEADOW,
            Biome.DARK_FOREST,
            Biome.OLD_GROWTH_PINE_TAIGA,
            Biome.SAVANNA,
            Biome.DESERT,
            Biome.FLOWER_FOREST,
            Biome.BEACH,
            Biome.STONY_PEAKS,
            Biome.PLAINS,
            Biome.SAVANNA_PLATEAU,
            Biome.DEEP_OCEAN,
            Biome.BADLANDS);

    private final TerrainModel terrain;

    public ForgeBiomeProvider(TerrainModel terrain) {
        this.terrain = terrain;
    }

    @Override
    public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        return pick(worldInfo, x, z).vanilla();
    }

    /**
     * Picks the ForgeWorldGen biome for a world column. Shared with the chunk
     * generator so terrain, surface blocks and biome colour always agree.
     */
    public @NotNull ForgeBiome pick(@NotNull WorldInfo worldInfo, int x, int z) {
        int height = terrain.heightAt(x, z);
        int hx = terrain.heightAt(x + 3, z);
        int hz = terrain.heightAt(x, z + 3);
        double slope = (Math.abs(hx - height) + Math.abs(hz - height)) / 6.0;
        return pick(worldInfo, x, z, height, slope);
    }

    /**
     * Biome pick with precomputed height and slope, so the chunk generator
     * can reuse the values it already measured instead of re-sampling.
     */
    public @NotNull ForgeBiome pick(@NotNull WorldInfo worldInfo, int x, int z,
                                    int height, double slope) {
        int sea = terrain.seaLevel();

        double volcanoDist = terrain.volcanoDistance(x, z);
        if (volcanoDist >= 0.0 && volcanoDist < 110.0) {
            return ForgeBiome.ASHEN_CALDERA;
        }

        // Special territories first: they override climate.
        TerrainModel.Region region = terrain.regionAt(x, z);
        if (region == TerrainModel.Region.PAINTED_CANYON) {
            return ForgeBiome.PAINTED_CANYON;
        }
        if (region == TerrainModel.Region.BARREN_WASTELAND) {
            return ForgeBiome.BARREN_WASTELAND;
        }
        if (region == TerrainModel.Region.DUNE_SEA) {
            return ForgeBiome.DUNE_SEA;
        }
        if (region == TerrainModel.Region.GEYSER_BASIN) {
            return ForgeBiome.GEYSER_BASIN;
        }
        if (terrain.graniteMaskAt(x, z) > 0.45) {
            return ForgeBiome.GRANITE_VALLEY;
        }
        if (terrain.highPlainsMaskAt(x, z) > 0.5 && height > sea + 8) {
            return ForgeBiome.HIGH_PLAINS;
        }

        if (height < sea - 12) {
            return ForgeBiome.DEEP_OCEAN;
        }
        if (height >= sea - 1 && height <= sea + 1) {
            return ForgeBiome.TIDEWATER_SHALLOWS; // beach band, palms at the waterline
        }
        if (height < sea - 1) {
            return ForgeBiome.TIDEWATER_SHALLOWS; // shallow sea
        }
        if (height >= sea + 92) {
            return ForgeBiome.FROSTCAP_PEAKS;
        }

        // Slope from finite differences; steep high ground becomes highlands.
        if (slope > 0.85 && height > sea + 18) {
            return ForgeBiome.EMBER_HIGHLANDS;
        }

        double temp = terrain.temperatureAt(x, z, height);
        double moist = terrain.moistureAt(x, z);

        if (temp > 0.74 && moist < 0.34) {
            return ForgeBiome.SUNBAKED_DUNES;
        }
        if (temp > 0.66 && moist < 0.45) {
            return ForgeBiome.GOLDEN_SAVANNA;
        }
        if (temp < 0.44) {
            return moist > 0.48 ? ForgeBiome.WHISPERING_PINES : ForgeBiome.EMBER_HIGHLANDS;
        }
        if (moist > 0.66) {
            return temp < 0.72 && moist > 0.78 ? ForgeBiome.BLOOMING_MEADOW : ForgeBiome.MISTWOOD;
        }
        if (moist < 0.32) {
            return ForgeBiome.GOLDEN_SAVANNA;
        }
        if (moist > 0.55) {
            return ForgeBiome.VERDANT_VALE;
        }
        return ForgeBiome.ROLLING_PLAINS;
    }

    @Override
    public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return ALL;
    }
}
