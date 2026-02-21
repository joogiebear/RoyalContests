package com.jacobscontest.util;

import com.jacobscontest.JacobsContest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * Manages loading of multiple config files: config.yml, lang.yml, brackets.yml, events.yml
 */
public class ConfigManager {

    private final JacobsContest plugin;
    private FileConfiguration langConfig;
    private FileConfiguration bracketsConfig;
    private FileConfiguration eventsConfig;

    public ConfigManager(JacobsContest plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        // Save defaults if they don't exist
        plugin.saveDefaultConfig();
        saveDefault("lang.yml");
        saveDefault("brackets.yml");
        saveDefault("events.yml");

        // Reload all
        plugin.reloadConfig();
        langConfig = loadYaml("lang.yml");
        bracketsConfig = loadYaml("brackets.yml");
        eventsConfig = loadYaml("events.yml");
    }

    public void reloadAll() {
        plugin.reloadConfig();
        langConfig = loadYaml("lang.yml");
        bracketsConfig = loadYaml("brackets.yml");
        eventsConfig = loadYaml("events.yml");
    }

    private void saveDefault(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
    }

    private FileConfiguration loadYaml(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        return YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration getConfig() {
        return plugin.getConfig();
    }

    public FileConfiguration getLang() {
        return langConfig;
    }

    public FileConfiguration getBrackets() {
        return bracketsConfig;
    }

    public FileConfiguration getEvents() {
        return eventsConfig;
    }

    /**
     * Get a message from lang.yml with fallback.
     */
    public String getMessage(String key) {
        return langConfig.getString(key, "&cMissing message: " + key);
    }
}
