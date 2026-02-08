package com.arenacore.config;

import com.arenacore.ArenaCore;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Manages FFA arena configuration.
 */
public class ConfigManager {

    private final ArenaCore plugin;
    private FileConfiguration config;

    public ConfigManager(ArenaCore plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
        setDefaults();
        plugin.saveConfig();
    }

    private void setDefaults() {
        // Spawn command settings
        addDefault("spawn.delay", 3);
        addDefault("spawn.clear-inventory", false);
        addDefault("spawn.messages.teleporting", "&7Teleporting to spawn in &e{time} &7seconds...");
        addDefault("spawn.messages.move-cancel", "&cTeleport cancelled - you moved!");
        addDefault("spawn.messages.success", "&aYou've been teleported to spawn!");

        // Death settings
        addDefault("death.animation.enabled", true);
        addDefault("death.animation.lightning", true);
        addDefault("death.animation.freeze-duration", 20);
        addDefault("death.title.enabled", true);
        addDefault("death.title.text", "&c&lYOU DIED");
        addDefault("death.title.subtitle", "&7Respawning...");

        // Respawn behavior
        addDefault("respawn.behavior", "GUI");
        addDefault("respawn.gui-timeout", 100);
        addDefault("respawn.timeout-message", "&7No spawn selected - sending to center");

        // Back command settings
        addDefault("back.enabled", true);
        addDefault("back.cooldown", 120);
        addDefault("back.messages.no-data", "&cYou have no recent arena death!");
        addDefault("back.messages.expired", "&cYour death location has expired!");
        addDefault("back.messages.cooldown", "&cYou can use /back in &e{time}");
        addDefault("back.messages.arena-unavailable", "&cThat arena is no longer available!");
        addDefault("back.messages.success", "&aReturned to &e{arena}");

        // Arena join messages
        addDefault("arena.messages.join", "&7Entered &e{arena} &7at &e{spawn} &7spawn");
        addDefault("arena.messages.leave", "&7Left arena &e{arena}");
        addDefault("arena.messages.no-permission", "&cYou don't have permission for this arena!");
        addDefault("arena.messages.disabled", "&cThis arena is currently disabled!");

        // Arena defaults
        addDefault("arena-defaults.clear-inventory", true);
        addDefault("arena-defaults.reset-health", true);
        addDefault("arena-defaults.reset-hunger", true);
        addDefault("arena-defaults.gamemode-override", "SURVIVAL");
        addDefault("arena-defaults.rekit-on-kill", true);
        addDefault("arena-defaults.enabled", true);

        // Regeneration defaults
        addDefault("regeneration.default-mode", "SMART");
        addDefault("regeneration.default-delay", 300);
        addDefault("regeneration.default-surface-threshold", 0.40);
        addDefault("regeneration.default-display", "ACTIONBAR");

        // Performance
        addDefault("performance.async-threads", 4);
        addDefault("performance.max-concurrent-regens", 3);
        addDefault("performance.scheduler-interval", 20);
        addDefault("performance.placeholder-cache-duration", 1000);
    }

