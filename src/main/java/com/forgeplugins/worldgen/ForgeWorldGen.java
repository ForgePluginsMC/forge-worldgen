package com.forgeplugins.worldgen;

import com.forgeplugins.worldgen.gen.ForgeChunkGenerator;
import com.forgeplugins.worldgen.gen.GenSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ForgeWorldGen v2 — vanilla-character terrain, gently smoothed.
 *
 * <p>Registers the {@code /fgen} command and re-attaches the custom
 * generator to managed worlds on startup so restarts never silently fall
 * back to vanilla generation at world borders.
 */
public final class ForgeWorldGen extends JavaPlugin {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ForgeChunkGenerator generator = null;
    private WorldManager worlds = null;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.generator = new ForgeChunkGenerator(GenSettings.fromConfig(getConfig()));
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
        getLogger().info("Enabled v2: vanilla-character terrain, gently smoothed.");
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
}
