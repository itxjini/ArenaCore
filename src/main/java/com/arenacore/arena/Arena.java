package com.arenacore.arena;

import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a Free-For-All arena with spawn points,
 * GUI configuration, and player tracking.
 */
public class Arena {

    private final String name;
    private final UUID uniqueId;
    private final World world;

    // Region data
    private Region region;
    private RegionShape shape;

    // FFA Spawn System
    private Location centerSpawn; // Mandatory
    private Map<String, Location> namedSpawns; // Optional spawns

    // Settings
    private boolean enabled;
    private String permission; // arena.join.<permission>

    // Inventory & state
    private boolean clearInventory;
    private boolean resetHealth;
    private boolean resetHunger;
    private String gamemodeOverride;

    // Kit & Effects
    private List<KitItem> kitItems;
    private Map<PotionEffectType, PotionConfig> potionEffects;
    private boolean rekitOnKill;

    // Spawn GUI Configuration
    private SpawnGUIConfig guiConfig;

    // Regeneration
    private RegenerationMode regenMode;
    private boolean regenEnabled;
    private int regenDelay;
    private double surfaceThreshold;
    private String regenDisplay;

    // Runtime state - Per player tracking
    private Map<UUID, PlayerArenaData> playersInside;
    private Set<BlockPosition> placedBlocks;
    private long lastRegenTime;
    private int blocksPlacedSinceRegen;

    // Surface tracking for smart regen
    private Map<BlockPosition, String> surfaceBlockMap;
    private int totalSurfaceBlocks;

    public Arena(String name, World world) {
        this.name = name;
        this.uniqueId = UUID.randomUUID();
        this.world = world;

        // Defaults
        this.enabled = true;
        this.clearInventory = true;
        this.resetHealth = true;
        this.resetHunger = true;
        this.gamemodeOverride = "SURVIVAL";
        this.rekitOnKill = true;

        // Collections
        this.namedSpawns = new LinkedHashMap<>();
        this.kitItems = new ArrayList<>();
        this.potionEffects = new HashMap<>();
        this.playersInside = new ConcurrentHashMap<>();
        this.placedBlocks = ConcurrentHashMap.newKeySet();
        this.surfaceBlockMap = new ConcurrentHashMap<>();

        // GUI Config
        this.guiConfig = new SpawnGUIConfig();

        // Regen defaults
        this.regenEnabled = true;
        this.regenMode = RegenerationMode.SMART;
        this.regenDelay = 300;
        this.surfaceThreshold = 0.40;
        this.regenDisplay = "ACTIONBAR";

        this.lastRegenTime = System.currentTimeMillis();
    }

    // Getters and setters
    public String getName() { return name; }
    public UUID getUniqueId() { return uniqueId; }
    public World getWorld() { return world; }

    public Region getRegion() { return region; }
    public void setRegion(Region region) { this.region = region; }

    public RegionShape getShape() { return shape; }
    public void setShape(RegionShape shape) { this.shape = shape; }

    public Location getCenterSpawn() { return centerSpawn; }
    public void setCenterSpawn(Location centerSpawn) { this.centerSpawn = centerSpawn; }

    public Map<String, Location> getNamedSpawns() { return namedSpawns; }
    public void addNamedSpawn(String name, Location location) {
        namedSpawns.put(name.toLowerCase(), location);
    }
    public void removeNamedSpawn(String name) {
        namedSpawns.remove(name.toLowerCase());
    }
    public Location getNamedSpawn(String name) {
        return namedSpawns.get(name.toLowerCase());
    }

    public boolean hasMultipleSpawns() {
        return !namedSpawns.isEmpty();
    }

    public int getTotalSpawns() {
        return 1 + namedSpawns.size(); // Center + named
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }

    public boolean shouldClearInventory() { return clearInventory; }
    public void setClearInventory(boolean clear) { this.clearInventory = clear; }

