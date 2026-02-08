package com.arenacore.arena;

import com.arenacore.ArenaCore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central manager for FFA arena operations.
 */
public class ArenaManager {

    private final ArenaCore plugin;
    private final Map<String, Arena> arenas;
    private final Map<UUID, Arena> playerArenaMap;
    private final File arenasFolder;
    private Location lobbySpawn;

    public ArenaManager(ArenaCore plugin) {
        this.plugin = plugin;
        this.arenas = new ConcurrentHashMap<>();
        this.playerArenaMap = new ConcurrentHashMap<>();
        this.arenasFolder = new File(plugin.getDataFolder(), "arenas");

        if (!arenasFolder.exists()) {
            arenasFolder.mkdirs();
        }

        loadLobbySpawn();
    }

    public Arena createArena(String name, World world) {
        if (arenas.containsKey(name.toLowerCase())) {
            return null;
        }

        Arena arena = new Arena(name, world);
        arenas.put(name.toLowerCase(), arena);

        plugin.getLogger().info("Created arena: " + name);
        return arena;
    }

    public boolean deleteArena(String name) {
        Arena arena = arenas.remove(name.toLowerCase());
        if (arena == null) {
            return false;
        }

        for (UUID playerId : new HashSet<UUID>(arena.getPlayersInside().keySet())) {
            playerArenaMap.remove(playerId);
        }

        File arenaFile = new File(arenasFolder, name.toLowerCase() + ".yml");
        if (arenaFile.exists()) {
            arenaFile.delete();
        }

        plugin.getGuiManager().invalidateGUI(name);

        plugin.getLogger().info("Deleted arena: " + name);
        return true;
    }

    public Arena getArena(String name) {
        return arenas.get(name.toLowerCase());
    }

    public Arena getPlayerArena(Player player) {
        return playerArenaMap.get(player.getUniqueId());
    }

    public Arena getPlayerArena(UUID uuid) {
        return playerArenaMap.get(uuid);
    }

    public Collection<Arena> getArenas() {
        return arenas.values();
    }

    public Set<String> getArenaNames() {
        return arenas.keySet();
    }

    public int getArenaCount() {
        return arenas.size();
    }

    public boolean isInArena(Player player) {
        return playerArenaMap.containsKey(player.getUniqueId());
    }

    public void addPlayerToArena(Player player, Arena arena, String spawnName) {
        arena.addPlayer(player.getUniqueId(), spawnName);
        playerArenaMap.put(player.getUniqueId(), arena);
    }

    public void removePlayerFromArena(Player player) {
        Arena arena = playerArenaMap.remove(player.getUniqueId());
        if (arena != null) {
            arena.removePlayer(player.getUniqueId());
        }
    }

    public Arena getArenaAtLocation(Location location) {
        for (Arena arena : arenas.values()) {
            if (arena.getRegion() != null && isLocationInRegion(location, arena)) {
                return arena;
            }
        }
        return null;
    }

