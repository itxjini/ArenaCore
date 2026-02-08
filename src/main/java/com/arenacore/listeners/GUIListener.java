package com.arenacore.listeners;

import com.arenacore.ArenaCore;
import com.arenacore.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles GUI interactions for spawn selection.
 */
public class GUIListener implements Listener {

    private final ArenaCore plugin;

    public GUIListener(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        // Check if player has pending spawn selection
        if (!plugin.getGuiManager().hasPendingSelection(player.getUniqueId())) {
            return;
        }

        // Cancel the event
        event.setCancelled(true);

        // Get clicked slot
        int slot = event.getRawSlot();

        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return; // Clicked outside GUI
        }

        // Handle spawn selection
        String spawnName = plugin.getGuiManager().handleGUIClick(
                player,
                event.getInventory(),
                slot
        );

        if (spawnName != null) {
            // Close inventory
            player.closeInventory();

            // Get arena
            String arenaName = plugin.getGuiManager().getPendingArena(player.getUniqueId());
            Arena arena = plugin.getArenaManager().getArena(arenaName);

            if (arena != null) {
                // Teleport to selected spawn
                Bukkit.getScheduler().runTask(plugin, () -> {
                    teleportToSpawn(player, arena, spawnName);
                });
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();

        // Check if player has pending spawn selection
        if (!plugin.getGuiManager().hasPendingSelection(player.getUniqueId())) {
            return;
        }

        // Get arena
        String arenaName = plugin.getGuiManager().getPendingArena(player.getUniqueId());
        Arena arena = plugin.getArenaManager().getArena(arenaName);

        if (arena == null) {
            plugin.getGuiManager().cancelSelection(player.getUniqueId());
            return;
        }

        // Timeout fallback - send to center spawn
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getGuiManager().hasPendingSelection(player.getUniqueId())) {
                plugin.getGuiManager().cancelSelection(player.getUniqueId());

                player.sendMessage(ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfigManager().getSpawnTimeoutMessage()));

                teleportToSpawn(player, arena, "center");
            }
        }, 1L);
    }

    /**
     * Teleports player to a spawn in the arena.
     */
    private void teleportToSpawn(Player player, Arena arena, String spawnName) {
        Location spawn;

        if ("center".equalsIgnoreCase(spawnName)) {
            spawn = arena.getCenterSpawn();
        } else {
            spawn = arena.getNamedSpawn(spawnName);
            if (spawn == null) {
                spawn = arena.getCenterSpawn();
                spawnName = "center";
            }
        }

        if (spawn == null) {
            plugin.getLogger().warning("No valid spawn for arena: " + arena.getName());
            return;
        }

        // Teleport
        player.teleport(spawn);

        // Add to arena
        plugin.getArenaManager().addPlayerToArena(player, arena, spawnName);

        // Apply arena join logic
        applyArenaJoinLogic(player, arena);

        // Send message
        String message = plugin.getConfigManager().getArenaJoinMessage()
                .replace("{arena}", arena.getName())
                .replace("{spawn}", spawnName);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
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
}