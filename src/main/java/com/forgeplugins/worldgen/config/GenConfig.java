package com.forgeplugins.worldgen.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Validated generation config — the single source of truth for v3 tuning
 * (the Iris "one annotated model class" idea, kept deliberately small:
 * validation lives here, nowhere else).
 *
 * <p>Immutable snapshot; the generator rebuilds its engines when this
 * changes via {@code /fgen reload}.
 */
public record GenConfig(
        int seaLevel,
        double mountainAmp,
        double volcanoRarity,
        boolean rivers,
        boolean scablands,
        boolean caves,
        boolean decorations,
        boolean structures,
        boolean mobs) {

    public static @NotNull GenConfig fromConfig(@NotNull FileConfiguration config) {
        int seaLevel = config.getInt("generation.sea-level", 62);
        if (seaLevel < 0 || seaLevel > 200) {
            throw new IllegalArgumentException(
                    "generation.sea-level must be 0-200, was " + seaLevel);
        }
        double mountainAmp = config.getDouble("terrain.mountain-amplification", 1.0);
        if (mountainAmp < 0.0 || mountainAmp > 2.5) {
            throw new IllegalArgumentException(
                    "terrain.mountain-amplification must be 0.0-2.5, was " + mountainAmp);
        }
        double volcanoRarity = config.getDouble("terrain.volcano-rarity", 0.06);
        if (volcanoRarity < 0.0 || volcanoRarity > 1.0) {
            throw new IllegalArgumentException(
                    "terrain.volcano-rarity must be 0.0-1.0, was " + volcanoRarity);
        }
        return new GenConfig(
                seaLevel,
                mountainAmp,
                volcanoRarity,
                config.getBoolean("terrain.rivers", true),
                config.getBoolean("terrain.scablands", true),
                config.getBoolean("features.caves", true),
                config.getBoolean("features.decorations", true),
                config.getBoolean("features.structures", true),
                config.getBoolean("features.mobs", true));
    }
}
