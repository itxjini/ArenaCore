package com.arenacore.death;

import com.arenacore.ArenaCore;
import com.arenacore.arena.Arena;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player death data, respawn flow, and /back system.
 */
public class DeathManager {

    private final ArenaCore plugin;
    private final Map<UUID, DeathData> deathDataMap;
    private final long backCooldown;

    public DeathManager(ArenaCore plugin) {
        this.plugin = plugin;
        this.deathDataMap = new ConcurrentHashMap<>();
        this.backCooldown = plugin.getConfigManager().getBackCooldown() * 1000L; // Convert to ms
    }

    /**
     * Records a player's death in an arena.
     */
    public void recordDeath(Player player, Arena arena, Location deathLocation) {
        Arena.PlayerArenaData arenaData = arena.getPlayerData(player.getUniqueId());
        String spawnName = arenaData != null ? arenaData.getSpawnName() : "center";

        DeathData data = new DeathData(
                arena.getName(),
                deathLocation.clone(),
                spawnName,
                System.currentTimeMillis()
        );

        deathDataMap.put(player.getUniqueId(), data);

        // Update arena stats
        if (arenaData != null) {
            arenaData.incrementDeaths();
        }
    }

    /**
     * Gets a player's last death data.
     */
    public DeathData getDeathData(UUID playerId) {
        return deathDataMap.get(playerId);
    }

    /**
     * Checks if a player has death data.
     */
    public boolean hasDeathData(UUID playerId) {
        return deathDataMap.containsKey(playerId);
    }

    /**
     * Checks if /back is available for a player.
     */
    public boolean canUseBack(UUID playerId) {
        DeathData data = deathDataMap.get(playerId);
        if (data == null) {
            return false;
        }

        // Check if cooldown expired
        if (backCooldown > 0) {
            long elapsed = System.currentTimeMillis() - data.getTimestamp();
            if (elapsed > backCooldown) {
                return false;
            }
        }

        // Check if arena still exists
        Arena arena = plugin.getArenaManager().getArena(data.getArenaName());
        return arena != null && arena.isEnabled();
    }

    /**
     * Gets the remaining cooldown time in seconds.
     */
    public long getRemainingCooldown(UUID playerId) {
        DeathData data = deathDataMap.get(playerId);
        if (data == null || backCooldown <= 0) {
            return 0;
        }

        long elapsed = System.currentTimeMillis() - data.getTimestamp();
        long remaining = (backCooldown - elapsed) / 1000;
        return Math.max(0, remaining);
    }

    /**
     * Clears death data for a player.
     */
    public void clearDeathData(UUID playerId) {
        deathDataMap.remove(playerId);
    }

    /**
     * Cleans up expired death data.
     */
    public void cleanup() {
        if (backCooldown <= 0) return;

        long now = System.currentTimeMillis();
        deathDataMap.entrySet().removeIf(entry ->
                now - entry.getValue().getTimestamp() > backCooldown
        );
    }

    /**
     * Stores information about a player's death.
     */
    public static class DeathData {
        private final String arenaName;
        private final Location deathLocation;
        private final String spawnName;
        private final long timestamp;

        public DeathData(String arenaName, Location deathLocation, String spawnName, long timestamp) {
            this.arenaName = arenaName;
            this.deathLocation = deathLocation;
            this.spawnName = spawnName;
            this.timestamp = timestamp;
        }

        public String getArenaName() { return arenaName; }
        public Location getDeathLocation() { return deathLocation; }
        public String getSpawnName() { return spawnName; }
        public long getTimestamp() { return timestamp; }
    }
}