package com.forgeplugins.worldgen.command;

import com.forgeplugins.worldgen.ForgeWorldGen;
import com.forgeplugins.worldgen.biome.ForgeBiome;
import com.forgeplugins.worldgen.world.PregenTask;
import com.forgeplugins.worldgen.world.WorldManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles {@code /fgen}: world creation, teleporting, pre-generation,
 * config reloads, determinism verification and biome inspection.
 */
public final class ForgeGenCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_ADMIN = "fgen.admin";
    private static final String PERM_TP = "fgen.tp";

    private final ForgeWorldGen plugin;
    private final WorldManager worlds;

    public ForgeGenCommand(@NotNull ForgeWorldGen plugin, @NotNull WorldManager worlds) {
        this.plugin = plugin;
        this.worlds = worlds;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "tp" -> handleTeleport(sender, args);
            case "pregen" -> handlePregen(sender, args);
            case "cancel" -> handleCancel(sender);
            case "reload" -> handleReload(sender);
            case "verify" -> handleVerify(sender, args);
            case "biome" -> handleBiome(sender);
            default -> sendUsage(sender, label);
        }
        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            plugin.tell(sender, "<red>You don't have permission to create worlds.</red>");
            return;
        }
        if (args.length < 2) {
            plugin.tell(sender, "<yellow>Usage: /fgen create <name> [seed]</yellow>");
            return;
        }
        String name = args[1];
        if (!name.matches("[A-Za-z0-9_-]{1,32}")) {
            plugin.tell(sender, "<red>World name must be 1-32 chars: letters, digits, _ or -.</red>");
            return;
        }
        long seed;
        if (args.length >= 3) {
            try {
                seed = Long.parseLong(args[2]);
            } catch (NumberFormatException e) {
                seed = args[2].hashCode();
            }
        } else {
            seed = new java.util.Random().nextLong();
        }
        plugin.tell(sender, "<yellow>Creating world <white>" + name + "</white> (seed " + seed + ")…</yellow>");
        World world = worlds.createWorld(name, seed);
        if (world == null) {
            plugin.tell(sender, "<red>A world named '" + name + "' is already loaded.</red>");
            return;
        }
        Location spawn = world.getSpawnLocation();
        plugin.tell(sender, "<green>World <white>" + name + "</white> created. Spawn: "
                + spawn.getBlockX() + ", " + spawn.getBlockY() + ", " + spawn.getBlockZ() + "</green>");
        if (sender instanceof Player) {
            plugin.tell(sender, "<gray>Use <white>/fgen tp " + name + "</white> to visit it.</gray>");
        }
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_TP) && !sender.hasPermission(PERM_ADMIN)) {
            plugin.tell(sender, "<red>You don't have permission to teleport.</red>");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.tell(sender, "<red>Only players can teleport.</red>");
            return;
        }
        if (args.length < 2) {
            plugin.tell(sender, "<yellow>Usage: /fgen tp <world></yellow>");
            return;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            plugin.tell(sender, "<red>World '" + args[1] + "' is not loaded.</red>");
            return;
        }
        Location spawn = world.getSpawnLocation();
        Location target = world.getHighestBlockAt(spawn.getBlockX(), spawn.getBlockZ()).getLocation().add(0.5, 1.0, 0.5);
        player.teleport(target);
        plugin.tell(sender, "<green>Teleported to <white>" + world.getName() + "</white>.</green>");
    }

    private void handlePregen(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            plugin.tell(sender, "<red>You don't have permission to pre-generate.</red>");
            return;
        }
        if (args.length < 3) {
            plugin.tell(sender, "<yellow>Usage: /fgen pregen <world> <radiusChunks> (max "
                    + PregenTask.MAX_RADIUS + ")</yellow>");
            return;
        }
        World world = Bukkit.getWorld(args[1]);
        if (world == null) {
            plugin.tell(sender, "<red>World '" + args[1] + "' is not loaded.</red>");
            return;
        }
        int radius;
        try {
            radius = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            plugin.tell(sender, "<red>Radius must be a number of chunks.</red>");
            return;
        }
        if (radius < 1 || radius > PregenTask.MAX_RADIUS) {
            plugin.tell(sender, "<red>Radius must be between 1 and " + PregenTask.MAX_RADIUS + " chunks.</red>");
            return;
        }
        if (worlds.pregenRunning()) {
            plugin.tell(sender, "<red>A pre-generation is already running. Use /fgen cancel first.</red>");
            return;
        }
        worlds.startPregen(world, radius);
        plugin.tell(sender, "<yellow>Pre-generation started — watch console for the chunks/sec readout.</yellow>");
    }

    private void handleCancel(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            plugin.tell(sender, "<red>You don't have permission to do that.</red>");
            return;
        }
        if (!worlds.pregenRunning()) {
            plugin.tell(sender, "<gray>No pre-generation is running.</gray>");
            return;
        }
        worlds.cancelPregen();
        plugin.tell(sender, "<yellow>Pre-generation cancelled.</yellow>");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            plugin.tell(sender, "<red>You don't have permission to reload.</red>");
            return;
        }
        try {
            plugin.reload();
        } catch (IllegalArgumentException e) {
            plugin.tell(sender, "<red>Config invalid: " + e.getMessage() + "</red>");
            return;
        }
        plugin.tell(sender, "<green>ForgeWorldGen config reloaded — engines rebuilt. New chunks use the new settings.</green>");
    }

    /**
     * Determinism proof: hashes the heightmap + biome grid for a seed.
     * Run it twice on the same seed — identical hashes mean the generator
     * is deterministic.
     */
    private void handleVerify(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            plugin.tell(sender, "<red>You don't have permission to verify.</red>");
            return;
        }
        long seed;
        if (args.length >= 2) {
            try {
                seed = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                plugin.tell(sender, "<red>Seed must be a number.</red>");
                return;
            }
        } else if (sender instanceof Player player) {
            seed = player.getWorld().getSeed();
        } else {
            plugin.tell(sender, "<yellow>Usage: /fgen verify <seed></yellow>");
            return;
        }
        String hash = plugin.generator().verifyHash(seed, 128);
        plugin.tell(sender, "<green>Determinism hash for seed <white>" + seed + "</white>:</green>");
        plugin.tell(sender, "<gray><white>" + hash + "</white></gray>");
        plugin.tell(sender, "<gray>Same seed ⇒ same hash, on any server, every time.</gray>");
    }

    /** Reports the ForgeWorldGen biome (and its vanilla derivative) at the player. */
    private void handleBiome(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.tell(sender, "<red>Only players can inspect biomes.</red>");
            return;
        }
        if (!sender.hasPermission(PERM_TP) && !sender.hasPermission(PERM_ADMIN)) {
            plugin.tell(sender, "<red>You don't have permission to do that.</red>");
            return;
        }
        Location loc = player.getLocation();
        ForgeBiome biome = plugin.generator().biomeAt(
                loc.getWorld().getSeed(), loc.getBlockX(), loc.getBlockZ());
        int h = plugin.generator().heightAt(
                loc.getWorld().getSeed(), loc.getBlockX(), loc.getBlockZ());
        plugin.tell(sender, "<green>Biome: <white>" + biome.label()
                + "</white> (vanilla sees <white>" + biome.vanillaLabel()
                + "</white>), terrain height <white>" + h + "</white>.</green>");
    }

    private void sendUsage(CommandSender sender, String label) {
        List<Component> lines = List.of(
                plugin.prefixed("<gold><bold>ForgeWorldGen</bold></gold> <gray>v"
                        + plugin.getPluginMeta().getVersion() + "</gray>"),
                Component.text("  /" + label + " create <name> [seed] - create a ForgeWorldGen world"),
                Component.text("  /" + label + " tp <world> - teleport to a world"),
                Component.text("  /" + label + " pregen <world> <radius> - pre-generate with speed readout"),
                Component.text("  /" + label + " cancel - stop a running pre-generation"),
                Component.text("  /" + label + " reload - reload config.yml"),
                Component.text("  /" + label + " verify [seed] - determinism hash for a seed"),
                Component.text("  /" + label + " biome - inspect the biome at your feet"));
        for (Component line : lines) {
            sender.sendMessage(line);
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "tp", "pregen", "cancel", "reload", "verify", "biome"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("pregen"))) {
            List<String> names = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                names.add(world.getName());
            }
            return filter(names, args[1]);
        }
        return List.of();
    }

    private static @NotNull List<String> filter(@NotNull List<String> options, @NotNull String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
