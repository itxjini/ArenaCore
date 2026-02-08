package com.arenacore;

import com.arenacore.arena.ArenaManager;
import com.arenacore.commands.ArenaCommand;
import com.arenacore.commands.BackCommand;
import com.arenacore.commands.SpawnCommand;
import com.arenacore.config.ConfigManager;
import com.arenacore.death.DeathManager;
import com.arenacore.gui.SpawnGUIManager;
import com.arenacore.listeners.ArenaListener;
import com.arenacore.listeners.DeathListener;
import com.arenacore.listeners.GUIListener;
import com.arenacore.placeholders.ArenaPlaceholders;
import com.arenacore.regeneration.RegenerationManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ArenaCore FFA - Free-For-All Arena System
 *
 * High-performance FFA arena plugin with spawn selection,
 * death handling, and PlaceholderAPI integration.
 *
 * @author ArenaCore Team
 * @version 2.0.0 (FFA Edition)
 * @since Java 21+
 */
public final class ArenaCore extends JavaPlugin {

    private static ArenaCore instance;

    private ConfigManager configManager;
    private ArenaManager arenaManager;
    private RegenerationManager regenerationManager;
    private DeathManager deathManager;
    private SpawnGUIManager guiManager;

    @Override
    public void onEnable() {
        instance = this;

        // ASCII Art Banner
        getLogger().info("╔═══════════════════════════════════════╗");
        getLogger().info("║      ArenaCore FFA v2.0.0             ║");
        getLogger().info("║   Free-For-All Arena System           ║");
        getLogger().info("╚═══════════════════════════════════════╝");

        // Check for FAWE
        if (!checkDependencies()) {
            getLogger().severe("FastAsyncWorldEdit not found! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        initializeManagers();

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        // Register PlaceholderAPI if available
        registerPlaceholders();

        getLogger().info("ArenaCore FFA has been enabled successfully!");
        getLogger().info("Loaded " + arenaManager.getArenaCount() + " arena(s)");
    }

    @Override
    public void onDisable() {
        if (regenerationManager != null) {
            regenerationManager.shutdown();
        }

        if (arenaManager != null) {
            arenaManager.saveAll();
        }

        if (deathManager != null) {
            deathManager.cleanup();
        }

        getLogger().info("ArenaCore FFA has been disabled successfully!");
    }

    private boolean checkDependencies() {
        // Check for FastAsyncWorldEdit
        if (getServer().getPluginManager().getPlugin("FastAsyncWorldEdit") == null) {
            return false;
        }

        // Check for PlaceholderAPI (optional)
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI detected - integration enabled");
        }

        return true;
    }

    private void initializeManagers() {
        getLogger().info("Initializing managers...");

        this.configManager = new ConfigManager(this);
        this.arenaManager = new ArenaManager(this);
        this.regenerationManager = new RegenerationManager(this);
        this.deathManager = new DeathManager(this);
        this.guiManager = new SpawnGUIManager(this);

        // Load arenas
        arenaManager.loadArenas();

        // Start regeneration scheduler
        regenerationManager.start();
    }

    private void registerCommands() {
        getLogger().info("Registering commands...");

        ArenaCommand arenaCommand = new ArenaCommand(this);
        getCommand("arena").setExecutor(arenaCommand);
        getCommand("arena").setTabCompleter(arenaCommand);

        SpawnCommand spawnCommand = new SpawnCommand(this);
        getCommand("spawn").setExecutor(spawnCommand);

        BackCommand backCommand = new BackCommand(this);
        getCommand("back").setExecutor(backCommand);
    }

    private void registerListeners() {
        getLogger().info("Registering event listeners...");

        getServer().getPluginManager().registerEvents(new ArenaListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ArenaPlaceholders(this).register();
            getLogger().info("PlaceholderAPI placeholders registered");
        }
    }

    // Getters
    public static ArenaCore getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public RegenerationManager getRegenerationManager() {
        return regenerationManager;
    }

    public DeathManager getDeathManager() {
        return deathManager;
    }

    public SpawnGUIManager getGuiManager() {
        return guiManager;
    }
}