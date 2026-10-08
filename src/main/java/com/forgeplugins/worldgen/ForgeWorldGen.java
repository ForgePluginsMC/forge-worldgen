package com.forgeplugins.worldgen;

import com.forgeplugins.worldgen.command.ForgeGenCommand;
import com.forgeplugins.worldgen.config.GenConfig;
import com.forgeplugins.worldgen.gen.ForgeChunkGenerator;
import com.forgeplugins.worldgen.world.WorldManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * ForgeWorldGen v3 — staged-pipeline custom world generation.
 *
 * <p>Registers the {@code /fgen} command and re-attaches the custom
 * generator (plus biome provider) to managed worlds on startup so restarts
 * never silently fall back to vanilla generation at world borders.
 */
public final class ForgeWorldGen extends JavaPlugin {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ForgeChunkGenerator generator = null;
    private WorldManager worlds = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.generator = new ForgeChunkGenerator(GenConfig.fromConfig(getConfig()));
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
        getLogger().info("Enabled v3: staged-pipeline terrain (volcanoes, ranges, rivers, scablands).");
    }

    @Override
    public void onDisable() {
        if (worlds != null) {
            worlds.cancelPregen();
        }
    }

    /** Reloads config.yml and rebuilds generation engines live. */
    public void reload() {
        reloadConfig();
        if (generator != null) {
            generator.updateConfig(GenConfig.fromConfig(getConfig()));
        }
    }

    public @NotNull ForgeChunkGenerator generator() {
        if (generator == null) {
            throw new IllegalStateException("ForgeWorldGen not enabled yet");
        }
        return generator;
    }

    public @NotNull WorldManager worlds() {
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

    public @NotNull Component prefixed(String miniMessage) {
        String prefix = getConfig().getString("messages.prefix", "<gold>[ForgeWorldGen]</gold> ");
        return MINI_MESSAGE.deserialize(prefix + miniMessage);
    }
}