    public boolean shouldResetHealth() { return resetHealth; }
    public void setResetHealth(boolean reset) { this.resetHealth = reset; }

    public boolean shouldResetHunger() { return resetHunger; }
    public void setResetHunger(boolean reset) { this.resetHunger = reset; }

    public String getGamemodeOverride() { return gamemodeOverride; }
    public void setGamemodeOverride(String mode) { this.gamemodeOverride = mode; }

    public List<KitItem> getKitItems() { return kitItems; }
    public void setKitItems(List<KitItem> items) { this.kitItems = items; }

    public Map<PotionEffectType, PotionConfig> getPotionEffects() {
        return potionEffects;
    }

    public boolean isRekitOnKill() { return rekitOnKill; }
    public void setRekitOnKill(boolean rekit) { this.rekitOnKill = rekit; }

    public SpawnGUIConfig getGuiConfig() { return guiConfig; }
    public void setGuiConfig(SpawnGUIConfig config) { this.guiConfig = config; }

    public RegenerationMode getRegenMode() { return regenMode; }
    public void setRegenMode(RegenerationMode mode) { this.regenMode = mode; }

    public boolean isRegenEnabled() { return regenEnabled; }
    public void setRegenEnabled(boolean enabled) { this.regenEnabled = enabled; }

    public int getRegenDelay() { return regenDelay; }
    public void setRegenDelay(int delay) { this.regenDelay = delay; }

    public double getSurfaceThreshold() { return surfaceThreshold; }
    public void setSurfaceThreshold(double threshold) { this.surfaceThreshold = threshold; }

    public String getRegenDisplay() { return regenDisplay; }
    public void setRegenDisplay(String display) { this.regenDisplay = display; }

    public Map<UUID, PlayerArenaData> getPlayersInside() { return playersInside; }
    public Set<BlockPosition> getPlacedBlocks() { return placedBlocks; }

    public long getLastRegenTime() { return lastRegenTime; }
    public void setLastRegenTime(long time) { this.lastRegenTime = time; }

    public int getBlocksPlacedSinceRegen() { return blocksPlacedSinceRegen; }
    public void incrementBlocksPlaced() { this.blocksPlacedSinceRegen++; }
    public void resetBlocksPlaced() { this.blocksPlacedSinceRegen = 0; }

    public Map<BlockPosition, String> getSurfaceBlockMap() { return surfaceBlockMap; }
    public int getTotalSurfaceBlocks() { return totalSurfaceBlocks; }
    public void setTotalSurfaceBlocks(int count) { this.totalSurfaceBlocks = count; }

    // Player tracking methods
    public boolean hasPlayer(UUID uuid) {
        return playersInside.containsKey(uuid);
    }

    public void addPlayer(UUID uuid, String spawnName) {
        PlayerArenaData data = new PlayerArenaData(uuid, spawnName);
        playersInside.put(uuid, data);
    }

    public void removePlayer(UUID uuid) {
        playersInside.remove(uuid);
    }

    public PlayerArenaData getPlayerData(UUID uuid) {
        return playersInside.get(uuid);
    }

    public int getPlayerCount() {
        return playersInside.size();
    }

    public int getPlayerCountAtSpawn(String spawnName) {
        return (int) playersInside.values().stream()
                .filter(data -> spawnName.equalsIgnoreCase(data.getSpawnName()))
                .count();
    }

    // Block tracking
    public boolean isBlockPlaced(BlockPosition pos) {
        return placedBlocks.contains(pos);
    }

    public void addPlacedBlock(BlockPosition pos) {
        placedBlocks.add(pos);
    }

    public void removePlacedBlock(BlockPosition pos) {
        placedBlocks.remove(pos);
    }

    public void clearPlacedBlocks() {
        placedBlocks.clear();
    }

    public boolean isComplete() {
        return region != null && centerSpawn != null;
    }

