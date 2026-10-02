package com.forgeplugins.worldgen.gen;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of the generation settings in config.yml.
 */
public record GenSettings(
        int seaLevel,
        double volcanoRarity,
        double treeDensity,
        double mountainScale,
        boolean caves,
        boolean decorations,
        boolean structures,
        boolean mobs) {

    public static @NotNull GenSettings fromConfig(@NotNull FileConfiguration config) {
        return new GenSettings(
                config.getInt("generation.sea-level", 62),
                config.getDouble("generation.volcano-rarity", 0.1),
                config.getDouble("generation.tree-density", 1.0),
                config.getDouble("generation.mountain-scale", 1.0),
                config.getBoolean("features.caves", true),
                config.getBoolean("features.decorations", true),
                config.getBoolean("features.structures", true),
                config.getBoolean("features.mobs", true));
    }
}
