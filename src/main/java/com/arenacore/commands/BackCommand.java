package com.arenacore.commands;

import com.arenacore.ArenaCore;
import com.arenacore.arena.Arena;
import com.arenacore.death.DeathManager;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Handles /back command to return to death location.
 */
public class BackCommand implements CommandExecutor {

    private final ArenaCore plugin;

    public BackCommand(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        // Check if player has death data
        if (!plugin.getDeathManager().hasDeathData(player.getUniqueId())) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfigManager().getBackNoDataMessage()));
            return true;
        }

        // Check if /back is available
        if (!plugin.getDeathManager().canUseBack(player.getUniqueId())) {
            long remaining = plugin.getDeathManager().getRemainingCooldown(player.getUniqueId());

            if (remaining <= 0) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfigManager().getBackExpiredMessage()));
            } else {
                String message = plugin.getConfigManager().getBackCooldownMessage()
                        .replace("{time}", formatTime(remaining));
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }

            return true;
        }

        // Get death data
        DeathManager.DeathData deathData = plugin.getDeathManager().getDeathData(player.getUniqueId());
        Arena arena = plugin.getArenaManager().getArena(deathData.getArenaName());

        if (arena == null || !arena.isEnabled()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfigManager().getBackArenaUnavailableMessage()));
            return true;
        }

        // Teleport to death location
        teleportToDeathLocation(player, arena, deathData);

        return true;
    }

    /**
     * Teleports player back to their death location.
     */
    private void teleportToDeathLocation(Player player, Arena arena, DeathManager.DeathData deathData) {
        Location deathLoc = deathData.getDeathLocation();

        // Teleport
        player.teleport(deathLoc);

        // Add to arena
        plugin.getArenaManager().addPlayerToArena(player, arena, deathData.getSpawnName());

        // Apply arena join logic
        applyArenaJoinLogic(player, arena);

        // Success message
        String message = plugin.getConfigManager().getBackSuccessMessage()
                .replace("{arena}", arena.getName());
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

        // Clear death data
        plugin.getDeathManager().clearDeathData(player.getUniqueId());
    }

    /**
     * Applies full arena join logic (kit, effects, health).
     */
    private void applyArenaJoinLogic(Player player, Arena arena) {
        // Clear inventory if configured
        if (arena.shouldClearInventory()) {
            player.getInventory().clear();
        }

        // Apply kit items
        for (Arena.KitItem kitItem : arena.getKitItems()) {
            try {
                Material material = Material.valueOf(kitItem.getMaterial());
                ItemStack item = new ItemStack(material, kitItem.getAmount());

                if (kitItem.getDisplayName() != null) {
                    var meta = item.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                                kitItem.getDisplayName()));

                        if (kitItem.getLore() != null && !kitItem.getLore().isEmpty()) {
                            var lore = kitItem.getLore().stream()
                                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                                    .toList();
                            meta.setLore(lore);
                        }

                        item.setItemMeta(meta);
                    }
                }

                if (kitItem.getSlot() >= 0 && kitItem.getSlot() < 41) {
                    player.getInventory().setItem(kitItem.getSlot(), item);
                } else {
                    player.getInventory().addItem(item);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in kit: " + kitItem.getMaterial());
            }
        }

        // Reset health
        if (arena.shouldResetHealth()) {
            player.setHealth(20.0);
        }

        // Reset hunger
        if (arena.shouldResetHunger()) {
            player.setFoodLevel(20);
            player.setSaturation(20);
        }

        // Apply potion effects
        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType())
        );

        arena.getPotionEffects().values().forEach(config ->
                player.addPotionEffect(config.createEffect())
        );

        // Set gamemode
        if (arena.getGamemodeOverride() != null) {
            try {
                GameMode mode = GameMode.valueOf(arena.getGamemodeOverride());
                player.setGameMode(mode);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    /**
     * Formats seconds into readable time.
     */
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes + "m " + secs + "s";
    }
}