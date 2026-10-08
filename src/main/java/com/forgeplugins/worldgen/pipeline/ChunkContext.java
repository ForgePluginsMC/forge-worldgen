package com.forgeplugins.worldgen.pipeline;

import com.forgeplugins.worldgen.biome.BiomeModel;
import com.forgeplugins.worldgen.config.GenConfig;
import com.forgeplugins.worldgen.terrain.TerrainEngine;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.jetbrains.annotations.NotNull;

/**
 * Everything a {@link GenStage} needs for one chunk: coordinates, the chunk
 * buffer, and the shared engines. Built fresh per chunk callback — stages
 * never share mutable state through it.
 */
public final class ChunkContext {

    public final int chunkX;
    public final int chunkZ;
    public final long seed;
    public final ChunkData data;
    public final TerrainEngine terrain;
    public final BiomeModel biomes;
    public final GenConfig config;

    public ChunkContext(int chunkX, int chunkZ, long seed, @NotNull ChunkData data,
                        @NotNull TerrainEngine terrain, @NotNull BiomeModel biomes,
                        @NotNull GenConfig config) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.seed = seed;
        this.data = data;
        this.terrain = terrain;
        this.biomes = biomes;
        this.config = config;
    }

    /** Minimum block Y of this chunk's world. */
    public int minY() {
        return data.getMinHeight();
    }

    /** Maximum block Y of this chunk's world. */
    public int maxY() {
        return data.getMaxHeight();
    }
}
