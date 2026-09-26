package com.mystipixel.royalcontests.contest;

import com.mystipixel.royalcontests.config.Durations;
import com.mystipixel.royalcontests.config.Settings;
import com.mystipixel.royalcontests.config.YamlFile;
import com.mystipixel.royalcontests.crop.CropRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

/**
 * The contest events and the order they run in, from {@code events.yml}.
 *
 * <p>Which event runs in a scheduled slot — and which crops a random-crop event picks — is a pure
 * function of the slot number. So the calendar can show upcoming contests exactly, and a restart
 * neither repeats nor skips one.
 */
public final class EventRegistry {

    private final YamlFile file;
    private final Settings settings;
    private final CropRegistry crops;
    private final Logger logger;
    private final Map<String, ContestEvent> events = new LinkedHashMap<>();
    private final List<String> rotation = new ArrayList<>();

    public EventRegistry(Plugin plugin, Settings settings, CropRegistry crops) {
        this.file = new YamlFile(plugin, "events.yml", false);
        this.settings = settings;
        this.crops = crops;
        this.logger = plugin.getLogger();
        load();
    }

    public void reload() {
        file.reload();
        load();
    }

    private void load() {
        events.clear();
        rotation.clear();
        ConfigurationSection root = file.yaml().getConfigurationSection("events");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection e = root.getConfigurationSection(id);
                if (e == null) {
                    continue;
                }
                String key = id.toLowerCase(Locale.ROOT);
                List<String> cropIds = new ArrayList<>();
                for (String crop : e.getStringList("crops")) {
                    if (crops.get(crop) == null) {
                        logger.warning("events." + id + ": no crop '" + crop + "' in crops.yml; skipped.");
                    } else {
                        cropIds.add(crop.toLowerCase(Locale.ROOT));
                    }
                }
                if (cropIds.isEmpty()) {
                    logger.warning("events." + id + " has no valid crops; skipped.");
                    continue;
                }
                events.put(key, new ContestEvent(key,
                        e.getString("display-name", id),
                        cropIds,
                        Math.max(0, e.getInt("random-crops", 0)),
                        Math.max(10_000L, Durations.parseMillis(e.getString("duration"), settings.defaultDuration())),
                        Math.max(0L, Durations.parseMillis(e.getString("countdown"), settings.defaultCountdown())),
                        e.getStringList("worlds"),
                        Math.max(1, e.getInt("weight", 1))));
            }
        }
        for (String id : file.yaml().getStringList("rotation")) {
            if (events.containsKey(id.toLowerCase(Locale.ROOT))) {
                rotation.add(id.toLowerCase(Locale.ROOT));
            } else {
                logger.warning("events.yml rotation: no event '" + id + "'; skipped.");
            }
        }
        if (rotation.isEmpty()) {
            rotation.addAll(events.keySet());
        }
    }

    public ContestEvent get(String id) {
        return id == null ? null : events.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<ContestEvent> all() {
        return Collections.unmodifiableCollection(events.values());
    }

    public boolean isEmpty() {
        return rotation.isEmpty();
    }

    /** The event for a schedule slot: the rotation in order, or a weighted pick seeded by the slot. */
    public ContestEvent forSlot(long slot, Settings.Selection selection) {
        if (rotation.isEmpty()) {
            return null;
        }
        if (selection == Settings.Selection.SEQUENTIAL) {
            return events.get(rotation.get((int) Math.floorMod(slot, (long) rotation.size())));
        }
        int total = 0;
        for (String id : rotation) {
            total += events.get(id).weight();
        }
        int roll = new Random(slot * 0x9E3779B97F4A7C15L).nextInt(total);
        for (String id : rotation) {
            roll -= events.get(id).weight();
            if (roll < 0) {
                return events.get(id);
            }
        }
        return events.get(rotation.get(0));
    }

    /** The crops a contest of this event in this slot is about. */
    public List<String> cropsFor(ContestEvent event, long slot) {
        List<String> pool = new ArrayList<>(event.crops());
        if (event.randomCrops() <= 0 || event.randomCrops() >= pool.size()) {
            return pool;
        }
        Collections.shuffle(pool, new Random(slot * 31 + event.id().hashCode()));
        return new ArrayList<>(pool.subList(0, event.randomCrops()));
    }

    /** The world patterns an event counts in. */
    public List<String> worldsFor(ContestEvent event) {
        return event.worlds().isEmpty() ? settings.defaultWorlds() : event.worlds();
    }
}
