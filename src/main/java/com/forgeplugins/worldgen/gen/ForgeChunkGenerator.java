package com.forgeplugins.worldgen.gen;

import com.forgeplugins.worldgen.biome.BiomeModel;
import com.forgeplugins.worldgen.biome.ForgeBiome;
import com.forgeplugins.worldgen.config.GenConfig;
import com.forgeplugins.worldgen.pipeline.ChunkContext;
import com.forgeplugins.worldgen.pipeline.GenPipeline;
import com.forgeplugins.worldgen.stage.SurfaceStage;
import com.forgeplugins.worldgen.stage.TerrainStage;
import com.forgeplugins.worldgen.terrain.TerrainEngine;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * ForgeWorldGen v3 generator — a staged pipeline over a deterministic
 * terrain engine, instead of v2's single heightfield method.
 *
 * <p>Noise stages run in {@link #generateNoise}; the surface stage runs in
 * {@link #generateSurface} (after vanilla's surface step, which it
 * repaints). Biomes come from our own {@link ForgeBiomeProvider}, each
 * mapped to a vanilla derivative so vanilla decorations, structures and
 * mob spawning keep working. Caves, decorations, structures and mobs stay
 * vanilla, toggled by config.
 */
public final class ForgeChunkGenerator extends ChunkGenerator {

    /** Everything built for one world seed. Engines are immutable. */
    private record EngineSet(
            TerrainEngine terrain,
            BiomeModel biomes,
            GenPipeline pipeline,
            ForgeBiomeProvider provider) {}

    private final Object engineLock = new Object();
    private final Map<Long, EngineSet> engines = new HashMap<>();
    private volatile GenConfig config;

    public ForgeChunkGenerator(@NotNull GenConfig config) {
        this.config = config;
    }

    /** Applies new config (e.g. after /fgen reload); engines rebuild lazily. */
    public void updateConfig(@NotNull GenConfig next) {
        synchronized (engineLock) {
            this.config = next;
            this.engines.clear();
        }
    }

    private @NotNull EngineSet enginesFor(long seed) {
        EngineSet set = engines.get(seed);
        if (set == null) {
            synchronized (engineLock) {
                set = engines.get(seed);
                if (set == null) {
                    GenConfig snap = config;
                    TerrainEngine terrain = new TerrainEngine(seed, snap);
                    BiomeModel biomes = new BiomeModel(seed, terrain);
                    GenPipeline pipeline = new GenPipeline(
                            List.of(new TerrainStage()),
                            List.of(new SurfaceStage()));
                    set = new EngineSet(terrain, biomes, pipeline,
                            new ForgeBiomeProvider(biomes));
                    engines.put(seed, set);
                }
            }
        }
        return set;
    }

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random,
                              int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        EngineSet set = enginesFor(seed);
        set.pipeline().runNoise(new ChunkContext(chunkX, chunkZ, seed, chunkData,
                set.terrain(), set.biomes(), config));
    }

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random,
                                int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        long seed = worldInfo.getSeed();
        EngineSet set = enginesFor(seed);
        set.pipeline().runSurface(new ChunkContext(chunkX, chunkZ, seed, chunkData,
                set.terrain(), set.biomes(), config));
    }

    @Override
    public @Nullable BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return enginesFor(worldInfo.getSeed()).provider();
    }

    /**
     * Determinism proof (the Iris goldenhash idea, simplified): SHA-256 over
     * a grid of heights + biome ordinals at fixed coordinates. The same seed
     * always yields the same hash — on any server, any run.
     */
    public @NotNull String verifyHash(long seed, int halfExtentBlocks) {
        EngineSet set = enginesFor(seed);
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            ByteBuffer buf = ByteBuffer.allocate(16);
            for (int dz = -halfExtentBlocks; dz <= halfExtentBlocks; dz += 4) {
                for (int dx = -halfExtentBlocks; dx <= halfExtentBlocks; dx += 4) {
                    int h = set.terrain().heightAt(dx, dz);
                    int b = set.biomes().biomeAt(dx, dz).ordinal();
                    buf.clear();
                    sha.update(buf.putInt(dx).putInt(dz).putInt(h).putInt(b).array());
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte by : sha.digest()) {
                hex.append(String.format("%02x", by));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Biome lookup for commands (e.g. /fgen biome). */
    public @NotNull ForgeBiome biomeAt(long seed, int x, int z) {
        return enginesFor(seed).biomes().biomeAt(x, z);
    }

    /** Terrain height lookup for commands. */
    public int heightAt(long seed, int x, int z) {
        return enginesFor(seed).terrain().heightAt(x, z);
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return true;
    }

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        // Pleasant low land near the origin for first join.
        TerrainEngine terrain = enginesFor(world.getSeed()).terrain();
        int sea = terrain.seaLevel();
        for (int ring = 0; ring < 12; ring++) {
            for (int dx = -ring * 8; dx <= ring * 8; dx += 8) {
                for (int dz = -ring * 8; dz <= ring * 8; dz += 8) {
                    int x = 8 + dx;
                    int z = 8 + dz;
                    int h = terrain.heightAt(x, z);
                    if (h > sea + 1 && h < sea + 40) {
                        return new Location(world, x + 0.5, h + 1.0, z + 0.5);
                    }
                }
            }
        }
        return null; // fall back to vanilla spawn search
    }

    // NOTE (from v2, still true): Paper 26.3's shouldGenerateSurface()
    // defaults to FALSE (verified via javap) — we must return true or no
    // surface step runs at all. We do NOT override the deprecated
    // isParallelCapable()/shouldGenerateBedrock(): bedrock is placed in the
    // terrain stage, and the generator is stateless/thread-safe regardless.
    @Override
    public boolean shouldGenerateSurface() {
        return true;
    }

    @Override
    public boolean shouldGenerateNoise() {
        return true;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return config.caves();
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return config.decorations();
    }

    @Override
    public boolean shouldGenerateStructures() {
        return config.structures();
    }

    @Override
    public boolean shouldGenerateMobs() {
        return config.mobs();
    }
}
