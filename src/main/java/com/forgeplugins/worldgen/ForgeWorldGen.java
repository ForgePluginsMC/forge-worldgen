package com.forgeplugins.worldgen;

import com.forgeplugins.worldgen.gen.FarmKit;
import com.forgeplugins.worldgen.gen.ForgeChunkGenerator;
import com.forgeplugins.worldgen.gen.GenSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ForgeWorldGen — blazing-fast custom terrain generation for Paper.
 *
 * <p>Registers the {@code /fgen} command and re-attaches the custom generator
 * to managed worlds on startup so restarts never silently fall back to
 * vanilla generation at world borders.
 */
public final class ForgeWorldGen extends JavaPlugin {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ForgeChunkGenerator generator = null;
    private WorldManager worlds = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        BlockData oakLeaves = persistentLeaves(Material.OAK_LEAVES);
        BlockData spruceLeaves = persistentLeaves(Material.SPRUCE_LEAVES);
        BlockData acaciaLeaves = persistentLeaves(Material.ACACIA_LEAVES);
        BlockData jungleLeaves = persistentLeaves(Material.JUNGLE_LEAVES);
        FarmKit farms = farmKit();

        this.generator = new ForgeChunkGenerator(GenSettings.fromConfig(getConfig()),
                oakLeaves, spruceLeaves, acaciaLeaves, jungleLeaves, farms);
        this.worlds = new WorldManager(this, generator);

        var handler = new ForgeGenCommand(this, worlds);
        var command = getCommand("fgen");
        if (command != null) {
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        } else {
            getLogger().warning("Command 'fgen' is missing from plugin.yml; commands will not work.");
        }

        worlds.reattachManaged();
        getLogger().info("Enabled. Generator ready: mountains, volcanoes, 20 biomes, ponds, fields.");
    }

    @Override
    public void onDisable() {
        if (worlds != null) {
            worlds.cancelPregen();
        }
    }

    /** Reloads config.yml and applies new generation settings live. */
    public void reload() {
        reloadConfig();
        if (generator != null) {
            generator.updateSettings(GenSettings.fromConfig(getConfig()));
        }
    }

    public ForgeChunkGenerator generator() {
        if (generator == null) {
            throw new IllegalStateException("ForgeWorldGen not enabled yet");
        }
        return generator;
    }

    public WorldManager worlds() {
        if (worlds == null) {
            throw new IllegalStateException("ForgeWorldGen not enabled yet");
        }
        return worlds;
    }

    /** Sends a prefixed MiniMessage line to any audience. */
    public void tell(net.kyori.adventure.audience.Audience audience, String miniMessage) {
        String prefix = getConfig().getString("messages.prefix", "<gold>[ForgeWorldGen]</gold> ");
        audience.sendMessage(MINI_MESSAGE.deserialize(prefix + miniMessage));
    }

    public Component prefixed(String miniMessage) {
        String prefix = getConfig().getString("messages.prefix", "<gold>[ForgeWorldGen]</gold> ");
        return MINI_MESSAGE.deserialize(prefix + miniMessage);
    }

    private BlockData persistentLeaves(Material material) {
        BlockData data = Bukkit.createBlockData(material);
        if (data instanceof Leaves leaves) {
            leaves.setPersistent(true);
            leaves.setDistance(1);
        }
        return data;
    }

    /** Builds the farmland/crop template kit for wheat fields (main thread). */
    private FarmKit farmKit() {
        BlockData farmland = Bukkit.createBlockData(Material.FARMLAND);
        if (farmland instanceof Farmland farm) {
            farm.setMoisture(7);
        }
        return new FarmKit(farmland,
                agedCrop(Material.WHEAT, 7), agedCrop(Material.WHEAT, 3),
                agedCrop(Material.CARROTS, 7), agedCrop(Material.CARROTS, 3),
                agedCrop(Material.POTATOES, 7), agedCrop(Material.POTATOES, 3),
                agedCrop(Material.BEETROOTS, 3), agedCrop(Material.BEETROOTS, 1));
    }

    private BlockData agedCrop(Material material, int age) {
        BlockData data = Bukkit.createBlockData(material);
        if (data instanceof Ageable crop) {
            crop.setAge(Math.min(age, crop.getMaximumAge()));
        }
        return data;
    }
}