    private boolean isLocationInRegion(Location location, Arena arena) {
        if (arena.getRegion() == null || !arena.getWorld().equals(location.getWorld())) {
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

    public boolean validateSpawnPlacement(Arena arena, Location spawn) {
        return isLocationInRegion(spawn, arena);
    }

    // Lobby spawn management
    public Location getLobbySpawn() {
        return lobbySpawn;
    }

    public void setLobbySpawn(Location location) {
        this.lobbySpawn = location;
        saveLobbySpawn();
    }

    private void loadLobbySpawn() {
        File lobbyFile = new File(plugin.getDataFolder(), "lobby.yml");
        if (!lobbyFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(lobbyFile);
        if (config.contains("spawn")) {
            this.lobbySpawn = deserializeLocation(config.getConfigurationSection("spawn"));
        }
    }

    private void saveLobbySpawn() {
        File lobbyFile = new File(plugin.getDataFolder(), "lobby.yml");
        YamlConfiguration config = new YamlConfiguration();

        if (lobbySpawn != null) {
            serializeLocation(config.createSection("spawn"), lobbySpawn);
        }

        try {
            config.save(lobbyFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save lobby spawn", e);
        }
    }

    public void loadArenas() {
        File[] files = arenasFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("No arenas found to load");
            return;
        }

        int loaded = 0;
        for (File file : files) {
            try {
                if (loadArena(file)) {
                    loaded++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to load arena: " + file.getName(), e);
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " arena(s)");
    }

    private boolean loadArena(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        String name = config.getString("name");
        String worldName = config.getString("world");

        if (name == null || worldName == null) {
            return false;
        }

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("World not found for arena: " + name);
            return false;
        }

        Arena arena = new Arena(name, world);

        arena.setEnabled(config.getBoolean("enabled", true));
        arena.setPermission(config.getString("permission"));

        // Load center spawn
        if (config.contains("spawns.center")) {
            arena.setCenterSpawn(deserializeLocation(config.getConfigurationSection("spawns.center")));
        }

        // Load named spawns
        if (config.contains("spawns.named")) {
            ConfigurationSection namedSection = config.getConfigurationSection("spawns.named");
            for (String spawnName : namedSection.getKeys(false)) {
                Location loc = deserializeLocation(namedSection.getConfigurationSection(spawnName));
                arena.addNamedSpawn(spawnName, loc);
            }
        }

        arena.setClearInventory(config.getBoolean("clearInventory", true));
        arena.setResetHealth(config.getBoolean("resetHealth", true));
        arena.setResetHunger(config.getBoolean("resetHunger", true));
        arena.setGamemodeOverride(config.getString("gamemodeOverride", "SURVIVAL"));
        arena.setRekitOnKill(config.getBoolean("rekitOnKill", true));

        // Load GUI config
        if (config.contains("gui")) {
            ConfigurationSection guiSection = config.getConfigurationSection("gui");
            Arena.SpawnGUIConfig guiConfig = arena.getGuiConfig();
            guiConfig.setTitle(guiSection.getString("title", "&cFFA Spawn Selector"));
            guiConfig.setRows(guiSection.getInt("rows", 3));
            guiConfig.setFillMaterial(guiSection.getString("fill-item.material", "BLACK_STAINED_GLASS_PANE"));
        }

        // Load regeneration settings
        arena.setRegenEnabled(config.getBoolean("regen.enabled", true));
        String regenMode = config.getString("regen.mode", "SMART");
        arena.setRegenMode(Arena.RegenerationMode.valueOf(regenMode));
        arena.setRegenDelay(config.getInt("regen.delay", 300));
        arena.setSurfaceThreshold(config.getDouble("regen.surfaceThreshold", 0.40));
        arena.setRegenDisplay(config.getString("regen.display", "ACTIONBAR"));

        arenas.put(name.toLowerCase(), arena);
        return true;
    }

    public void saveAll() {
        for (Arena arena : arenas.values()) {
            saveArena(arena);
        }
    }

    public void saveArena(Arena arena) {
        File file = new File(arenasFolder, arena.getName().toLowerCase() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("name", arena.getName());
        config.set("world", arena.getWorld().getName());
        config.set("enabled", arena.isEnabled());
        config.set("permission", arena.getPermission());

        // Save center spawn
        if (arena.getCenterSpawn() != null) {
            serializeLocation(config.createSection("spawns.center"), arena.getCenterSpawn());
        }

        // Save named spawns
        if (!arena.getNamedSpawns().isEmpty()) {
            ConfigurationSection namedSection = config.createSection("spawns.named");
            for (Map.Entry<String, Location> entry : arena.getNamedSpawns().entrySet()) {
                serializeLocation(namedSection.createSection(entry.getKey()), entry.getValue());
            }
        }

        config.set("clearInventory", arena.shouldClearInventory());
        config.set("resetHealth", arena.shouldResetHealth());
        config.set("resetHunger", arena.shouldResetHunger());
        config.set("gamemodeOverride", arena.getGamemodeOverride());
        config.set("rekitOnKill", arena.isRekitOnKill());

        // Save GUI config
        Arena.SpawnGUIConfig guiConfig = arena.getGuiConfig();
        config.set("gui.title", guiConfig.getTitle());
        config.set("gui.rows", guiConfig.getRows());
        config.set("gui.fill-item.material", guiConfig.getFillMaterial());

        // Save regeneration
        config.set("regen.enabled", arena.isRegenEnabled());
        config.set("regen.mode", arena.getRegenMode().name());
        config.set("regen.delay", arena.getRegenDelay());
        config.set("regen.surfaceThreshold", arena.getSurfaceThreshold());
        config.set("regen.display", arena.getRegenDisplay());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to save arena: " + arena.getName(), e);
        }
    }

    private Location deserializeLocation(ConfigurationSection section) {
        if (section == null) return null;

        String worldName = section.getString("world");
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) return null;

        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    private void serializeLocation(ConfigurationSection section, Location loc) {
        section.set("world", loc.getWorld().getName());
        section.set("x", loc.getX());
        section.set("y", loc.getY());
        section.set("z", loc.getZ());
        section.set("yaw", loc.getYaw());
        section.set("pitch", loc.getPitch());
    }
}