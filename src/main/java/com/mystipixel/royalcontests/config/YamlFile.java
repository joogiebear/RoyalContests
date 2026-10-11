package com.mystipixel.royalcontests.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * One editable YAML file in the plugin folder, backed by the copy in the jar.
 *
 * <p>Fixed-shape files (config, messages, menus) use the shipped copy as defaults, so a new setting never
 * reads blank. List files (crops, events, brackets) don't, or an entry the admin deleted would come back.
 *
 * <p>A file that fails to parse is never overwritten; the shipped copy is used until it is fixed.
 */
public class YamlFile {

    private final Plugin plugin;
    private final String name;
    private final boolean withDefaults;
    private YamlConfiguration yaml = new YamlConfiguration();

    public YamlFile(Plugin plugin, String name, boolean withDefaults) {
        this.plugin = plugin;
        this.name = name;
        this.withDefaults = withDefaults;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists() && plugin.getResource(name) != null) {
            plugin.saveResource(name, false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            if (file.exists()) {
                loaded.load(file);
            } else {
                loaded = bundled();
            }
        } catch (IOException | InvalidConfigurationException broken) {
            plugin.getLogger().severe(name + " could not be read, so the shipped copy is in use until it is "
                    + "fixed: " + broken.getMessage());
            loaded = bundled();
        }
        if (withDefaults) {
            loaded.setDefaults(bundled());
        }
        this.yaml = loaded;
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public String name() {
        return name;
    }

    private YamlConfiguration bundled() {
        try (InputStream in = plugin.getResource(name)) {
            if (in == null) {
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            return new YamlConfiguration();
        }
    }
}