    public double getSurfaceModificationPercentage() {
        if (totalSurfaceBlocks == 0) return 0.0;

        int modifiedBlocks = 0;
        for (BlockPosition pos : surfaceBlockMap.keySet()) {
            if (placedBlocks.contains(pos)) {
                modifiedBlocks++;
            }
        }

        return (double) modifiedBlocks / totalSurfaceBlocks;
    }

    // Enums
    public enum RegionShape {
        CUBOID, POLYGONAL, ELLIPSOID, SPHERE, CUSTOM
    }

    public enum RegenerationMode {
        DISABLED, TIMER, SMART
    }

    // Inner classes
    public static class PlayerArenaData {
        private final UUID playerId;
        private final String spawnName;
        private final long joinTime;
        private int kills;
        private int deaths;

        public PlayerArenaData(UUID playerId, String spawnName) {
            this.playerId = playerId;
            this.spawnName = spawnName;
            this.joinTime = System.currentTimeMillis();
            this.kills = 0;
            this.deaths = 0;
        }

        public UUID getPlayerId() { return playerId; }
        public String getSpawnName() { return spawnName; }
        public long getJoinTime() { return joinTime; }
        public int getKills() { return kills; }
        public int getDeaths() { return deaths; }

        public void incrementKills() { kills++; }
        public void incrementDeaths() { deaths++; }
    }

    public static class KitItem {
        private final String material;
        private final int amount;
        private final int slot;
        private final String displayName;
        private final List<String> lore;

        public KitItem(String material, int amount, int slot, String displayName, List<String> lore) {
            this.material = material;
            this.amount = amount;
            this.slot = slot;
            this.displayName = displayName;
            this.lore = lore;
        }

        public String getMaterial() { return material; }
        public int getAmount() { return amount; }
        public int getSlot() { return slot; }
        public String getDisplayName() { return displayName; }
        public List<String> getLore() { return lore; }
    }

    public static class PotionConfig {
        private final PotionEffectType type;
        private final int level;
        private final int duration; // -1 for infinite

        public PotionConfig(PotionEffectType type, int level, int duration) {
            this.type = type;
            this.level = level;
            this.duration = duration;
        }

        public PotionEffect createEffect() {
            if (duration == -1) {
                return new PotionEffect(type, Integer.MAX_VALUE, level, false, false);
            }
            return new PotionEffect(type, duration * 20, level, false, false);
        }

        public PotionEffectType getType() { return type; }
        public int getLevel() { return level; }
        public int getDuration() { return duration; }
    }

    public static class SpawnGUIConfig {
        private String title;
        private int rows;
        private String fillMaterial;
        private Map<String, SpawnGUIItem> spawnItems;

        public SpawnGUIConfig() {
            this.title = "&cFFA Spawn Selector";
            this.rows = 3;
            this.fillMaterial = "BLACK_STAINED_GLASS_PANE";
            this.spawnItems = new HashMap<>();
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public int getRows() { return rows; }
        public void setRows(int rows) { this.rows = Math.max(1, Math.min(6, rows)); }

        public String getFillMaterial() { return fillMaterial; }
        public void setFillMaterial(String material) { this.fillMaterial = material; }

        public Map<String, SpawnGUIItem> getSpawnItems() { return spawnItems; }
        public void addSpawnItem(String spawnName, SpawnGUIItem item) {
            spawnItems.put(spawnName.toLowerCase(), item);
        }
    }

    public static class SpawnGUIItem {
        private final int slot;
        private final String material;
        private final String name;
        private final List<String> lore;

        public SpawnGUIItem(int slot, String material, String name, List<String> lore) {
            this.slot = slot;
            this.material = material;
            this.name = name;
            this.lore = lore;
        }

        public int getSlot() { return slot; }
        public String getMaterial() { return material; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
    }

    public static class BlockPosition {
        private final int x, y, z;

        public BlockPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockPosition)) return false;
            BlockPosition that = (BlockPosition) o;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}