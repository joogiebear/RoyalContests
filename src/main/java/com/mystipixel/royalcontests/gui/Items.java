package com.mystipixel.royalcontests.gui;

import com.mystipixel.royalcontests.config.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu items described in config: {@code material}, {@code name}, {@code lore}, and optionally
 * {@code slot}.
 *
 * <p>A lore line that is exactly one placeholder is dropped when the placeholder fills to nothing, so an
 * optional line leaves no gap; a placeholder may also fill to several lines, separated by {@code \n}.
 */
public final class Items {

    private Items() {
    }

    public static ItemStack build(ConfigurationSection section, Material fallback, String... placeholders) {
        return build(section, null, fallback, placeholders);
    }

    /** Build an item, forcing {@code material} if it is not null (a crop's own icon, say). */
    public static ItemStack build(ConfigurationSection section, Material material, Material fallback,
                                  String... placeholders) {
        if (section == null) {
            return plain(material != null ? material : fallback, "&c(missing menu item)", List.of());
        }
        Material type = material;
        if (type == null) {
            Material configured = Material.matchMaterial(section.getString("material", ""));
            type = configured != null && configured.isItem() ? configured : fallback;
        }
        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            String trimmed = line.trim();
            boolean optional = trimmed.startsWith("%") && trimmed.endsWith("%") && trimmed.indexOf('%', 1) == trimmed.length() - 1;
            String filled = Text.fill(line, placeholders);
            if (optional && filled.isBlank()) {
                continue;
            }
            for (String part : filled.split("\n", -1)) {
                lore.add(part);
            }
        }
        ItemStack item = plain(type, Text.fill(section.getString("name", ""), placeholders), lore);
        int amount = section.getInt("amount", 1);
        item.setAmount(Math.clamp(amount, 1, 64));
        return item;
    }

    public static ItemStack plain(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Text.item(name));
        List<Component> lines = new ArrayList<>(lore.size());
        for (String line : lore) {
            lines.add(Text.item(line));
        }
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /** The filler for empty slots, or null for none. */
    public static ItemStack filler(String material) {
        Material type = material == null || material.isBlank() ? null : Material.matchMaterial(material.trim());
        return type == null || !type.isItem() ? null : plain(type, " ", List.of());
    }
}
