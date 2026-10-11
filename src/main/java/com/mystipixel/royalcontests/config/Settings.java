package com.mystipixel.royalcontests.config;

import com.mystipixel.royalcontests.hook.Papi;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * {@code config.yml}: how contests are scheduled, scored, shown and stored.
 *
 * <p>Accessors read the live file, so {@code /royalcontests reload} takes effect on the next call. A
 * running contest keeps the event it started with; only what happens next changes.
 */
public final class Settings extends YamlFile {

    /** A requirement read through PlaceholderAPI: {@code placeholder} must be at least {@code min}. */
    public record Requirement(String placeholder, double min, String message) {
    }

    public enum ScheduleMode { INTERVAL, BREAK, MANUAL }

    public enum Selection { SEQUENTIAL, RANDOM }

    public enum Scoring { BLOCKS, DROPS, PLACEHOLDER }

    public enum Display { BOSS_BAR, ACTION_BAR, NONE }

    public Settings(Plugin plugin) {
        super(plugin, "config.yml", true);
    }

    public ScheduleMode scheduleMode() {
        return parse(ScheduleMode.class, "schedule.mode", ScheduleMode.INTERVAL);
    }

    /** Contests start every this long, on the clock (interval mode). */
    public long intervalMillis() {
        return Math.max(60_000L, duration("schedule.interval", 3_600_000L));
    }

    /** Shift of the interval from the top of the clock: {@code 15m} starts contests at :15. */
    public long offsetMillis() {
        return duration("schedule.offset", 0L);
    }

    /** The wait after a contest ends before the next one's countdown (break mode). */
    public long breakMillis() {
        return Math.max(0L, duration("schedule.break", 2_400_000L));
    }

    public Selection selection() {
        return parse(Selection.class, "schedule.selection", Selection.SEQUENTIAL);
    }

    /** How far ahead of a scheduled contest to announce it, e.g. {@code [30m, 10m, 1m]}. */
    public List<Long> announceBefore() {
        return durations("schedule.announce-before");
    }

    public int calendarSize() {
        return Math.clamp(yaml().getInt("schedule.calendar-size", 5), 1, 28);
    }

    /** World name patterns contests count in; {@code *} matches anything, so {@code garden_*} is every Garden. */
    public List<String> defaultWorlds() {
        return yaml().getStringList("contest.worlds");
    }

    public long defaultDuration() {
        return Math.max(10_000L, duration("contest.default-duration", 1_200_000L));
    }

    public long defaultCountdown() {
        return Math.max(0L, duration("contest.default-countdown", 120_000L));
    }

    public List<Long> countdownAnnouncements() {
        return durations("contest.countdown-announcements");
    }

    /** Whether a player's first scoring crop is the only one they score in (as on Hypixel). */
    public boolean lockIn() {
        return yaml().getBoolean("contest.lock-in", true);
    }

    public int minimumScore() {
        return Math.max(0, yaml().getInt("contest.minimum-score", 100));
    }

    /** What a restart does to a running contest: {@code END} pays it out, {@code CANCEL} drops it. */
    public boolean endOnShutdown() {
        return !"cancel".equalsIgnoreCase(yaml().getString("contest.on-shutdown", "end"));
    }

    public boolean disableBoneMeal() {
        return yaml().getBoolean("contest.disable-bone-meal", false);
    }

    /** How many of each crop's leaders are announced when a contest ends. */
    public int broadcastTop() {
        return Math.max(0, yaml().getInt("contest.broadcast-top", 3));
    }

    public Scoring scoring() {
        Scoring mode = parse(Scoring.class, "scoring.mode", Scoring.BLOCKS);
        return mode == Scoring.PLACEHOLDER && !Papi.available() ? Scoring.BLOCKS : mode;
    }

    /** The placeholder template for placeholder scoring; {@code %collection%} is the crop's collection id. */
    public String scoringPlaceholder() {
        return yaml().getString("scoring.placeholder", "%aurora_collections_farming_%collection%_count_raw%");
    }

