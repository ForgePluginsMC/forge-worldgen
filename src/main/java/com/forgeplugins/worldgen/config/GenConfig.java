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
        double ruggedness,
        boolean rivers,
        boolean scablands,
        boolean customTrees,
        double treeDensity,
        boolean decorations,
        double decorDensity,
        boolean caves,
        boolean vanillaDecorations,
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
        double ruggedness = config.getDouble("terrain.ruggedness", 1.0);
        if (ruggedness < 0.0 || ruggedness > 2.0) {
            throw new IllegalArgumentException(
                    "terrain.ruggedness must be 0.0-2.0, was " + ruggedness);
        }
        double treeDensity = config.getDouble("vegetation.tree-density", 1.0);
        if (treeDensity < 0.0 || treeDensity > 3.0) {
            throw new IllegalArgumentException(
                    "vegetation.tree-density must be 0.0-3.0, was " + treeDensity);
        }
        double decorDensity = config.getDouble("vegetation.decor-density", 1.0);
        if (decorDensity < 0.0 || decorDensity > 3.0) {
            throw new IllegalArgumentException(
                    "vegetation.decor-density must be 0.0-3.0, was " + decorDensity);
        }
        return new GenConfig(
                seaLevel,
                mountainAmp,
                volcanoRarity,
                ruggedness,
                config.getBoolean("terrain.rivers", true),
                config.getBoolean("terrain.scablands", true),
                config.getBoolean("vegetation.custom-trees", true),
                treeDensity,
                config.getBoolean("vegetation.decorations", true),
                decorDensity,
                config.getBoolean("features.caves", true),
                config.getBoolean("features.decorations", true),
                config.getBoolean("features.structures", true),
                config.getBoolean("features.mobs", true));
    }
}
