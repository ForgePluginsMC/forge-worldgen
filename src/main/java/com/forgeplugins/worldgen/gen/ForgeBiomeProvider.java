package com.forgeplugins.worldgen.gen;

import java.util.List;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Climate-driven biome provider. Biomes are chosen from continuous
 * temperature/moisture/altitude fields, so borders follow smooth contour
 * lines instead of hard chunk-aligned edges.
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
            Biome.OCEAN,
            Biome.STONY_PEAKS);

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
        int sea = terrain.seaLevel();

        double volcanoDist = terrain.volcanoDistance(x, z);
        if (volcanoDist >= 0.0 && volcanoDist < 110.0) {
            return ForgeBiome.ASHEN_CALDERA;
        }

        if (height < sea - 2) {
            return ForgeBiome.TIDEWATER_SHALLOWS;
        }
        if (height >= sea + 92) {
            return ForgeBiome.FROSTCAP_PEAKS;
        }

        // Slope from finite differences; steep high ground becomes highlands.
        int hx = terrain.heightAt(x + 3, z);
        int hz = terrain.heightAt(x, z + 3);
        double slope = (Math.abs(hx - height) + Math.abs(hz - height)) / 6.0;
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
        return ForgeBiome.VERDANT_VALE;
    }

    @Override
    public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return ALL;
    }
}
