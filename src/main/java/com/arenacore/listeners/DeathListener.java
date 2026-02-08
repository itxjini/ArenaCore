package com.arenacore.listeners;

import com.arenacore.ArenaCore;
import com.arenacore.arena.Arena;
import com.arenacore.death.DeathManager;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Handles player death, animations, and respawn flow in arenas.
 */
public class DeathListener implements Listener {

    private final ArenaCore plugin;

    public DeathListener(ArenaCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Arena arena = plugin.getArenaManager().getPlayerArena(victim);

        if (arena == null) {
            return; // Not in arena
        }

        Location deathLoc = victim.getLocation();

        plugin.getDeathManager().recordDeath(victim, arena, deathLoc);

        Player killer = victim.getKiller();
        if (killer != null && arena.hasPlayer(killer.getUniqueId())) {
            handleKill(killer, arena);
        }

        playDeathAnimation(victim, deathLoc);

        if (!arena.shouldClearInventory()) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DeathManager.DeathData deathData = plugin.getDeathManager().getDeathData(player.getUniqueId());

        if (deathData == null) {
            return; // Not an arena death
        }

        Arena arena = plugin.getArenaManager().getArena(deathData.getArenaName());
        if (arena == null) {
            return;
        }

        String respawnBehavior = plugin.getConfigManager().getRespawnBehavior();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            handleRespawn(player, arena, deathData, respawnBehavior);
        }, 1L);
    }

    /**
     * Plays death animation for the player.
     */
    private void playDeathAnimation(Player player, Location deathLoc) {
        // Freeze effect (short duration)
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS, 20, 255, false, false
            ));
        });

        // Visual lightning (client-side only)
        if (plugin.getConfigManager().useDeathLightning()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.getWorld().strikeLightningEffect(deathLoc);
            }, 5L);
        }

        // Death title
        String deathTitle = plugin.getConfigManager().getDeathTitle();
        String deathSubtitle = plugin.getConfigManager().getDeathSubtitle();

        if (deathTitle != null && !deathTitle.isEmpty()) {
            player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', deathTitle),
                    ChatColor.translateAlternateColorCodes('&', deathSubtitle),
                    10, 40, 20
            );
        }
    }

    /**
     * Handles respawn flow based on configuration.
     */
    private void handleRespawn(Player player, Arena arena, DeathManager.DeathData deathData, String behavior) {
        switch (behavior.toUpperCase()) {
            case "LOBBY":
                sendToLobby(player);
                break;

            case "GUI":
                if (arena.hasMultipleSpawns()) {
                    plugin.getGuiManager().openSpawnGUI(player, arena);
                } else {
                    sendToArena(player, arena, "center");
                }
                break;

            case "CENTER":
                sendToArena(player, arena, "center");
                break;

            case "SAME_SPAWN":
                sendToArena(player, arena, deathData.getSpawnName());
                break;

            default:
                sendToArena(player, arena, "center");
        }
    }

    /**
     * Sends player to lobby.
     */
    private void sendToLobby(Player player) {
        Location lobby = plugin.getArenaManager().getLobbySpawn();
        if (lobby != null) {
            player.teleport(lobby);
        }

        // Remove from arena
        plugin.getArenaManager().removePlayerFromArena(player);

        // Clear effects
        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType())
        );
    }

    /**
     * Sends player back to arena spawn.
     */
    private void sendToArena(Player player, Arena arena, String spawnName) {
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
            sendToLobby(player);
            return;
        }

        player.teleport(spawn);

        plugin.getArenaManager().addPlayerToArena(player, arena, spawnName);

        applyArenaJoinLogic(player, arena);
    }

    /**
     * Handles player getting a kill (rekit).
     */
    private void handleKill(Player killer, Arena arena) {
        Arena.PlayerArenaData data = arena.getPlayerData(killer.getUniqueId());
        if (data != null) {
            data.incrementKills();
        }

        // Rekit on kill
        if (arena.isRekitOnKill()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                applyArenaJoinLogic(killer, arena);
            });
        }
    }

    /**
     * Applies full arena join logic (kit, effects, health).
     */
    private void applyArenaJoinLogic(Player player, Arena arena) {
        if (arena.shouldClearInventory()) {
            player.getInventory().clear();
        }

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

        if (arena.shouldResetHealth()) {
            player.setHealth(20.0);
        }

        if (arena.shouldResetHunger()) {
            player.setFoodLevel(20);
            player.setSaturation(20);
        }

        player.getActivePotionEffects().forEach(effect ->
                player.removePotionEffect(effect.getType())
        );

        arena.getPotionEffects().values().forEach(config ->
                player.addPotionEffect(config.createEffect())
        );

        if (arena.getGamemodeOverride() != null) {
            try {
                GameMode mode = GameMode.valueOf(arena.getGamemodeOverride());
                player.setGameMode(mode);
            } catch (IllegalArgumentException ignored) {}
        }
    }
}