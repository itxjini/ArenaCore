package com.arenacore.gui;

import com.arenacore.ArenaCore;
import com.arenacore.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages spawn selection GUIs for arenas with multiple spawns.
 */
public class SpawnGUIManager {

    private final ArenaCore plugin;
    private final Map<String, Inventory> cachedGUIs;
    private final Map<UUID, String> pendingSelections; // Player -> Arena name

    public SpawnGUIManager(ArenaCore plugin) {
        this.plugin = plugin;
        this.cachedGUIs = new ConcurrentHashMap<>();
        this.pendingSelections = new ConcurrentHashMap<>();
    }

    /**
     * Opens the spawn selection GUI for a player.
     */
    public void openSpawnGUI(Player player, Arena arena) {
        if (!arena.hasMultipleSpawns()) {
            plugin.getLogger().warning("Attempted to open GUI for arena without multiple spawns: " + arena.getName());
            return;
        }

        Inventory gui = getOrCreateGUI(arena);
        pendingSelections.put(player.getUniqueId(), arena.getName());

        // Schedule on main thread
        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(gui));
    }

    /**
     * Gets or creates a cached GUI for an arena.
     */
    private Inventory getOrCreateGUI(Arena arena) {
        String arenaName = arena.getName();

        // Return cached if exists
        if (cachedGUIs.containsKey(arenaName)) {
            return cachedGUIs.get(arenaName);
        }

        // Create new GUI
        Arena.SpawnGUIConfig config = arena.getGuiConfig();
        String title = ChatColor.translateAlternateColorCodes('&', config.getTitle());
        int size = config.getRows() * 9;

        Inventory gui = Bukkit.createInventory(null, size, title);

        // Fill with background
        ItemStack fillItem = createItem(config.getFillMaterial(), " ", Collections.emptyList());
        for (int i = 0; i < size; i++) {
            gui.setItem(i, fillItem);
        }

        // Add center spawn
        ItemStack centerItem = createItem(
                "EMERALD",
                "&a&lCenter Spawn",
                Arrays.asList("&7Click to spawn at center")
        );
        int centerSlot = (size / 2); // Middle slot
        gui.setItem(centerSlot, centerItem);

        // Add named spawns
        for (Map.Entry<String, Arena.SpawnGUIItem> entry : config.getSpawnItems().entrySet()) {
            Arena.SpawnGUIItem item = entry.getValue();

            if (item.getSlot() >= 0 && item.getSlot() < size) {
                ItemStack spawnItem = createItem(
                        item.getMaterial(),
                        item.getName(),
                        item.getLore()
                );
                gui.setItem(item.getSlot(), spawnItem);
            }
        }

        // Cache and return
        cachedGUIs.put(arenaName, gui);
        return gui;
    }

    /**
     * Handles a player clicking in a spawn GUI.
     * Returns the spawn name if valid, null otherwise.
     */
    public String handleGUIClick(Player player, Inventory inventory, int slot) {
        String arenaName = pendingSelections.get(player.getUniqueId());
        if (arenaName == null) {
            return null;
        }

        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null) {
            pendingSelections.remove(player.getUniqueId());
            return null;
        }

        // Check if clicked on center spawn
        Arena.SpawnGUIConfig config = arena.getGuiConfig();
        int size = config.getRows() * 9;
        int centerSlot = size / 2;

        if (slot == centerSlot) {
            pendingSelections.remove(player.getUniqueId());
            return "center";
        }

        // Check named spawns
        for (Map.Entry<String, Arena.SpawnGUIItem> entry : config.getSpawnItems().entrySet()) {
            if (entry.getValue().getSlot() == slot) {
                String spawnName = entry.getKey();

                // Verify spawn exists
                if (arena.getNamedSpawn(spawnName) != null) {
                    pendingSelections.remove(player.getUniqueId());
                    return spawnName;
                }
            }
        }

        return null;
    }

    /**
     * Checks if a player has a pending spawn selection.
     */
    public boolean hasPendingSelection(UUID playerId) {
        return pendingSelections.containsKey(playerId);
    }

    /**
     * Gets the arena name for a pending selection.
     */
    public String getPendingArena(UUID playerId) {
        return pendingSelections.get(playerId);
    }

    /**
     * Cancels a pending selection.
     */
    public void cancelSelection(UUID playerId) {
        pendingSelections.remove(playerId);
    }

    /**
     * Invalidates cached GUI for an arena.
     */
    public void invalidateGUI(String arenaName) {
        cachedGUIs.remove(arenaName);
    }

    /**
     * Clears all cached GUIs.
     */
    public void clearCache() {
        cachedGUIs.clear();
    }

    /**
     * Creates an ItemStack with display name and lore.
     */
    private ItemStack createItem(String materialName, String displayName, List<String> lore) {
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(coloredLore);
            }

            item.setItemMeta(meta);
        }

        return item;
    }
}