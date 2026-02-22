package com.jacobscontest.contest;

import com.jacobscontest.JacobsContest;
import com.jacobscontest.scoring.*;
import com.jacobscontest.storage.PendingReward;
import com.jacobscontest.storage.PlayerStats;
import com.jacobscontest.util.ConfigManager;
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
    private BukkitTask autoRotationTask;
    private BossBar bossBar;
    private long contestEndTime;
    private boolean contestActive = false;
    private boolean countdownActive = false;
    private int rotationIndex = 0;

    // Skill requirement cache
    private boolean skillRequirementEnabled = false;
    private String skillPlaceholder = "";
    private int skillMinimum = 0;
    private String skillDenyMessage = "";

    // Minimum score
    private int minimumScore = 0;
    private String minimumScoreMessage = "";

    // Cooldown for deny messages (prevent spam)
    private final Map<UUID, Long> denyMessageCooldown = new HashMap<>();
    private static final long DENY_COOLDOWN_MS = 5000; // 5 seconds

    // Scoring provider
    private ScoringProvider scoringProvider;

    public ContestManager(JacobsContest plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    // ========================================
    //             Config Loading
    // ========================================

    public void loadConfig() {
        ConfigManager cm = plugin.getConfigManager();
        cm.reloadAll();
        FileConfiguration config = cm.getConfig();
        FileConfiguration bracketsYml = cm.getBrackets();
        FileConfiguration eventsYml = cm.getEvents();

        events.clear();
        brackets.clear();
        rotation.clear();

        // Load brackets from brackets.yml
        for (String key : bracketsYml.getKeys(false)) {
            ConfigurationSection b = bracketsYml.getConfigurationSection(key);
            if (b == null) continue;
            brackets.add(new Bracket(
                    key,
                    b.getInt("threshold", 100),
                    b.getString("color", "&7"),
                    b.getString("display-name", key),
                    b.getStringList("commands")
            ));
        }
        // Sort brackets by threshold ascending (strictest first)
        brackets.sort(Comparator.comparingInt(Bracket::threshold));

        // Load events from events.yml
        ConfigurationSection eventSection = eventsYml.getConfigurationSection("events");
        if (eventSection != null) {
            for (String key : eventSection.getKeys(false)) {
                ConfigurationSection e = eventSection.getConfigurationSection(key);
                if (e == null) continue;

                List<Material> crops = new ArrayList<>();
                for (String cropName : e.getStringList("crops")) {
                    Material mat = Material.matchMaterial(cropName);
                    if (mat != null) {
                        crops.add(mapCropMaterial(mat));
                    }
                }

                events.put(key, new ContestEvent(
                        key,
                        e.getString("display-name", key),
                        parseDuration(e.getString("duration", config.getString("settings.default-duration", "20m"))),
                        parseDuration(e.getString("countdown", config.getString("settings.countdown", "2m"))),
                        crops,
                        e.getStringList("worlds")
                ));
            }
        }

        // Load rotation from events.yml
        rotation.addAll(eventsYml.getStringList("rotation"));

        // Load skill requirement
        ConfigurationSection skillSection = config.getConfigurationSection("settings.skill-requirement");
        if (skillSection != null) {
            skillRequirementEnabled = skillSection.getBoolean("enabled", false);
            skillPlaceholder = skillSection.getString("placeholder", "");
            skillMinimum = skillSection.getInt("minimum", 0);
            skillDenyMessage = skillSection.getString("deny-message", "&cYou don't meet the skill requirement!");
        }

        // Load minimum score
        minimumScore = config.getInt("settings.minimum-score", 0);
        minimumScoreMessage = config.getString("settings.minimum-score-message",
                "&cYou need at least &6%minimum% crops &cto earn rewards!");

        // Initialize scoring provider
        initScoringProvider();

        // Start auto-rotation if enabled
        startAutoRotation();
    }

    // ========================================
    //           Scoring Provider
    // ========================================

    private void initScoringProvider() {
        String mode = plugin.getConfig().getString("scoring.mode", "BLOCK_BREAK").toUpperCase();

        scoringProvider = switch (mode) {
            case "AURORA_COLLECTIONS" -> new AuroraCollectionsScoring(plugin);
            case "PLACEHOLDER" -> new PlaceholderScoring(plugin);
            case "DROPS" -> new DropScoring(plugin);
            default -> new BlockBreakScoring(plugin);
        };

        // Register event listener if the scoring provider has one
        if (scoringProvider.getListener() != null) {
            plugin.getServer().getPluginManager().registerEvents(
                    scoringProvider.getListener(), plugin);
        }

        plugin.getLogger().info("Scoring mode: " + mode);
    }

    public ScoringProvider getScoringProvider() {
        return scoringProvider;
    }

    // ========================================
    //           Auto Rotation
    // ========================================

    public void startAutoRotation() {
        stopAutoRotation();

        if (!plugin.getConfig().getBoolean("settings.auto-rotation.enabled", false)) return;
        if (rotation.isEmpty()) return;

        // Schedule the first contest after the break period
        long breakTicks = parseDuration(plugin.getConfig().getString("settings.auto-rotation.break-between", "1h"));

        // If no contest is active, start the first one after a short delay
        if (!contestActive && !countdownActive) {
            scheduleNextContest(200L); // 10 second initial delay
        }
    }

    public void stopAutoRotation() {
        if (autoRotationTask != null) {
            autoRotationTask.cancel();
            autoRotationTask = null;
        }
    }

    private void scheduleNextContest(long delayTicks) {
        if (!plugin.getConfig().getBoolean("settings.auto-rotation.enabled", false)) return;

        // Announce next contest
        if (plugin.getConfig().getBoolean("settings.auto-rotation.announce-next", true) && delayTicks > 200L) {
            String nextEventId = rotation.get(rotationIndex % rotation.size());
            ContestEvent nextEvent = events.get(nextEventId);
            if (nextEvent != null) {
                String announceMsg = getMessage("next-contest")
                        .replace("%event%", nextEvent.displayName())
                        .replace("%time%", formatTime(delayTicks / 20));
                broadcastMessage(announceMsg);
            }
        }

        autoRotationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!contestActive && !countdownActive) {
                    startNextRotation();
                }
            }
        }.runTaskLater(plugin, delayTicks);
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
                updateAllScores();
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

        // Final score update from AuroraCollections
        updateAllScores();

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

        // Schedule next auto-rotation contest
        if (plugin.getConfig().getBoolean("settings.auto-rotation.enabled", false)) {
            long breakTicks = parseDuration(plugin.getConfig().getString("settings.auto-rotation.break-between", "1h"));
            scheduleNextContest(breakTicks);
        }
    }

    public void forceStop() {
        stopAutoRotation();
        if (countdownActive) {
            countdownActive = false;
            if (countdownTask != null) { countdownTask.cancel(); countdownTask = null; }
        }
        if (contestActive) {
            // Temporarily disable auto-rotation so endContest doesn't schedule the next one
            boolean wasAutoRotation = plugin.getConfig().getBoolean("settings.auto-rotation.enabled", false);
            plugin.getConfig().set("settings.auto-rotation.enabled", false);
            endContest();
            plugin.getConfig().set("settings.auto-rotation.enabled", wasAutoRotation);
        }
        participants.clear();
        activeEvent = null;
    }

    // ========================================
    //           Participant Tracking
    // ========================================

    /**
     * Validates the crop break and handles lock-in. Called from BlockBreakEvent.
     * On first break, takes a snapshot of the player's AuroraCollections count.
     * Returns true if this is a valid contest break.
     */
    public boolean validateAndLockCrop(Player player, Material blockType) {
        if (!contestActive) return false;
        if (activeEvent == null) return false;

        // Check world
        if (!activeEvent.isWorldValid(player.getWorld().getName())) return false;

        // Check if this block is a valid crop for this event
        if (!activeEvent.isCropValid(blockType)) return false;

        UUID uuid = player.getUniqueId();

        // Check skill requirement (only on first participation, not every break)
        if (!participants.containsKey(uuid) && skillRequirementEnabled) {
            if (!meetsSkillRequirement(player)) {
                // Rate-limit deny messages so we don't spam the player
                long now = System.currentTimeMillis();
                Long lastDeny = denyMessageCooldown.get(uuid);
                if (lastDeny == null || now - lastDeny > DENY_COOLDOWN_MS) {
                    player.sendMessage(MessageUtil.toComponent(skillDenyMessage));
                    denyMessageCooldown.put(uuid, now);
                }
                return false;
            }
        }

        PlayerContestData data = participants.computeIfAbsent(uuid,
                k -> new PlayerContestData(uuid, player.getName()));

        // Lock-in mechanic
        if (!data.isLocked()) {
            data.lockCrop(blockType);

            // Let the scoring provider take any snapshots
            scoringProvider.onPlayerLockIn(player, data);

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
     * Update all participant scores via the active scoring provider.
     * Called periodically by the display updater and at contest end.
     */
    public void updateAllScores() {
        if (scoringProvider != null) {
            scoringProvider.updateScores(participants);
        }
    }

    /**
     * Add score points to a player directly (fallback method).
     */
    public void addPlayerScore(UUID playerId, int points) {
        PlayerContestData data = participants.get(playerId);
        if (data != null) {
            data.addScore(points);
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

        // Filter out players who didn't meet minimum score for ranking purposes
        List<PlayerContestData> qualified = sorted.stream()
                .filter(d -> d.getScore() >= minimumScore)
                .collect(Collectors.toList());

        int totalQualified = qualified.size();

        // Broadcast results header
        broadcastMessage(getMessage("results-header"));

        if (totalQualified == 0) {
            broadcastMessage(getMessage("no-qualified"));
        }

        // Award qualified players — save pending rewards + update stats
        for (int i = 0; i < qualified.size(); i++) {
            PlayerContestData data = qualified.get(i);
            int position = i + 1;

            // Calculate percentile based on qualified players only
            double percentile = ((double) position / totalQualified) * 100.0;

            // Find matching bracket
            Bracket bracket = findBracket(percentile);
            if (bracket == null) continue;

            // Update player stats and check for personal best
            PlayerStats stats = plugin.getStorage().getPlayerStats(data.getPlayerId(), data.getCropId());
            boolean newPersonalBest = stats.updateAfterContest(data.getScore(), bracket.name());
            plugin.getStorage().savePlayerStats(stats);

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

            // Announce personal best
            if (newPersonalBest) {
                String pbMsg = "&e&lNEW PERSONAL BEST! &f" + data.getPlayerName()
                        + " &7set a new PB of &6" + data.getScore() + " &7for &6" + data.getCropName() + "&7!";
                broadcastMessage(pbMsg);
            }

            // Save as pending reward (claim-based, not auto-claim)
            PendingReward reward = new PendingReward(
                    data.getPlayerId(),
                    data.getPlayerName(),
                    activeEvent != null ? activeEvent.displayName() : "Contest",
                    data.getCropId(),
                    data.getScore(),
                    stats.getPersonalBest(),
                    bracket.name(),
                    bracket.displayName(),
                    position,
                    totalQualified,
                    bracket.commands()
            );
            plugin.getStorage().savePendingReward(reward);

            // Notify player to claim rewards
            Player player = Bukkit.getPlayer(data.getPlayerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(MessageUtil.toComponent(
                        "&e&lJacob's Contest &8» &aYou earned rewards! Use &6/contests &ato claim them!"));
            }
        }

        // Update stats for unqualified players too (they still participated)
        for (PlayerContestData data : sorted) {
            if (data.getScore() < minimumScore) {
                // Still save their stats (participation counts)
                if (data.getScore() > 0) {
                    PlayerStats stats = plugin.getStorage().getPlayerStats(data.getPlayerId(), data.getCropId());
                    stats.updateAfterContest(data.getScore(), "NONE");
                    plugin.getStorage().savePlayerStats(stats);
                }

                Player player = Bukkit.getPlayer(data.getPlayerId());
                if (player != null && player.isOnline()) {
                    String msg = minimumScoreMessage
                            .replace("%score%", String.valueOf(data.getScore()))
                            .replace("%minimum%", String.valueOf(minimumScore));
                    player.sendMessage(MessageUtil.toComponent(msg));
                }
            }
        }
    }

    /**
     * Check if a player meets the skill requirement via PlaceholderAPI.
     */
    private boolean meetsSkillRequirement(Player player) {
        if (!skillRequirementEnabled || skillPlaceholder.isEmpty()) return true;

        try {
            // Use PlaceholderAPI to resolve the placeholder
            String resolved = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(
                    player, "%" + skillPlaceholder + "%");

            // Try to parse as number
            double value = Double.parseDouble(resolved);
            return value >= skillMinimum;
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Could not parse skill placeholder '" + skillPlaceholder
                    + "' for player " + player.getName() + ": " + e.getMessage());
            return false;
        } catch (Exception e) {
            // PlaceholderAPI not available or other error — allow participation
            return true;
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
        return plugin.getConfigManager().getMessage(key);
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