    private void addDefault(String path, Object value) {
        if (!config.contains(path)) {
            config.set(path, value);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // Spawn command
    public int getSpawnDelay() {
        return config.getInt("spawn.delay", 3);
    }

    public boolean shouldClearInventoryOnSpawn() {
        return config.getBoolean("spawn.clear-inventory", false);
    }

    public String getSpawnTeleportMessage() {
        return config.getString("spawn.messages.teleporting", "&7Teleporting in &e{time}s...");
    }

    public String getSpawnMoveCancelMessage() {
        return config.getString("spawn.messages.move-cancel", "&cTeleport cancelled!");
    }

    public String getSpawnSuccessMessage() {
        return config.getString("spawn.messages.success", "&aTeleported to spawn!");
    }

    // Death settings
    public boolean isDeathAnimationEnabled() {
        return config.getBoolean("death.animation.enabled", true);
    }

    public boolean useDeathLightning() {
        return config.getBoolean("death.animation.lightning", true);
    }

    public int getDeathFreezeDuration() {
        return config.getInt("death.animation.freeze-duration", 20);
    }

    public boolean isDeathTitleEnabled() {
        return config.getBoolean("death.title.enabled", true);
    }

    public String getDeathTitle() {
        return config.getString("death.title.text", "&c&lYOU DIED");
    }

    public String getDeathSubtitle() {
        return config.getString("death.title.subtitle", "&7Respawning...");
    }

    // Respawn behavior
    public String getRespawnBehavior() {
        return config.getString("respawn.behavior", "GUI");
    }

    public int getGUITimeout() {
        return config.getInt("respawn.gui-timeout", 100);
    }

    public String getSpawnTimeoutMessage() {
        return config.getString("respawn.timeout-message", "&7Sending to center");
    }

    // Back command
    public boolean isBackEnabled() {
        return config.getBoolean("back.enabled", true);
    }

    public int getBackCooldown() {
        return config.getInt("back.cooldown", 120);
    }

    public String getBackNoDataMessage() {
        return config.getString("back.messages.no-data", "&cNo recent death!");
    }

    public String getBackExpiredMessage() {
        return config.getString("back.messages.expired", "&cExpired!");
    }

    public String getBackCooldownMessage() {
        return config.getString("back.messages.cooldown", "&cWait {time}");
    }

    public String getBackArenaUnavailableMessage() {
        return config.getString("back.messages.arena-unavailable", "&cArena unavailable!");
    }

    public String getBackSuccessMessage() {
        return config.getString("back.messages.success", "&aReturned to {arena}");
    }

    // Arena messages
    public String getArenaJoinMessage() {
        return config.getString("arena.messages.join", "&7Joined {arena}");
    }

    public String getArenaLeaveMessage() {
        return config.getString("arena.messages.leave", "&7Left {arena}");
    }

    public String getArenaNoPermissionMessage() {
        return config.getString("arena.messages.no-permission", "&cNo permission!");
    }

    public String getArenaDisabledMessage() {
        return config.getString("arena.messages.disabled", "&cDisabled!");
    }

    // Arena defaults
    public boolean getDefaultClearInventory() {
        return config.getBoolean("arena-defaults.clear-inventory", true);
    }

    public boolean getDefaultResetHealth() {
        return config.getBoolean("arena-defaults.reset-health", true);
    }

    public boolean getDefaultResetHunger() {
        return config.getBoolean("arena-defaults.reset-hunger", true);
    }

    public String getDefaultGamemodeOverride() {
        return config.getString("arena-defaults.gamemode-override", "SURVIVAL");
    }

    public boolean getDefaultRekitOnKill() {
        return config.getBoolean("arena-defaults.rekit-on-kill", true);
    }

    public boolean getDefaultEnabled() {
        return config.getBoolean("arena-defaults.enabled", true);
    }

    // Regeneration
    public String getDefaultRegenMode() {
        return config.getString("regeneration.default-mode", "SMART");
    }

    public int getDefaultRegenDelay() {
        return config.getInt("regeneration.default-delay", 300);
    }

    public double getDefaultSurfaceThreshold() {
        return config.getDouble("regeneration.default-surface-threshold", 0.40);
    }

    public String getDefaultRegenDisplay() {
        return config.getString("regeneration.default-display", "ACTIONBAR");
    }

    // Performance
    public int getAsyncThreads() {
        return config.getInt("performance.async-threads", 4);
    }

    public int getMaxConcurrentRegens() {
        return config.getInt("performance.max-concurrent-regens", 3);
    }

    public int getSchedulerInterval() {
        return config.getInt("performance.scheduler-interval", 20);
    }

    public long getPlaceholderCacheDuration() {
        return config.getLong("performance.placeholder-cache-duration", 1000);
    }

    public FileConfiguration getConfig() {
        return config;
    }
}