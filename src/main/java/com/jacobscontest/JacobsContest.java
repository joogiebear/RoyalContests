package com.jacobscontest;

import com.jacobscontest.commands.ContestCommand;
import com.jacobscontest.contest.ContestManager;
import com.jacobscontest.gui.MenuListener;
import com.jacobscontest.listeners.CropBreakListener;
import com.jacobscontest.placeholders.ContestPlaceholders;
import com.jacobscontest.storage.MySQLStorage;
import com.jacobscontest.storage.SQLiteStorage;
import com.jacobscontest.storage.StorageProvider;
import com.jacobscontest.util.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class JacobsContest extends JavaPlugin {

    private static JacobsContest instance;
    private ContestManager contestManager;
    private ConfigManager configManager;
    private StorageProvider storage;

    @Override
    public void onEnable() {
        instance = this;

        // Load all config files
        configManager = new ConfigManager(this);
        configManager.loadAll();

        // Initialize storage (sqlite or mysql)
        String storageType = getConfig().getString("storage.type", "sqlite").toLowerCase();
        if ("mysql".equals(storageType)) {
            getLogger().info("Using MySQL storage...");
            storage = new MySQLStorage(this);
        } else {
            getLogger().info("Using SQLite storage...");
            storage = new SQLiteStorage(this);
        }
        storage.init();

        contestManager = new ContestManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new CropBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(this), this);

        // Register commands
        ContestCommand cmdHandler = new ContestCommand(this);
        getCommand("jacobscontest").setExecutor(cmdHandler);
        getCommand("jacobscontest").setTabCompleter(cmdHandler);
        getCommand("contests").setExecutor(cmdHandler);

        // Register PlaceholderAPI expansion
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ContestPlaceholders(this).register();
            getLogger().info("PlaceholderAPI expansion registered!");
        }

        getLogger().info("Jacob's Contest has been enabled!");
    }

    @Override
    public void onDisable() {
        if (contestManager != null && contestManager.isContestActive()) {
            contestManager.forceStop();
        }
        if (storage != null) {
            storage.shutdown();
        }
        getLogger().info("Jacob's Contest has been disabled!");
    }

    public static JacobsContest getInstance() {
        return instance;
    }

    public ContestManager getContestManager() {
        return contestManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public StorageProvider getStorage() {
        return storage;
    }
}
