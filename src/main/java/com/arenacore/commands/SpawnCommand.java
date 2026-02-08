package com.arenacore.commands;

import com.arenacore.ArenaCore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles /spawn command with smooth delayed teleport.
 */
public class SpawnCommand implements CommandExecutor {

    private final ArenaCore plugin;
    private final Map<UUID, BukkitRunnable> pendingTeleports;
    private final Map<UUID, Location> lastLocations;

    public SpawnCommand(ArenaCore plugin) {
        this.plugin = plugin;
        this.pendingTeleports = new HashMap<>();
        this.lastLocations = new HashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        // Check if already teleporting
        if (pendingTeleports.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou are already teleporting to spawn!");
            return true;
        }

        Location lobby = plugin.getArenaManager().getLobbySpawn();
        if (lobby == null) {
            player.sendMessage("§cSpawn location has not been set!");
            return true;
        }

        int delay = plugin.getConfigManager().getSpawnDelay();

        if (delay <= 0) {
            // Instant teleport
            teleportToSpawn(player, lobby);
            return true;
        }

        // Start delayed teleport
        startDelayedTeleport(player, lobby, delay);

        return true;
    }

    /**
     * Starts a delayed teleport with movement cancellation.
     */
    private void startDelayedTeleport(Player player, Location lobby, int delaySeconds) {
        UUID playerId = player.getUniqueId();
        lastLocations.put(playerId, player.getLocation());

        // Send initial message
        String message = plugin.getConfigManager().getSpawnTeleportMessage()
                .replace("{time}", String.valueOf(delaySeconds));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

        // Create countdown task
        BukkitRunnable task = new BukkitRunnable() {
            int remaining = delaySeconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    pendingTeleports.remove(playerId);
                    lastLocations.remove(playerId);
                    return;
                }

                // Check if player moved
                Location lastLoc = lastLocations.get(playerId);
                Location currentLoc = player.getLocation();

                if (lastLoc != null && hasMoved(lastLoc, currentLoc)) {
                    player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfigManager().getSpawnMoveCancelMessage()));
                    cancel();
                    pendingTeleports.remove(playerId);
                    lastLocations.remove(playerId);
                    return;
                }

                if (remaining <= 0) {
                    teleportToSpawn(player, lobby);
                    cancel();
                    pendingTeleports.remove(playerId);
                    lastLocations.remove(playerId);
                    return;
                }

                // Show countdown
                if (remaining <= 3) {
                    player.sendTitle("",
                            ChatColor.YELLOW + "Teleporting in " + ChatColor.GOLD + remaining + "...",
                            0, 20, 5);
                }

                remaining--;
            }
        };

        pendingTeleports.put(playerId, task);
        task.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Teleports player to spawn with effects.
     */
    private void teleportToSpawn(Player player, Location lobby) {
        // Remove from arena if in one
        if (plugin.getArenaManager().isInArena(player)) {
            plugin.getArenaManager().removePlayerFromArena(player);
        }

        // Clear effects
        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType())
        );

        // Fade effect
        player.sendTitle("", "", 10, 20, 10);

        // Teleport
        player.teleport(lobby);

        // Success message
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getSpawnSuccessMessage()));

        // Handle inventory based on config
        if (plugin.getConfigManager().shouldClearInventoryOnSpawn()) {
            player.getInventory().clear();
        }
    }

    /**
     * Checks if player has moved significantly.
     */
    private boolean hasMoved(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) {
            return true;
        }

        double threshold = 0.1;
        return Math.abs(from.getX() - to.getX()) > threshold ||
                Math.abs(from.getY() - to.getY()) > threshold ||
                Math.abs(from.getZ() - to.getZ()) > threshold;
    }
}