package com.forgeplugins.worldgen.gen;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of the generation settings in config.yml.
 * Deliberately minimal: v2 changes one thing (smoothing) and leaves the
 * rest to vanilla.
 */
public record GenSettings(
        int seaLevel,
        double smoothing,
        boolean caves,
        boolean decorations,
        boolean structures,
        boolean mobs) {

    public static @NotNull GenSettings fromConfig(@NotNull FileConfiguration config) {
        return new GenSettings(
                config.getInt("generation.sea-level", 62),
                Math.clamp(config.getDouble("terrain.smoothing", 0.35), 0.0, 1.0),
                config.getBoolean("features.caves", true),
                config.getBoolean("features.decorations", true),
                config.getBoolean("features.structures", true),
                config.getBoolean("features.mobs", true));
    }
}
