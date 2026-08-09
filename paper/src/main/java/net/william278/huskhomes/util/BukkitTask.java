/*
 * This file is part of HuskHomes, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.huskhomes.util;

import net.william278.huskhomes.PaperHuskHomes;
import net.william278.huskhomes.HuskHomes;
import net.william278.huskhomes.user.OnlineUser;
import net.william278.huskhomes.user.BukkitUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public interface BukkitTask extends Task {

    class Sync extends Task.Sync implements BukkitTask {

        private ScheduledTask task;
        private org.bukkit.scheduler.BukkitTask legacyTask;
        private final @Nullable OnlineUser user;

        protected Sync(@NotNull HuskHomes plugin, @NotNull Runnable runnable,
                       @Nullable OnlineUser user, long delayTicks) {
            super(plugin, runnable, delayTicks);
            this.user = user;
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                if (task != null) task.cancel();
                if (legacyTask != null) legacyTask.cancel();
            }
            super.cancel();
        }

        @Override
        public void run() {
            if (isPluginDisabled()) {
                runnable.run();
                return;
            }
            if (cancelled) {
                return;
            }

            if (!FoliaScheduler.isFolia()) {
                if (user != null) {
                    legacyTask = FoliaScheduler.runLegacy((PaperHuskHomes) getPlugin(), runnable, delayTicks);
                } else {
                    legacyTask = FoliaScheduler.runLegacy((PaperHuskHomes) getPlugin(), runnable, delayTicks);
                }
            } else if (user != null) {
                this.task = FoliaScheduler.runEntity((PaperHuskHomes) getPlugin(), ((BukkitUser) user).getPlayer(), t -> runnable.run(), delayTicks);
            } else {
                this.task = FoliaScheduler.runGlobal((PaperHuskHomes) getPlugin(), t -> runnable.run(), delayTicks);
            }
        }
    }

    class Async extends Task.Async implements BukkitTask {

        private ScheduledTask task;
        private org.bukkit.scheduler.BukkitTask legacyTask;

        protected Async(@NotNull HuskHomes plugin, @NotNull Runnable runnable, long delayTicks) {
            super(plugin, runnable, delayTicks);
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                if (task != null) task.cancel();
                if (legacyTask != null) legacyTask.cancel();
            }
            super.cancel();
        }

        @Override
        public void run() {
            if (isPluginDisabled()) {
                runnable.run();
                return;
            }
            if (cancelled) {
                return;
            }

            if (!FoliaScheduler.isFolia()) {
                legacyTask = FoliaScheduler.runLegacyAsync((PaperHuskHomes) getPlugin(), runnable, delayTicks);
            } else {
                this.task = FoliaScheduler.runAsync((PaperHuskHomes) getPlugin(), t -> runnable.run(), delayTicks);
            }
        }
    }

    class Repeating extends Task.Repeating implements BukkitTask {

        private ScheduledTask task;
        private org.bukkit.scheduler.BukkitTask legacyTask;

        protected Repeating(@NotNull HuskHomes plugin, @NotNull Runnable runnable, long repeatingTicks) {
            super(plugin, runnable, repeatingTicks);
        }

        @Override
        public void cancel() {
            if (!cancelled) {
                if (task != null) task.cancel();
                if (legacyTask != null) legacyTask.cancel();
            }
            super.cancel();
        }

        @Override
        public void run() {
            if (isPluginDisabled()) {
                return;
            }

            if (!cancelled) {
                if (!FoliaScheduler.isFolia()) {
                    legacyTask = FoliaScheduler.runLegacyRepeating((PaperHuskHomes) getPlugin(), runnable, repeatingTicks);
                } else {
                    this.task = FoliaScheduler.runAsyncRepeating((PaperHuskHomes) getPlugin(), t -> runnable.run(), repeatingTicks);
                }
            }
        }
    }

    // Returns if the Bukkit HuskHomes plugin is disabled
    default boolean isPluginDisabled() {
        return !((PaperHuskHomes) getPlugin()).isEnabled();
    }

    interface Supplier extends Task.Supplier {

        @NotNull
        @Override
        default Task.Sync getSyncTask(@NotNull Runnable runnable, @Nullable OnlineUser user, long delayTicks) {
            return new Sync(getPlugin(), runnable, user, delayTicks);
        }

        @NotNull
        @Override
        default Task.Async getAsyncTask(@NotNull Runnable runnable, long delayTicks) {
            return new Async(getPlugin(), runnable, delayTicks);
        }

        @NotNull
        @Override
        default Task.Repeating getRepeatingTask(@NotNull Runnable runnable, long repeatingTicks) {
            return new Repeating(getPlugin(), runnable, repeatingTicks);
        }

        @Override
        default void cancelTasks() {
            FoliaScheduler.cancel((PaperHuskHomes) getPlugin());
        }

    }

}
