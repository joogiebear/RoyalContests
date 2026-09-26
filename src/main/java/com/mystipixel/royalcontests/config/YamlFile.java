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
 * <p>The bundled copy is written out on first run so there is something to edit. For files with a fixed
 * shape (config, messages, menus) it is also set as the loaded file's <em>defaults</em>, so an update that
 * adds a setting never leaves a blank: a key missing from disk reads as the shipped value. Files that are
 * lists of things (crops, events, brackets) are read without defaults — otherwise an entry the admin
 * deleted would keep coming back from the shipped example.
 *
 * <p>A file that fails to parse is never overwritten. The error is logged and the shipped copy is used
 * until it is fixed, so a stray tab costs a warning, not the admin's edits.
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
