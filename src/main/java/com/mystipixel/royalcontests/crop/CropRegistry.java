package com.mystipixel.royalcontests.crop;

import com.mystipixel.royalcontests.config.Text;
import com.mystipixel.royalcontests.config.YamlFile;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The crops contests can be about, from {@code crops.yml}.
 *
 * <p>A crop is a set of blocks rather than one material, so custom setups need no code, and a block
 * belongs to at most one crop, so a break is attributed without guessing.
 */
public final class CropRegistry {

    private final YamlFile file;
    private final Logger logger;
    private final Map<String, CropType> crops = new LinkedHashMap<>();
    private final Map<Material, CropType> byBlock = new EnumMap<>(Material.class);

    public CropRegistry(Plugin plugin) {
        this.file = new YamlFile(plugin, "crops.yml", false);
        this.logger = plugin.getLogger();
        load();
    }

    public void reload() {
        file.reload();
        load();
    }

    private void load() {
        crops.clear();
        byBlock.clear();
        ConfigurationSection root = file.yaml().getConfigurationSection("crops");
        if (root == null) {
            logger.warning("crops.yml has no crops: section; contests have nothing to count.");
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) {
                continue;
            }
            String key = id.toLowerCase(Locale.ROOT);
            Set<Material> blocks = materials(c.getStringList("blocks"), "crops." + id + ".blocks");
            if (blocks.isEmpty()) {
                logger.warning("crops." + id + " has no valid blocks; skipped.");
                continue;
            }
            Material icon = Material.matchMaterial(c.getString("icon", ""));
            CropType crop = new CropType(
                    key,
                    c.getString("display-name", Text.titleCase(key)),
                    icon == null || !icon.isItem() ? Material.WHEAT : icon,
                    blocks,
                    materials(c.getStringList("drops"), "crops." + id + ".drops"),
                    c.getString("collection", key),
                    Math.max(0, c.getInt("points", 1)),
                    c.getBoolean("require-ripe", true),
                    c.getBoolean("natural-only", true),
                    c.getBoolean("stacked", false));
            for (Material block : blocks) {
                CropType previous = byBlock.put(block, crop);
                if (previous != null) {
                    logger.warning(block.name().toLowerCase(Locale.ROOT) + " is listed under both "
                            + previous.id() + " and " + key + " in crops.yml; it counts as " + key + ".");
                }
            }
            crops.put(key, crop);
        }
    }

    private Set<Material> materials(List<String> names, String where) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name.trim());
            if (material == null) {
                logger.warning(where + ": '" + name + "' is not a material.");
            } else {
                out.add(material);
            }
        }
        return out;
    }

    public CropType get(String id) {
        return id == null ? null : crops.get(id.toLowerCase(Locale.ROOT));
    }

    /** The crop a block belongs to, or null. */
    public CropType byBlock(Material block) {
        return byBlock.get(block);
    }

    public Collection<CropType> all() {
        return Collections.unmodifiableCollection(crops.values());
    }

    /** Whether any crop cares whether this block was placed by a player. */
    public boolean tracksPlacement(Material block) {
        CropType crop = byBlock.get(block);
        return crop != null && crop.naturalOnly();
    }
}