    /** Breaks per second past which a player's extra breaks stop counting; 0 for no limit. */
    public int maxBreaksPerSecond() {
        return Math.max(0, yaml().getInt("scoring.max-breaks-per-second", 30));
    }

    /** Whether harvests inside RoyalRegen zones count. Off by default: those crops regrow on a timer. */
    public boolean countRegenZones() {
        return yaml().getBoolean("scoring.count-regen-zones", false);
    }

    public List<Requirement> requirements() {
        List<Requirement> out = new ArrayList<>();
        for (Map<?, ?> raw : yaml().getMapList("requirements")) {
            Object placeholder = raw.get("placeholder");
            Object min = raw.get("min");
            if (placeholder == null || !(min instanceof Number number)) {
                continue;
            }
            Object message = raw.get("message");
            out.add(new Requirement(placeholder.toString(), number.doubleValue(),
                    message == null ? "" : message.toString()));
        }
        return out;
    }

    /** The first requirement a player does not meet, or null. */
    public Requirement unmet(Player player) {
        for (Requirement requirement : requirements()) {
            double value = Papi.number(player, requirement.placeholder());
            if (Double.isNaN(value) || value < requirement.min()) {
                return requirement;
            }
        }
        return null;
    }

    public Display display() {
        return parse(Display.class, "display.type", Display.BOSS_BAR);
    }

    public String bossBarColor() {
        return yaml().getString("display.boss-bar.color", "yellow");
    }

    public String bossBarOverlay() {
        return yaml().getString("display.boss-bar.overlay", "progress");
    }

    public boolean displayOnlyInContestWorlds() {
        return yaml().getBoolean("display.only-in-contest-worlds", true);
    }

    /** Play a sound from {@code sounds.<key>}: {@code "entity.player.levelup 1.0 1.2"}, or {@code ""} for none. */
    public void playSound(Player player, String key) {
        String raw = yaml().getString("sounds." + key, "");
        if (raw == null || raw.isBlank()) {
            return;
        }
        String[] parts = raw.trim().split("\\s+");
        String sound = parts[0].toLowerCase(Locale.ROOT);
        if (!sound.contains(".") && !sound.contains(":")) {
            sound = sound.replace('_', '.');          // ENTITY_PLAYER_LEVELUP → entity.player.levelup
        }
        float volume = parts.length > 1 ? parseFloat(parts[1], 1f) : 1f;
        float pitch = parts.length > 2 ? parseFloat(parts[2], 1f) : 1f;
        Location at = player.getLocation();
        player.playSound(at, sound, volume, pitch);
    }

    /** Whether rewards are given straight away to online players instead of waiting in {@code /contests}. */
    public boolean autoClaim() {
        return yaml().getBoolean("rewards.auto-claim", false);
    }

    public ConfigurationSection storage() {
        return yaml().getConfigurationSection("storage");
    }

    public int leaderboardRefreshSeconds() {
        return Math.max(30, yaml().getInt("leaderboards.refresh-seconds", 300));
    }

    public int leaderboardSize() {
        return Math.clamp(yaml().getInt("leaderboards.size", 10), 1, 100);
    }

    private long duration(String path, long fallback) {
        return Durations.parseMillis(yaml().getString(path), fallback);
    }

    private List<Long> durations(String path) {
        List<Long> out = new ArrayList<>();
        for (String raw : yaml().getStringList(path)) {
            long millis = Durations.parseMillis(raw, -1L);
            if (millis > 0) {
                out.add(millis);
            }
        }
        return out;
    }

    private <E extends Enum<E>> E parse(Class<E> type, String path, E fallback) {
        String raw = yaml().getString(path);
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw);
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    /** Whether a world name matches any of these patterns ({@code *} is a wildcard, case-insensitive). */
    public static boolean worldMatches(String world, List<String> patterns) {
        for (String pattern : patterns) {
            String regex = "(?i)" + Pattern.quote(pattern).replace("*", "\\E.*\\Q");
            if (world.matches(regex)) {
                return true;
            }
        }
        return false;
    }
}
