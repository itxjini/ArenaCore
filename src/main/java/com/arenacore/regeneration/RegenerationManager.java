package com.arenacore.regeneration;

import com.arenacore.ArenaCore;
import com.arenacore.arena.Arena;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Manages all arena regeneration operations using FAWE for
 * high-performance async block operations.
 */
public class RegenerationManager {

    private final ArenaCore plugin;
    private final ExecutorService asyncExecutor;
    private final Map<String, Clipboard> clipboards;
    private final Map<String, BossBar> regenBossBars;
    private final File clipboardsFolder;

    private BukkitRunnable schedulerTask;

    public RegenerationManager(ArenaCore plugin) {
        this.plugin = plugin;
        this.asyncExecutor = Executors.newFixedThreadPool(4);
        this.clipboards = new ConcurrentHashMap<>();
        this.regenBossBars = new ConcurrentHashMap<>();
        this.clipboardsFolder = new File(plugin.getDataFolder(), "clipboards");

        if (!clipboardsFolder.exists()) {
            clipboardsFolder.mkdirs();
        }
    }

    /**
     * Starts the regeneration scheduler that monitors arenas.
     */
    public void start() {
        schedulerTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkArenaRegenerations();
            }
        };

        schedulerTask.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Stops the regeneration scheduler and executor.
     */
    public void shutdown() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
        }

        asyncExecutor.shutdown();

        for (BossBar bar : regenBossBars.values()) {
            bar.removeAll();
        }
        regenBossBars.clear();
    }

    /**
     * Checks all arenas for regeneration triggers.
     */
    private void checkArenaRegenerations() {
        for (Arena arena : plugin.getArenaManager().getArenas()) {
            if (!arena.isRegenEnabled() || !arena.isEnabled()) {
                continue;
            }

            if (shouldRegenerate(arena)) {
                regenerateArena(arena);
            } else {
                updateRegenDisplay(arena);
            }
        }
    }

    /**
     * Determines if an arena should be regenerated based on its mode.
     */
    private boolean shouldRegenerate(Arena arena) {
        switch (arena.getRegenMode()) {
            case DISABLED:
                return false;

            case TIMER:
                long elapsed = (System.currentTimeMillis() - arena.getLastRegenTime()) / 1000;
                return elapsed >= arena.getRegenDelay();

            case SMART:
                return checkSmartRegeneration(arena);

            default:
                return false;
        }
    }

    /**
     * Smart regeneration logic based on surface modification and activity.
     */
    private boolean checkSmartRegeneration(Arena arena) {
        if (arena.getPlayersInside().isEmpty()) {
            return false;
        }

        double modification = arena.getSurfaceModificationPercentage();
        if (modification >= arena.getSurfaceThreshold()) {
            return true;
        }

        if (arena.getBlocksPlacedSinceRegen() > 0) {
            long combatDuration = (System.currentTimeMillis() - arena.getLastRegenTime()) / 1000;

            if (combatDuration > 600 && arena.getBlocksPlacedSinceRegen() > 100) {
                return true;
            }
        }

        return false;
    }

    /**
     * Updates the regeneration countdown display for players in the arena.
     */
    private void updateRegenDisplay(Arena arena) {
        if (arena.getPlayersInside().isEmpty()) {
            return;
        }

        String display = arena.getRegenDisplay();
        if (display == null || display.equals("NONE")) {
            return;
        }

        String message = getRegenMessage(arena);

        for (UUID playerId : arena.getPlayersInside().keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                continue;
            }

            switch (display.toUpperCase()) {
                case "ACTIONBAR":
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(message));
                    break;

                case "BOSSBAR":
                    updateBossBar(arena, player, message);
                    break;

                case "TITLE":
                    if (shouldShowTitle(arena)) {
                        player.sendTitle("", message, 0, 20, 10);
                    }
                    break;
            }
        }
    }

    /**
     * Gets the regeneration status message for an arena.
     */
    private String getRegenMessage(Arena arena) {
        switch (arena.getRegenMode()) {
            case TIMER:
                long elapsed = (System.currentTimeMillis() - arena.getLastRegenTime()) / 1000;
                long remaining = arena.getRegenDelay() - elapsed;
                return "§7Arena resets in: §e" + formatTime(remaining);

            case SMART:
                double modification = arena.getSurfaceModificationPercentage() * 100;
                return String.format("§7Surface modified: §e%.1f%%§7/§c%.1f%%",
                        modification, arena.getSurfaceThreshold() * 100);

            default:
                return "";
        }
    }

    /**
     * Updates or creates a boss bar for regeneration display.
     */
    private void updateBossBar(Arena arena, Player player, String message) {
        String barKey = arena.getName();
        BossBar bar = regenBossBars.get(barKey);

        if (bar == null) {
            bar = Bukkit.createBossBar(message, BarColor.YELLOW, BarStyle.SOLID);
            regenBossBars.put(barKey, bar);
        }

        bar.setTitle(message);

        double progress = getRegenProgress(arena);
        bar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    /**
     * Calculates regeneration progress (0.0 to 1.0).
     */
    private double getRegenProgress(Arena arena) {
        switch (arena.getRegenMode()) {
            case TIMER:
                long elapsed = (System.currentTimeMillis() - arena.getLastRegenTime()) / 1000;
                return (double) elapsed / arena.getRegenDelay();

            case SMART:
                return arena.getSurfaceModificationPercentage() / arena.getSurfaceThreshold();

            default:
                return 0.0;
        }
    }

    /**
     * Determines if a title should be shown (to avoid spam).
     */
    private boolean shouldShowTitle(Arena arena) {
        double progress = getRegenProgress(arena);
        return Math.abs(progress - 0.5) < 0.05 ||
                Math.abs(progress - 0.75) < 0.05 ||
                Math.abs(progress - 0.90) < 0.05;
    }

    /**
     * Formats seconds into a readable time string.
     */
    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes + "m " + secs + "s";
    }

    /**
     * Saves an arena's current state as a clipboard for future regeneration.
     */
    public CompletableFuture<Boolean> saveArenaSnapshot(Arena arena) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Region region = arena.getRegion();
                if (region == null) {
                    return false;
                }

                com.sk89q.worldedit.world.World weWorld =
                        BukkitAdapter.adapt(arena.getWorld());

                // Create clipboard using WorldEdit
                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(weWorld)
                        .build()) {

                    Clipboard clipboard = new com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard(region);
                    clipboard.setOrigin(region.getMinimumPoint());

                    com.sk89q.worldedit.function.operation.ForwardExtentCopy copy =
                            new com.sk89q.worldedit.function.operation.ForwardExtentCopy(
                                    editSession, region, clipboard, region.getMinimumPoint()
                            );

                    Operations.complete(copy);
                    clipboards.put(arena.getName(), clipboard);

                    // Save to file
                    File clipboardFile = new File(clipboardsFolder, arena.getName() + ".schem");
                    ClipboardFormat format = ClipboardFormats.findByFile(clipboardFile);

                    if (format != null) {
                        try (FileOutputStream fos = new FileOutputStream(clipboardFile)) {
                            format.getWriter(fos).write(clipboard);
                        }
                    }

                    plugin.getLogger().info("Saved snapshot for arena: " + arena.getName());
                    return true;
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save arena snapshot: " + arena.getName(), e);
                return false;
            }
        }, asyncExecutor);
    }

    /**
     * Loads an arena's clipboard snapshot from file.
     */
    public CompletableFuture<Boolean> loadArenaSnapshot(Arena arena) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File clipboardFile = new File(clipboardsFolder, arena.getName() + ".schem");

                if (!clipboardFile.exists()) {
                    plugin.getLogger().warning("Clipboard file not found for arena: " +
                            arena.getName());
                    return false;
                }

                ClipboardFormat format = ClipboardFormats.findByFile(clipboardFile);
                if (format == null) {
                    return false;
                }

                try (FileInputStream fis = new FileInputStream(clipboardFile);
                     ClipboardReader reader = format.getReader(fis)) {

                    Clipboard clipboard = reader.read();
                    clipboards.put(arena.getName(), clipboard);

                    plugin.getLogger().info("Loaded snapshot for arena: " + arena.getName());
                    return true;
                }

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to load arena snapshot: " + arena.getName(), e);
                return false;
            }
        }, asyncExecutor);
    }

    /**
     * Regenerates an arena using its saved clipboard.
     */
    public CompletableFuture<Boolean> regenerateArena(Arena arena) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Clipboard clipboard = clipboards.get(arena.getName());
                if (clipboard == null) {
                    plugin.getLogger().warning("No clipboard found for arena: " +
                            arena.getName());
                    return false;
                }

                notifyPlayers(arena, "§eArena is regenerating...");

                com.sk89q.worldedit.world.World weWorld =
                        BukkitAdapter.adapt(arena.getWorld());

                try (EditSession editSession = WorldEdit.getInstance()
                        .newEditSessionBuilder()
                        .world(weWorld)
                        .fastMode(true)
                        .build()) {

                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(clipboard.getOrigin())
                            .ignoreAirBlocks(false)
                            .build();

                    Operations.complete(operation);
                }

                arena.clearPlacedBlocks();
                arena.resetBlocksPlaced();
                arena.setLastRegenTime(System.currentTimeMillis());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    notifyPlayers(arena, "§aArena regenerated!");
                });

                plugin.getLogger().info("Regenerated arena: " + arena.getName());
                return true;

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to regenerate arena: " + arena.getName(), e);
                return false;
            }
        }, asyncExecutor);
    }

    /**
     * Sends a message to all players in an arena.
     */
    private void notifyPlayers(Arena arena, String message) {
        for (UUID playerId : arena.getPlayersInside().keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Gets the clipboard for an arena.
     */
    public Clipboard getClipboard(String arenaName) {
        return clipboards.get(arenaName);
    }

    /**
     * Checks if an arena has a saved clipboard.
     */
    public boolean hasClipboard(String arenaName) {
        return clipboards.containsKey(arenaName);
    }
}