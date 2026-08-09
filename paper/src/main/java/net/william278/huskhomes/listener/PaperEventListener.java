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

package net.william278.huskhomes.listener;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import net.william278.huskhomes.PaperHuskHomes;
import net.william278.huskhomes.config.Settings;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.NotNull;

public class PaperEventListener extends EventListener implements Listener {

    public PaperEventListener(@NotNull PaperHuskHomes plugin) {
        super(plugin);
    }

    @Override
    public void register() {
        getPlugin().getServer().getPluginManager().registerEvents(this, getPlugin());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        getPlugin().getOnlineUserMap().remove(event.getPlayer().getUniqueId());
        super.handlePlayerJoin(getPlugin().getOnlineUser(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerLeave(PlayerQuitEvent event) {
        super.handlePlayerLeave(getPlugin().getOnlineUser(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        super.handlePlayerDeath(getPlugin().getOnlineUser(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        getPlugin().getOnlineUserMap().remove(event.getPlayer().getUniqueId());
        if (event.getRespawnReason() == PlayerRespawnEvent.RespawnReason.DEATH) {
            super.handlePlayerRespawn(getPlugin().getOnlineUser(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        final Player player = event.getPlayer();

        // Return if the disconnecting entity is a Citizens NPC, or if the teleport was naturally caused
        if (player.hasMetadata("NPC")) {
            return;
        }
        if (!(event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND ||
              event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            return;
        }

        this.handlePlayerTeleport(
                getPlugin().getOnlineUser(player),
                PaperHuskHomes.Adapter.adapt(event.getFrom(), getPlugin().getServerName())
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // Cancel warmup on any "hurt" event during warmup, even if damage is absorbed
        if (!getPlugin().isWarmingUp(player.getUniqueId()) || event.getDamage() <= 0) {
            return;
        }
        getPlugin().getWarmupDamagedUsers().add(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerUpdateRespawnLocation(PlayerSetSpawnEvent event) {
        final Settings.CrossServerSettings crossServer = getPlugin().getSettings().getCrossServer();
        if (!(crossServer.isEnabled() && crossServer.isGlobalRespawning())) {
            return;
        }
        final Location location = event.getLocation();
        if (location == null) {
            return;
        }

        // Update the player's respawn location
        this.handlePlayerUpdateSpawnPoint(
                getPlugin().getOnlineUser(event.getPlayer()),
                PaperHuskHomes.Adapter.adapt(location, getPlugin().getServerName())
        );
    }

    @Override
    @NotNull
    protected PaperHuskHomes getPlugin() {
        return (PaperHuskHomes) super.getPlugin();
    }


}
