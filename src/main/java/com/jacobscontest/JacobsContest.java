package com.jacobscontest;

import com.jacobscontest.commands.ContestCommand;
import com.jacobscontest.contest.ContestManager;
import com.jacobscontest.listeners.CropBreakListener;
import com.jacobscontest.placeholders.ContestPlaceholders;
import com.jacobscontest.util.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class JacobsContest extends JavaPlugin {

    private static JacobsContest instance;
    private ContestManager contestManager;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        instance = this;

        // Load all config files
        configManager = new ConfigManager(this);
        configManager.loadAll();

        contestManager = new ContestManager(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(new CropBreakListener(this), this);

        // Register commands
        getCommand("jacobscontest").setExecutor(new ContestCommand(this));
        getCommand("jacobscontest").setTabCompleter(new ContestCommand(this));

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
}
