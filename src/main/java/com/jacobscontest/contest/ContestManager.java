package com.jacobscontest.contest;

import com.jacobscontest.JacobsContest;
import com.jacobscontest.util.MessageUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the lifecycle of contests: loading config, starting, tracking, ending, rewarding.
 */
public class ContestManager {

    private final JacobsContest plugin;

    // Loaded from config
    private final Map<String, ContestEvent> events = new LinkedHashMap<>();
    private final List<Bracket> brackets = new ArrayList<>();
    private final List<String> rotation = new ArrayList<>();

    // Active contest state
    private ContestEvent activeEvent;
    private final Map<UUID, PlayerContestData> participants = new LinkedHashMap<>();
    private BukkitTask countdownTask;
    private BukkitTask contestTask;
    private BukkitTask displayTask;
    private BossBar bossBar;
    private long contestEndTime;
    private boolean contestActive = false;
    private boolean countdownActive = false;
    private int rotationIndex = 0;

    public ContestManager(JacobsContest plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    // ========================================
    //             Config Loading
    // ========================================

    public void loadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        events.clear();
        brackets.clear();
        rotation.clear();

        // Load brackets
        ConfigurationSection bracketSection = config.getConfigurationSection("brackets");
        if (bracketSection != null) {
            for (String key : bracketSection.getKeys(false)) {
                ConfigurationSection b = bracketSection.getConfigurationSection(key);
                if (b == null) continue;
                brackets.add(new Bracket(
                        key,
                        b.getInt("threshold", 100),
                        b.getString("color", "&7"),
                        b.getString("display-name", key),
                        b.getStringList("commands")
                ));
            }
        }
        // Sort brackets by threshold ascending (strictest first)
        brackets.sort(Comparator.comparingInt(Bracket::threshold));

        // Load events
        ConfigurationSection eventSection = config.getConfigurationSection("events");
        if (eventSection != null) {
            for (String key : eventSection.getKeys(false)) {
                ConfigurationSection e = eventSection.getConfigurationSection(key);
                if (e == null) continue;

                List<Material> crops = new ArrayList<>();
                for (String cropName : e.getStringList("crops")) {
                    Material mat = Material.matchMaterial(cropName);
                    if (mat != null) {
                        // Map crop names to their block equivalents
                        crops.add(mapCropMaterial(mat));
                    }
                }

                events.put(key, new ContestEvent(
                        key,
                        e.getString("display-name", key),
                        parseDuration(e.getString("duration", config.getString("settings.default-duration", "10m"))),
                        parseDuration(e.getString("countdown", config.getString("settings.countdown", "5m"))),
                        crops,
                        e.getStringList("worlds")
                ));
            }
        }

        // Load rotation
        rotation.addAll(config.getStringList("rotation"));
    }

    /**
     * Maps item/crop names to their planted block form.
     * e.g., WHEAT -> WHEAT (block), CARROTS -> CARROTS (block), POTATOES -> POTATOES (block)
     */
    private Material mapCropMaterial(Material mat) {
        return switch (mat) {
            case CARROT -> Material.CARROTS;
            case POTATO -> Material.POTATOES;
            case BEETROOT -> Material.BEETROOTS;
            case SWEET_BERRIES -> Material.SWEET_BERRY_BUSH;
            default -> mat;
        };
    }

    // ========================================
    //           Contest Lifecycle
    // ========================================

    public boolean startContest(String eventId) {
        if (contestActive || countdownActive) return false;

        ContestEvent event = events.get(eventId);
        if (event == null) return false;

        activeEvent = event;

        long countdownTicks = event.countdownTicks();
        if (countdownTicks > 0) {
            startCountdown(event, countdownTicks);
        } else {
            beginContest(event);
        }
        return true;
    }

    public boolean startNextRotation() {
        if (rotation.isEmpty()) return false;
        String eventId = rotation.get(rotationIndex % rotation.size());
        rotationIndex++;
        return startContest(eventId);
    }

    private void startCountdown(ContestEvent event, long ticks) {
        countdownActive = true;
        final long[] remainingSeconds = {ticks / 20};

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (remainingSeconds[0] <= 0) {
                    countdownActive = false;
                    this.cancel();
                    beginContest(event);
                    return;
                }

                // Broadcast at certain intervals
                if (shouldAnnounce(remainingSeconds[0])) {
                    String timeStr = formatTime(remainingSeconds[0]);
                    String msg = getMessage("contest-countdown")
                            .replace("%event%", event.displayName())
                            .replace("%time%", timeStr)
                            .replace("%crops%", event.cropsDisplayString());
                    broadcastMessage(msg);
                }

                remainingSeconds[0]--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Every second
    }

