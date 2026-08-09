package net.william278.huskhomes.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/** Native scheduler bridge for Paper and Folia. */
public final class FoliaScheduler {
    private static Boolean folia;

    private FoliaScheduler() {
    }

    public static boolean isFolia() {
        if (folia != null) {
            return folia;
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {
            folia = false;
        }
        return folia;
    }

    public static BukkitTask runLegacy(@NotNull Plugin plugin, @NotNull Runnable task, long delay) {
        return Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(0, delay));
    }

    public static BukkitTask runLegacyAsync(@NotNull Plugin plugin, @NotNull Runnable task, long delay) {
        return delay > 0
                ? Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay)
                : Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public static BukkitTask runLegacyRepeating(@NotNull Plugin plugin, @NotNull Runnable task, long period) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, 0, Math.max(1, period));
    }

    public static io.papermc.paper.threadedregions.scheduler.ScheduledTask runGlobal(@NotNull Plugin plugin, @NotNull Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> task,
                                 long delay) {
        return Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task, Math.max(1, delay));
    }

    public static io.papermc.paper.threadedregions.scheduler.ScheduledTask runEntity(@NotNull Plugin plugin, @NotNull Entity entity,
                                 @NotNull Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> task,
                                 long delay) {
        return entity.getScheduler().runDelayed(plugin, task, null, Math.max(1, delay));
    }

    public static io.papermc.paper.threadedregions.scheduler.ScheduledTask runRegion(@NotNull Plugin plugin, @NotNull Location location,
                                 @NotNull Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> task,
                                 long delay) {
        return Bukkit.getRegionScheduler().runDelayed(plugin, location, task, Math.max(1, delay));
    }

    public static io.papermc.paper.threadedregions.scheduler.ScheduledTask runAsync(@NotNull Plugin plugin,
                                @NotNull Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> task,
                                long delay) {
        return Bukkit.getAsyncScheduler().runDelayed(plugin, task, Math.max(0, delay) * 50L,
                java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public static io.papermc.paper.threadedregions.scheduler.ScheduledTask runAsyncRepeating(
            @NotNull Plugin plugin,
            @NotNull Consumer<io.papermc.paper.threadedregions.scheduler.ScheduledTask> task,
            long periodTicks) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin, task, 0,
                Math.max(1, periodTicks) * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public static void cancel(@NotNull Plugin plugin) {
        if (isFolia()) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
        } else {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }
}
