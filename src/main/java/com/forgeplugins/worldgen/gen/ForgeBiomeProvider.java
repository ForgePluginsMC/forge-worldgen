package com.forgeplugins.worldgen.gen;

import com.forgeplugins.worldgen.biome.BiomeModel;
import com.forgeplugins.worldgen.biome.ForgeBiome;
import java.util.List;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Supplies our biomes to vanilla systems. The y coordinate is ignored —
 * v3 biomes are column-based — and every answer is a vanilla derivative,
 * so decorations, structures and mob spawning behave normally.
 */
public final class ForgeBiomeProvider extends BiomeProvider {

    private final BiomeModel biomes;

    public ForgeBiomeProvider(@NotNull BiomeModel biomes) {
        this.biomes = biomes;
    }

    @Override
    public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
        return biomes.biomeAt(x, z).vanilla();
    }

    @Override
    public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
        return ForgeBiome.vanillaList();
    }
}
