package com.arenacore.listeners;

import com.arenacore.ArenaCore;
import com.arenacore.arena.Arena;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Handles all arena-related events including block interactions,
 * player movement, and combat tracking.
 */
public class ArenaListener implements Listener {

    private final ArenaCore plugin;

    public ArenaListener(ArenaCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles block placement within arenas.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();

        Arena playerArena = plugin.getArenaManager().getPlayerArena(player);

        if (playerArena != null) {
            if (!isLocationInArena(loc, playerArena)) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot place blocks outside the arena!");
                return;
            }

            Arena.BlockPosition blockPos = new Arena.BlockPosition(
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
            );

            playerArena.addPlacedBlock(blockPos);
            playerArena.incrementBlocksPlaced();

        } else {
            Arena arenaAtLocation = plugin.getArenaManager().getArenaAtLocation(loc);

            if (arenaAtLocation != null) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot modify this arena from outside!");
            }
        }
    }

    /**
     * Handles block breaking within arenas.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();

        Arena playerArena = plugin.getArenaManager().getPlayerArena(player);

        if (playerArena != null) {
            Arena.BlockPosition blockPos = new Arena.BlockPosition(
                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
            );

            if (!playerArena.isBlockPlaced(blockPos)) {
                event.setCancelled(true);
                player.sendMessage("§cYou can only break blocks that players have placed!");
                return;
            }

            playerArena.removePlacedBlock(blockPos);

        } else {
            Arena arenaAtLocation = plugin.getArenaManager().getArenaAtLocation(loc);

            if (arenaAtLocation != null) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot modify this arena from outside!");
            }
        }
    }

    /**
     * Tracks when players move between arenas or enter/exit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null ||
                (from.getBlockX() == to.getBlockX() &&
                        from.getBlockY() == to.getBlockY() &&
                        from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        Arena currentArena = plugin.getArenaManager().getPlayerArena(player);
        Arena targetArena = plugin.getArenaManager().getArenaAtLocation(to);

        if (currentArena == null && targetArena != null) {
            handleArenaEntry(player, targetArena, event);
        } else if (currentArena != null && targetArena == null) {
            handleArenaExit(player, currentArena);
        } else if (currentArena != null && targetArena != null &&
                !currentArena.equals(targetArena)) {
            handleArenaExit(player, currentArena);
            handleArenaEntry(player, targetArena, event);
        }
    }

    /**
     * Handles player teleportation to track arena transitions.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        Arena currentArena = plugin.getArenaManager().getPlayerArena(player);
        Arena targetArena = plugin.getArenaManager().getArenaAtLocation(to);

        if (currentArena != null && targetArena == null) {
            handleArenaExit(player, currentArena);
        }
    }

    /**
     * Removes players from arenas when they quit.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Arena arena = plugin.getArenaManager().getPlayerArena(player);

        if (arena != null) {
            handleArenaExit(player, arena);
        }
    }

    /**
     * Tracks combat activity for smart regeneration.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player victim = (Player) event.getEntity();
        Arena victimArena = plugin.getArenaManager().getPlayerArena(victim);

        if (victimArena != null) {
            // Combat tracking could be implemented here if needed
        }
    }

    /**
     * Handles a player entering an arena.
     */
    private void handleArenaEntry(Player player, Arena arena, PlayerMoveEvent event) {
        if (!arena.isEnabled()) {
            event.setCancelled(true);
            player.sendMessage("§cThis arena is currently disabled!");
            return;
        }

        if (arena.getPermission() != null &&
                !player.hasPermission("arena.join." + arena.getPermission()) &&
                !player.hasPermission("arena.admin.bypass")) {

            event.setCancelled(true);
            player.sendMessage("§cYou don't have permission to enter this arena!");
            return;
        }

        // Add player to arena with "center" as default spawn
        plugin.getArenaManager().addPlayerToArena(player, arena, "center");
        player.sendMessage("§7You entered arena: §e" + arena.getName());
    }

    /**
     * Handles a player exiting an arena.
     */
    private void handleArenaExit(Player player, Arena arena) {
        plugin.getArenaManager().removePlayerFromArena(player);

        arena.getPotionEffects().values().forEach(config ->
                player.removePotionEffect(config.getType())
        );

        player.sendMessage("§7You left arena: §e" + arena.getName());
    }

    /**
     * Checks if a location is within an arena's bounds.
     */
    private boolean isLocationInArena(Location location, Arena arena) {
        if (arena.getRegion() == null) {
            return false;
        }

        if (!arena.getWorld().equals(location.getWorld())) {
            return false;
        }

        com.sk89q.worldedit.math.BlockVector3 vec =
                com.sk89q.worldedit.math.BlockVector3.at(
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ()
                );

        return arena.getRegion().contains(vec);
    }
}