    private void beginContest(ContestEvent event) {
        contestActive = true;
        participants.clear();
        contestEndTime = System.currentTimeMillis() + (event.durationTicks() / 20 * 1000);

        // Broadcast start message
        String msg = getMessage("contest-started")
                .replace("%event%", event.displayName())
                .replace("%crops%", event.cropsDisplayString());
        broadcastMessage(msg);

        // Play start sound
        String soundName = plugin.getConfig().getString("settings.start-sound", "entity.player.levelup");
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase().replace('.', '_')), 1.0f, 1.0f);
            } catch (Exception ignored) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }

        // Start display updater
        String displayMethod = plugin.getConfig().getString("settings.display", "BOSS_BAR");
        if (displayMethod.equalsIgnoreCase("BOSS_BAR")) {
            startBossBar();
        }

        // Start contest timer
        contestTask = new BukkitRunnable() {
            @Override
            public void run() {
                endContest();
            }
        }.runTaskLater(plugin, event.durationTicks());

        // Start display update loop
        displayTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!contestActive) {
                    this.cancel();
                    return;
                }
                updateDisplay();
            }
        }.runTaskTimer(plugin, 0L, 20L); // Update every second
    }

    public void endContest() {
        if (!contestActive) return;
        contestActive = false;

        // Cancel tasks
        if (contestTask != null) { contestTask.cancel(); contestTask = null; }
        if (displayTask != null) { displayTask.cancel(); displayTask = null; }

        // Remove boss bar
        removeBossBar();

        // Broadcast end
        String endMsg = getMessage("contest-ended")
                .replace("%event%", activeEvent.displayName());
        broadcastMessage(endMsg);

        // Calculate results and distribute rewards
        distributeRewards();

        // Play end sound
        String soundName = plugin.getConfig().getString("settings.end-sound", "entity.firework_rocket.blast");
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase().replace('.', '_')), 1.0f, 1.0f);
            } catch (Exception ignored) {
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);
            }
        }

        // Clean up
        participants.clear();
        activeEvent = null;
    }

    public void forceStop() {
        if (countdownActive) {
            countdownActive = false;
            if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        }
        if (contestActive) {
            endContest();
        }
    }

    // ========================================
    //           Participant Tracking
    // ========================================

    /**
     * Validates the crop break and handles lock-in. Called from BlockBreakEvent.
     * Returns true if this is a valid contest break that should be scored.
     */
    public boolean validateAndLockCrop(Player player, Material blockType) {
        if (!contestActive) return false;
        if (activeEvent == null) return false;

        // Check world
        if (!activeEvent.isWorldValid(player.getWorld().getName())) return false;

        // Check if this block is a valid crop for this event
        if (!activeEvent.isCropValid(blockType)) return false;

        UUID uuid = player.getUniqueId();
        PlayerContestData data = participants.computeIfAbsent(uuid,
                k -> new PlayerContestData(uuid, player.getName()));

        // Lock-in mechanic
        if (!data.isLocked()) {
            data.lockCrop(blockType);
            String lockMsg = getMessage("player-locked")
                    .replace("%crop%", data.getCropName());
            player.sendMessage(MessageUtil.toComponent(lockMsg));
        }

        // Check if breaking the right crop
        if (data.getLockedCrop() != blockType) {
            String wrongMsg = getMessage("player-wrong-crop")
                    .replace("%crop%", data.getCropName());
            player.sendMessage(MessageUtil.toComponent(wrongMsg));
            return false;
        }

        return true;
    }

    /**
     * Add score points to a player. Called from BlockDropItemEvent after counting drops.
     */
    public void addPlayerScore(UUID playerId, int points) {
        PlayerContestData data = participants.get(playerId);
        if (data != null) {
            data.addScore(points);
        }
    }

    /**
     * Legacy method: handle crop break with flat 1 point per block.
     * Kept for backwards compatibility but prefer the drop-based scoring.
     */
    public void handleCropBreak(Player player, Material blockType) {
        if (validateAndLockCrop(player, blockType)) {
            addPlayerScore(player.getUniqueId(), 1);
        }
    }

    // ========================================
    //              Rewards
    // ========================================

    private void distributeRewards() {
        if (participants.isEmpty()) return;

        // Sort participants by score descending
        List<PlayerContestData> sorted = participants.values().stream()
                .sorted(Comparator.comparingInt(PlayerContestData::getScore).reversed())
                .collect(Collectors.toList());

        int totalParticipants = sorted.size();

        // Broadcast results header
        broadcastMessage(getMessage("results-header"));

        for (int i = 0; i < sorted.size(); i++) {
            PlayerContestData data = sorted.get(i);
            int position = i + 1;

            // Calculate percentile: position 1 out of 100 = top 1%
            double percentile = ((double) position / totalParticipants) * 100.0;

            // Find matching bracket (first one that qualifies, since sorted by threshold)
            Bracket bracket = findBracket(percentile);
            if (bracket == null) continue;

            // Broadcast result line
            String resultMsg = getMessage("bracket-result")
                    .replace("%bracket_display%", bracket.displayName())
                    .replace("%bracket%", bracket.name())
                    .replace("%bracket_color%", bracket.color())
                    .replace("%player%", data.getPlayerName())
                    .replace("%position%", String.valueOf(position))
                    .replace("%score%", String.valueOf(data.getScore()))
                    .replace("%crop%", data.getCropId());
            broadcastMessage(resultMsg);

            // Execute reward commands
            for (String cmd : bracket.commands()) {
                String finalCmd = cmd
                        .replace("%player%", data.getPlayerName())
                        .replace("%crop%", data.getCropId())
                        .replace("%score%", String.valueOf(data.getScore()))
                        .replace("%bracket%", bracket.name())
                        .replace("%position%", String.valueOf(position));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            }
        }
    }

    private Bracket findBracket(double percentile) {
        for (Bracket bracket : brackets) {
            if (percentile <= bracket.threshold()) {
                return bracket;
            }
        }
        // Fallback to last bracket
        return brackets.isEmpty() ? null : brackets.get(brackets.size() - 1);
    }

    // ========================================
    //           Display (BossBar)
    // ========================================

    private void startBossBar() {
        String colorStr = plugin.getConfig().getString("settings.bossbar-color", "YELLOW");
        BossBar.Color color;
        try {
            color = BossBar.Color.valueOf(colorStr.toUpperCase());
        } catch (Exception e) {
            color = BossBar.Color.YELLOW;
        }

        bossBar = BossBar.bossBar(
                Component.text("Jacob's Contest"),
                1.0f,
                color,
                BossBar.Overlay.PROGRESS
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bossBar);
        }
    }

    private void updateDisplay() {
        if (bossBar == null) return;

        long remaining = Math.max(0, (contestEndTime - System.currentTimeMillis()) / 1000);
        float progress = (float) remaining / (activeEvent.durationSeconds());
        progress = Math.max(0f, Math.min(1f, progress));
        bossBar.progress(progress);

        String timeStr = formatTime(remaining);

        // Update boss bar for each player with their personal stats
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerContestData data = participants.get(player.getUniqueId());
            String crop = data != null ? data.getCropName() : "None";
            int score = data != null ? data.getScore() : 0;

            String text = getMessage("bossbar-text")
                    .replace("%crop%", crop)
                    .replace("%score%", String.valueOf(score))
                    .replace("%time%", timeStr);

            bossBar.name(MessageUtil.toComponent(text));
        }

        // Add boss bar to any new players who joined
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bossBar);
        }
    }

    private void removeBossBar() {
        if (bossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(bossBar);
            }
            bossBar = null;
        }
    }

    // ========================================
    //              Utilities
    // ========================================

    public boolean isContestActive() {
        return contestActive;
    }

    public boolean isCountdownActive() {
        return countdownActive;
    }

    public ContestEvent getActiveEvent() {
        return activeEvent;
    }

    public Map<UUID, PlayerContestData> getParticipants() {
        return participants;
    }

    public PlayerContestData getPlayerData(UUID uuid) {
        return participants.get(uuid);
    }

    public Map<String, ContestEvent> getEvents() {
        return events;
    }

    public long getTimeRemaining() {
        if (!contestActive) return 0;
        return Math.max(0, (contestEndTime - System.currentTimeMillis()) / 1000);
    }

    public int getPlayerPosition(UUID uuid) {
        List<PlayerContestData> sorted = participants.values().stream()
                .sorted(Comparator.comparingInt(PlayerContestData::getScore).reversed())
                .collect(Collectors.toList());
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getPlayerId().equals(uuid)) return i + 1;
        }
        return -1;
    }

    public Bracket getPlayerBracket(UUID uuid) {
        int position = getPlayerPosition(uuid);
        if (position < 0) return null;
        int total = participants.size();
        double percentile = ((double) position / total) * 100.0;
        return findBracket(percentile);
    }

    private String getMessage(String key) {
        return plugin.getConfig().getString("messages." + key, "&cMissing message: " + key);
    }

    private void broadcastMessage(String message) {
        Component component = MessageUtil.toComponent(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    private boolean shouldAnnounce(long seconds) {
        return seconds == 300 || seconds == 240 || seconds == 180 || seconds == 120
                || seconds == 60 || seconds == 30 || seconds == 15 || seconds == 10
                || seconds == 5 || seconds == 4 || seconds == 3 || seconds == 2 || seconds == 1;
    }

    public static String formatTime(long seconds) {
        if (seconds >= 60) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            return secs > 0 ? mins + "m " + secs + "s" : mins + "m";
        }
        return seconds + "s";
    }

    /**
     * Parse duration string like "10m", "5m30s", "300s" into ticks.
     */
    public static long parseDuration(String input) {
        if (input == null || input.isEmpty()) return 12000L; // 10 min default

        long totalSeconds = 0;
        StringBuilder number = new StringBuilder();

        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                number.append(c);
            } else if (c == 'm' || c == 'M') {
                totalSeconds += Long.parseLong(number.toString()) * 60;
                number.setLength(0);
            } else if (c == 's' || c == 'S') {
                totalSeconds += Long.parseLong(number.toString());
                number.setLength(0);
            } else if (c == 'h' || c == 'H') {
                totalSeconds += Long.parseLong(number.toString()) * 3600;
                number.setLength(0);
            }
        }

        // If just a number with no suffix, treat as minutes
        if (!number.isEmpty()) {
            totalSeconds += Long.parseLong(number.toString()) * 60;
        }

        return totalSeconds * 20; // Convert to ticks
    }
}
