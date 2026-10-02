package com.forgeplugins.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * Pre-generates a square region of chunks around a world's spawn in an
 * outward spiral, a few chunks per tick so the server stays responsive.
 * Reports live progress and the headline number: chunks per second.
 */
public final class PregenTask implements Runnable {

    /** Hard cap: 97x97 chunks is already ~9.4k chunks. */
    public static final int MAX_RADIUS = 48;
    private static final int CHUNKS_PER_TICK = 8;

    private final ForgeWorldGen plugin;
    private final World world;
    private final Audience audience;
    private final List<int[]> spiral;
    private final long startedNanos = System.nanoTime();

    private @NotNull BukkitTask task;
    private int index = 0;
    private int lastReported = 0;
    private long lastReportNanos = startedNanos;
    private volatile boolean done = false;

    public PregenTask(@NotNull ForgeWorldGen plugin, @NotNull World world, int radiusChunks) {
        this.plugin = plugin;
        this.world = world;
        this.audience = Bukkit.getConsoleSender();
        int centerX = world.getSpawnLocation().getBlockX() >> 4;
        int centerZ = world.getSpawnLocation().getBlockZ() >> 4;
        this.spiral = buildSpiral(centerX, centerZ, radiusChunks);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this, 1L, 1L);
    }

    public void start() {
        plugin.tell(audience, "<yellow>Pre-generating <white>" + spiral.size()
                + "</white> chunks in world <white>" + world.getName() + "</white>…</yellow>");
        plugin.getLogger().info("Pregen started: " + spiral.size() + " chunks in '" + world.getName() + "'.");
    }

    public void cancel() {
        done = true;
        task.cancel();
    }

    public boolean isDone() {
        return done;
    }

    @Override
    public void run() {
        if (done) {
            return;
        }
        int processed = 0;
        while (processed < CHUNKS_PER_TICK && index < spiral.size()) {
            int[] c = spiral.get(index++);
            world.getChunkAt(c[0], c[1], true);
            processed++;
        }

        long now = System.nanoTime();
        if (index - lastReported >= 200 || index >= spiral.size()) {
            double elapsed = (now - lastReportNanos) / 1_000_000_000.0;
            double rate = elapsed > 0 ? (index - lastReported) / elapsed : 0.0;
            int pct = (int) (index * 100.0 / spiral.size());
            plugin.tell(audience, "<gray>Pregen <white>" + pct + "%</white> ("
                    + index + "/" + spiral.size() + ") — <green>" + String.format("%.1f", rate)
                    + " chunks/sec</green></gray>");
            lastReported = index;
            lastReportNanos = now;
        }

        if (index >= spiral.size()) {
            done = true;
            task.cancel();
            double total = (System.nanoTime() - startedNanos) / 1_000_000_000.0;
            double avg = total > 0 ? spiral.size() / total : 0.0;
            String summary = "Pregen complete: " + spiral.size() + " chunks in "
                    + String.format("%.1f", total) + "s — avg "
                    + String.format("%.1f", avg) + " chunks/sec";
            plugin.tell(audience, "<green><bold>" + summary + "</bold></green>");
            plugin.getLogger().info("[ForgeWorldGen] " + summary);
            Bukkit.getScheduler().runTask(plugin, () -> world.save());
        }
    }

    private static @NotNull List<int[]> buildSpiral(int centerX, int centerZ, int radius) {
        List<int[]> coords = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                coords.add(new int[]{centerX + dx, centerZ + dz});
            }
        }
        coords.sort(Comparator.comparingInt(c ->
                (c[0] - centerX) * (c[0] - centerX) + (c[1] - centerZ) * (c[1] - centerZ)));
        return coords;
    }
}
