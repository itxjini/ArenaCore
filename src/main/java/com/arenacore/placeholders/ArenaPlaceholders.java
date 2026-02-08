package com.arenacore.placeholders;

import com.arenacore.ArenaCore;
import com.arenacore.arena.Arena;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * PlaceholderAPI expansion for ArenaCore.
 *
 * Provides placeholders:
 * - %arena_players_<arena>% - Total players in arena
 * - %arena_players_<arena>_<spawn>% - Players at specific spawn
 */
public class ArenaPlaceholders extends PlaceholderExpansion {

    private final ArenaCore plugin;
    private final Map<String, Integer> cachedCounts;
    private long lastCacheUpdate;
    private static final long CACHE_DURATION = 1000; // 1 second

    public ArenaPlaceholders(ArenaCore plugin) {
        this.plugin = plugin;
        this.cachedCounts = new HashMap<>();
        this.lastCacheUpdate = 0;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "arena";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ArenaCore Team";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        // Update cache if needed
        updateCacheIfNeeded();

        // Parse placeholder
        if (params.startsWith("players_")) {
            return handlePlayersPlaceholder(params.substring(8));
        }

        return null;
    }

    /**
     * Handles %arena_players_<arena>% and %arena_players_<arena>_<spawn>%
     */
    private String handlePlayersPlaceholder(String params) {
        String[] parts = params.split("_", 2);

        if (parts.length == 0) {
            return "0";
        }

        String arenaName = parts[0];
        Arena arena = plugin.getArenaManager().getArena(arenaName);

        if (arena == null) {
            return "0";
        }

        // Just arena name - return total players
        if (parts.length == 1) {
            String cacheKey = "arena:" + arenaName;
            return String.valueOf(cachedCounts.getOrDefault(cacheKey, 0));
        }

        // Arena + spawn name - return players at that spawn
        String spawnName = parts[1];
        String cacheKey = "spawn:" + arenaName + ":" + spawnName;
        return String.valueOf(cachedCounts.getOrDefault(cacheKey, 0));
    }

    /**
     * Updates the cache if enough time has passed.
     */
    private void updateCacheIfNeeded() {
        long now = System.currentTimeMillis();

        if (now - lastCacheUpdate < CACHE_DURATION) {
            return;
        }

        cachedCounts.clear();

        for (Arena arena : plugin.getArenaManager().getArenas()) {
            String arenaName = arena.getName();

            // Cache total arena players
            int totalPlayers = arena.getPlayerCount();
            cachedCounts.put("arena:" + arenaName, totalPlayers);

            // Cache center spawn players
            int centerPlayers = arena.getPlayerCountAtSpawn("center");
            cachedCounts.put("spawn:" + arenaName + ":center", centerPlayers);

            // Cache named spawn players
            for (String spawnName : arena.getNamedSpawns().keySet()) {
                int spawnPlayers = arena.getPlayerCountAtSpawn(spawnName);
                cachedCounts.put("spawn:" + arenaName + ":" + spawnName, spawnPlayers);
            }
        }

        lastCacheUpdate = now;
    }
}