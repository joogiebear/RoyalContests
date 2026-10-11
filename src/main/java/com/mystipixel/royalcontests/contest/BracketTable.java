package com.mystipixel.royalcontests.contest;

import com.mystipixel.royalcontests.config.YamlFile;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The reward brackets, best first, from {@code brackets.yml}.
 *
 * <p>File order is ladder order: the first bracket a player reaches is theirs.
 */
public final class BracketTable {

    private final YamlFile file;
    private final List<Bracket> brackets = new ArrayList<>();

    public BracketTable(Plugin plugin) {
        this.file = new YamlFile(plugin, "brackets.yml", false);
        load();
    }

    public void reload() {
        file.reload();
        load();
    }

    private void load() {
        brackets.clear();
        ConfigurationSection root = file.yaml().getConfigurationSection("brackets");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection b = root.getConfigurationSection(id);
            if (b == null) {
                continue;
            }
            brackets.add(new Bracket(id.toLowerCase(Locale.ROOT),
                    b.getString("display-name", id),
                    b.getDouble("top-percent", 0),
                    b.getInt("top-positions", 0),
                    b.getStringList("commands"),
                    b.getStringList("messages")));
        }
    }

    public List<Bracket> all() {
        return Collections.unmodifiableList(brackets);
    }

    public Bracket get(String id) {
        for (Bracket bracket : brackets) {
            if (bracket.id().equalsIgnoreCase(id)) {
                return bracket;
            }
        }
        return null;
    }

    /** A bracket's place in the ladder, 0 being best; unknown ids rank below every real one. */
    public int rank(String id) {
        for (int i = 0; i < brackets.size(); i++) {
            if (brackets.get(i).id().equalsIgnoreCase(id)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }
}
