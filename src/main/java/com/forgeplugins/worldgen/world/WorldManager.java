package com.forgeplugins.worldgen.world;

import com.forgeplugins.worldgen.ForgeWorldGen;
import com.forgeplugins.worldgen.gen.ForgeChunkGenerator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns ForgeWorldGen's worlds: creation via {@code /fgen create} and
 * re-attaching the custom generator on startup.
 *
 * <p>Bukkit auto-loads world folders on boot but forgets which generator made
 * them, so without re-attachment a restart would silently generate vanilla
 * chunks at the borders of our worlds. Managed worlds (recorded in
 * config.yml's {@code managed-worlds} list) are unloaded and recreated with
 * our generator on enable; existing region files on disk are kept.
 */
public final class WorldManager {

    private final ForgeWorldGen plugin;
    private final ForgeChunkGenerator generator;
    private final Set<String> sessionWorlds = new HashSet<>();
    private @Nullable PregenTask pregen = null;

    public WorldManager(@NotNull ForgeWorldGen plugin, @NotNull ForgeChunkGenerator generator) {
        this.plugin = plugin;
        this.generator = generator;
    }

    /**
     * Creates a new ForgeWorldGen world and records it as managed.
     *
     * @return the created world, or null when a world with that name exists
     */
    public @Nullable World createWorld(@NotNull String name, long seed) {
        if (Bukkit.getWorld(name) != null) {
            return null;
        }
        WorldCreator creator = WorldCreator.name(name)
                .seed(seed)
                .type(WorldType.NORMAL)
                .environment(World.Environment.NORMAL)
                .generator(generator)
                .generateStructures(true);
        World world = Bukkit.createWorld(creator);
        if (world != null) {
            sessionWorlds.add(name);
            List<String> managed = plugin.getConfig().getStringList("managed-worlds");
            if (!managed.contains(name)) {
                managed.add(name);
                plugin.getConfig().set("managed-worlds", managed);
                plugin.saveConfig();
            }
            plugin.getLogger().info("Created world '" + name + "' (seed " + seed + ").");
        }
        return world;
    }

    /** Re-attaches the generator to managed worlds after a restart. */
    public void reattachManaged() {
        if (!plugin.getConfig().getBoolean("world.auto-manage", true)) {
            return;
        }
        for (String name : plugin.getConfig().getStringList("managed-worlds")) {
            if (sessionWorlds.contains(name)) {
                continue;
            }
            World loaded = Bukkit.getWorld(name);
            if (loaded != null) {
                // Unload the auto-loaded copy (saved first), then reload with our generator.
                if (!Bukkit.unloadWorld(loaded, true)) {
                    plugin.getLogger().warning("Could not unload auto-loaded world '" + name
                            + "'; its generator may not be ForgeWorldGen.");
                    continue;
                }
            }
            WorldCreator creator = WorldCreator.name(name)
                    .type(WorldType.NORMAL)
                    .environment(World.Environment.NORMAL)
                    .generator(generator)
                    .generateStructures(true);
            World world = Bukkit.createWorld(creator);
            if (world != null) {
                sessionWorlds.add(name);
                plugin.getLogger().info("Re-attached ForgeWorldGen generator to world '" + name + "'.");
            } else {
                plugin.getLogger().warning("Failed to load managed world '" + name + "'.");
            }
        }
    }

    public boolean pregenRunning() {
        return pregen != null && !pregen.isDone();
    }

    public void startPregen(@NotNull World world, int radiusChunks) {
        cancelPregen();
        pregen = new PregenTask(plugin, world, radiusChunks);
        pregen.start();
    }

    public void cancelPregen() {
        if (pregen != null) {
            pregen.cancel();
            pregen = null;
        }
    }
}
