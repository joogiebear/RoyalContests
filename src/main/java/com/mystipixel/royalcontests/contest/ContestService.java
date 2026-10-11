package com.mystipixel.royalcontests.contest;

import com.mystipixel.royalcontests.config.Durations;
import com.mystipixel.royalcontests.config.Lang;
import com.mystipixel.royalcontests.config.Settings;
import com.mystipixel.royalcontests.crop.CropRegistry;
import com.mystipixel.royalcontests.crop.CropType;
import com.mystipixel.royalcontests.hook.Papi;
import com.mystipixel.royalcontests.storage.Records.Result;
import com.mystipixel.royalcontests.storage.RewardService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * Runs contests: when they start, what they count, who is winning, and what everyone gets.
 *
 * <p>One timer, once a second, drives every state change, so a reload has nothing to leak or stack.
 *
 * <ul>
 *   <li><b>interval</b>: every {@code schedule.interval} on the clock. A slot's event and crops are fixed
 *       by its number, so a restart changes nothing.</li>
 *   <li><b>break</b>: the next countdown starts {@code schedule.break} after the last contest ended.</li>
 *   <li><b>manual</b>: only {@code /royalcontests start}.</li>
 * </ul>
 */
public final class ContestService {

    public enum State { IDLE, COUNTDOWN, RUNNING }

    /** A contest that has been decided on but not begun. */
    public record Planned(ContestEvent event, List<String> crops, long startAt, long slot) {
    }

    private static final long NOTICE_COOLDOWN = 5_000L;

    private final Plugin plugin;
    private final Settings settings;
    private final Lang lang;
    private final CropRegistry crops;
    private final EventRegistry events;
    private final BracketTable brackets;
    private final RewardService rewards;
    private final ContestDisplay display;
    private final File stateFile;

    private State state = State.IDLE;
    private Contest contest;
    private BukkitTask ticker;
    private long lastSlot = Long.MIN_VALUE;
    private long breakAnchor;
    private long breakIndex;
    private final Set<String> announced = new HashSet<>();
    private final Map<UUID, Long> lastNotice = new HashMap<>();
    private final Set<String> warnedPlaceholders = new HashSet<>();
    // live placings, rebuilt every second while a contest runs
    private Map<Participant, Ranking.Placed<Participant>> live = new IdentityHashMap<>();

    public ContestService(Plugin plugin, Settings settings, Lang lang, CropRegistry crops, EventRegistry events,
                          BracketTable brackets, RewardService rewards, ContestDisplay display) {
        this.plugin = plugin;
        this.settings = settings;
        this.lang = lang;
        this.crops = crops;
        this.events = events;
        this.brackets = brackets;
        this.rewards = rewards;
        this.display = display;
        this.stateFile = new File(plugin.getDataFolder(), "state.yml");
    }

    public void start() {
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(stateFile);
        this.breakIndex = saved.getLong("break-index", 0);
        this.lastSlot = saved.getLong("last-slot", Long.MIN_VALUE);
        this.breakAnchor = System.currentTimeMillis();
        this.ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    /** Stop for shutdown: a running contest is paid out or dropped per {@code contest.on-shutdown}. */
    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        if (state == State.RUNNING) {
            finish(settings.endOnShutdown(), true);
        } else if (state == State.COUNTDOWN) {
            contest = null;
            state = State.IDLE;
        }
        display.clear();
        saveState();
    }

    private void saveState() {
        YamlConfiguration saved = new YamlConfiguration();
        saved.set("break-index", breakIndex);
        saved.set("last-slot", lastSlot);
        try {
            saved.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save state.yml: " + e.getMessage());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        try {
            switch (state) {
                case IDLE -> tickIdle(now);
                case COUNTDOWN -> tickCountdown(now);
                case RUNNING -> tickRunning(now);
            }
        } catch (RuntimeException failed) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Contest tick failed", failed);
        }
    }

    private void tickIdle(long now) {
        Planned next = next(now);
        if (next == null) {
            return;
        }
        long left = next.startAt() - now;
        for (long before : settings.announceBefore()) {
            if (left <= before && left > before - 2_000L && announced.add("up:" + next.slot() + ":" + before)) {
                lang.broadcast("contest-upcoming", "%event%", next.event().displayName(),
                        "%crops%", cropNames(next.crops()), "%time%", Durations.format(left));
            }
        }
        if (now >= next.startAt() - next.event().countdownMillis()) {
            begin(next);
        }
    }

    private void tickCountdown(long now) {
        long left = contest.startAt() - now;
        if (left <= 0) {
            run();
            return;
        }
        for (long at : settings.countdownAnnouncements()) {
            if (left <= at && left > at - 1_500L && announced.add("cd:" + at)) {
                lang.broadcast("contest-countdown", "%event%", contest.event().displayName(),
                        "%crops%", cropNames(contest.crops()), "%time%", Durations.format(left));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    settings.playSound(player, "countdown");
                }
            }
        }
        float progress = contest.event().countdownMillis() <= 0 ? 0f
                : (float) left / contest.event().countdownMillis();
        display.update(player -> countdownLine(player, left), progress);
    }

    private void tickRunning(long now) {
        if (settings.scoring() == Settings.Scoring.PLACEHOLDER) {
            pollPlaceholders();
        }
        rankLive();
        long left = contest.endAt() - now;
        if (left <= 0) {
            finish(true, false);
            return;
        }
        display.update(player -> runningLine(player, left),
                (float) left / contest.event().durationMillis());
    }

    /** The next contest the schedule will run, or null in manual mode or with no events. */
    public Planned next(long now) {
        if (events.isEmpty()) {
            return null;
        }
        return switch (settings.scheduleMode()) {
            case MANUAL -> null;
            case INTERVAL -> {
                long slot = Schedule.nextSlot(now, settings.intervalMillis(), settings.offsetMillis());
                if (lastSlot != Long.MIN_VALUE && slot <= lastSlot) {
                    slot = lastSlot + 1;
                }
                yield planSlot(slot);
            }
            case BREAK -> {
                ContestEvent event = events.forSlot(breakIndex, settings.selection());
                yield new Planned(event, events.cropsFor(event, breakIndex),
                        breakAnchor + settings.breakMillis() + event.countdownMillis(), breakIndex);
            }
        };
    }

    private Planned planSlot(long slot) {
        ContestEvent event = events.forSlot(slot, settings.selection());
        return new Planned(event, events.cropsFor(event, slot),
                Schedule.startOf(slot, settings.intervalMillis(), settings.offsetMillis()), slot);
    }

    /** The next {@code count} scheduled contests, for the calendar. */
    public List<Planned> upcoming(int count) {
        long now = System.currentTimeMillis();
        List<Planned> out = new ArrayList<>();
        Planned first = next(now);
        if (first == null) {
            return out;
        }
        out.add(first);
        for (int i = 1; i < count; i++) {
            Planned previous = out.get(i - 1);
            if (settings.scheduleMode() == Settings.ScheduleMode.INTERVAL) {
                out.add(planSlot(previous.slot() + 1));
            } else {
                long slot = previous.slot() + 1;
                ContestEvent event = events.forSlot(slot, settings.selection());
                long start = previous.startAt() + previous.event().durationMillis() + settings.breakMillis()
                        + event.countdownMillis();
                out.add(new Planned(event, events.cropsFor(event, slot), start, slot));
            }
        }
        return out;
    }

    private void begin(Planned planned) {
        contest = new Contest(planned.event(), planned.crops(), events.worldsFor(planned.event()),
                planned.startAt(), planned.slot());
        if (settings.scheduleMode() == Settings.ScheduleMode.INTERVAL && planned.slot() != Long.MIN_VALUE) {
            lastSlot = Math.max(lastSlot, planned.slot());
            saveState();
        }
        announced.clear();
        state = State.COUNTDOWN;
        long left = planned.startAt() - System.currentTimeMillis();
        if (left <= 0) {
            run();
            return;
        }
        lang.broadcast("contest-countdown", "%event%", planned.event().displayName(),
                "%crops%", cropNames(planned.crops()), "%time%", Durations.format(left));
    }

    /**
     * Start a contest now, outside the schedule.
     *
     * @param event null for the next event the schedule would have run
     * @return false if one is already counting down or running, or there is nothing to run
     */
    public boolean startNow(ContestEvent event, boolean skipCountdown) {
        if (state != State.IDLE) {
            return false;
        }
        long now = System.currentTimeMillis();
        ContestEvent chosen = event;
        if (chosen == null) {
            Planned next = next(now);
            chosen = next != null ? next.event() : events.forSlot(now / 1000, settings.selection());
        }
        if (chosen == null) {
            return false;
        }
        long seed = now / 1000;
        begin(new Planned(chosen, events.cropsFor(chosen, seed),
                now + (skipCountdown ? 0 : chosen.countdownMillis()), Long.MIN_VALUE));
        return true;
    }

    private void run() {
        state = State.RUNNING;
        lang.broadcast("contest-started", "%event%", contest.event().displayName(),
                "%crops%", cropNames(contest.crops()), "%time%", Durations.format(contest.event().durationMillis()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            settings.playSound(player, "start");
        }
    }

    /** End the running contest and pay it out. Returns false if none is running. */
    public boolean stop() {
        if (state != State.RUNNING) {
            return false;
        }
        finish(true, false);
        return true;
    }

    /** Drop a countdown or running contest with no results. Returns false if there is none. */
    public boolean cancel() {
        if (state == State.IDLE) {
            return false;
        }
        finish(false, false);
        return true;
    }

    private void finish(boolean payOut, boolean shuttingDown) {
        Contest ended = contest;
        boolean wasRunning = state == State.RUNNING;
        contest = null;
        state = State.IDLE;
        display.clear();
        live = new IdentityHashMap<>();
        announced.clear();
        if (settings.scheduleMode() == Settings.ScheduleMode.BREAK && ended != null
                && ended.slot() != Long.MIN_VALUE) {
            breakIndex++;
        }
        breakAnchor = System.currentTimeMillis();
        saveState();
        if (ended == null) {
            return;
        }
        if (!payOut || !wasRunning) {
            lang.broadcast("contest-cancelled", "%event%", ended.event().displayName());
            return;
        }
        if (settings.scoring() == Settings.Scoring.PLACEHOLDER && !shuttingDown) {
            pollPlaceholders(ended);
        }
        lang.broadcast("contest-ended", "%event%", ended.event().displayName());
        long finishedAt = System.currentTimeMillis();
        int minimum = settings.minimumScore();
        for (String cropId : ended.crops()) {
            CropType crop = crops.get(cropId);
            String cropName = crop == null ? cropId : crop.displayName();
            List<Participant> entries = new ArrayList<>(ended.entries(cropId));
            if (entries.isEmpty()) {
                continue;
            }
            List<Ranking.Placed<Participant>> placed = Ranking.rank(entries, Participant::score, minimum, brackets.all());
            lang.broadcastPlain("results-crop", "%crop%", cropName,
                    "%participants%", String.valueOf(entries.size()));
            int shown = 0;
            for (Ranking.Placed<Participant> p : placed) {
                if (shown++ >= settings.broadcastTop()) {
                    break;
                }
                lang.broadcastPlain("results-line", "%position%", String.valueOf(p.position()),
                        "%player%", p.entry().name(), "%score%", String.valueOf(p.score()),
                        "%bracket%", bracketName(p.bracket()), "%crop%", cropName);
            }
            Set<Participant> ranked = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Ranking.Placed<Participant> p : placed) {
                ranked.add(p.entry());
                Participant entry = p.entry();
                rewards.record(new Result(ended.id(), ended.event().id(), cropId, entry.player(), entry.name(),
                                p.score(), p.position(), p.total(), p.bracket() == null ? null : p.bracket().id(),
                                finishedAt),
                        ended.event().displayName(), p.bracket(), cropName, shuttingDown);
                Player online = Bukkit.getPlayer(entry.player());
                if (online != null) {
                    lang.send(online, "result-personal", "%crop%", cropName, "%score%", String.valueOf(p.score()),
                            "%position%", String.valueOf(p.position()), "%participants%", String.valueOf(p.total()),
                            "%bracket%", bracketName(p.bracket()));
                }
            }
            for (Participant entry : entries) {
                if (ranked.contains(entry) || entry.score() <= 0) {
                    continue;
                }
                rewards.record(new Result(ended.id(), ended.event().id(), cropId, entry.player(), entry.name(),
                        entry.score(), 0, placed.size(), null, finishedAt), ended.event().displayName(), null,
                        cropName, shuttingDown);
                Player online = Bukkit.getPlayer(entry.player());
                if (online != null) {
                    lang.send(online, "below-minimum", "%crop%", cropName, "%score%", String.valueOf(entry.score()),
                            "%minimum%", String.valueOf(minimum));
                }
            }
        }
        if (!shuttingDown) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                settings.playSound(player, "end");
            }
        }
    }

    /**
     * Count a harvest. The listener has already checked world, crop, ripeness and placed blocks; this
     * checks the player can enter the contest for that crop and adds the points.
     *
     * @return the player's entry if the harvest counted, or null
     */
    public Participant score(Player player, CropType crop, long points) {
        if (state != State.RUNNING || !contest.has(crop.id())) {
            return null;
        }
        UUID id = player.getUniqueId();
        Participant entry = contest.entry(id, crop.id());
        if (entry == null) {
            String locked = contest.lockedCrop(id);
            if (settings.lockIn() && locked != null && !locked.equals(crop.id())) {
                CropType lockedCrop = crops.get(locked);
                notice(player, "wrong-crop", "%crop%", lockedCrop == null ? locked : lockedCrop.displayName());
                return null;
            }
            Settings.Requirement unmet = settings.unmet(player);
            if (unmet != null) {
                if (unmet.message().isEmpty()) {
                    notice(player, "requirement-not-met", "%required%", String.valueOf(unmet.min()));
                } else if (noticeDue(player)) {
                    player.sendMessage(com.mystipixel.royalcontests.config.Text.color(unmet.message()));
                }
                return null;
            }
            entry = contest.join(id, player.getName(), crop.id());
            if (settings.lockIn()) {
                contest.lock(id, crop.id());
            }
            lang.send(player, "joined", "%crop%", crop.displayName());
            settings.playSound(player, "join");
        }
        if (settings.scoring() == Settings.Scoring.PLACEHOLDER && entry.snapshot() == Long.MIN_VALUE) {
            readPlaceholder(player, crop).ifPresent(entry::reading);
        }
        if (!entry.withinRate(settings.maxBreaksPerSecond(), System.currentTimeMillis())) {
            return null;
        }
        if (settings.scoring() != Settings.Scoring.PLACEHOLDER) {
            entry.add(points);
        }
        return entry;
    }

    private OptionalLong readPlaceholder(Player player, CropType crop) {
        String placeholder = settings.scoringPlaceholder().replace("%collection%", crop.collection());
        double value = Papi.number(player, placeholder);
        if (Double.isNaN(value)) {
            if (warnedPlaceholders.add(placeholder)) {
                plugin.getLogger().warning("Scoring placeholder " + placeholder + " did not return a number; "
                        + "check scoring.placeholder and the crop's collection id.");
            }
            return OptionalLong.empty();
        }
        return OptionalLong.of((long) value);
    }

    private void pollPlaceholders() {
        pollPlaceholders(contest);
    }

    private void pollPlaceholders(Contest target) {
        for (String cropId : target.crops()) {
            CropType crop = crops.get(cropId);
            if (crop == null) {
                continue;
            }
            for (Participant entry : target.entries(cropId)) {
                Player player = Bukkit.getPlayer(entry.player());
                if (player != null) {
                    readPlaceholder(player, crop).ifPresent(entry::reading);
                }
            }
        }
    }

    private void rankLive() {
        Map<Participant, Ranking.Placed<Participant>> ranks = new IdentityHashMap<>();
        for (String cropId : contest.crops()) {
            for (Ranking.Placed<Participant> p : Ranking.rank(new ArrayList<>(contest.entries(cropId)),
                    Participant::score, settings.minimumScore(), brackets.all())) {
                ranks.put(p.entry(), p);
            }
        }
        live = ranks;
    }

    private String countdownLine(Player player, long left) {
        if (settings.displayOnlyInContestWorlds() && !contest.counts(player.getWorld().getName())) {
            return null;
        }
        return lang.raw("display-countdown", "%event%", contest.event().displayName(),
                "%crops%", cropNames(contest.crops()), "%time%", Durations.format(left));
    }

    private String runningLine(Player player, long left) {
        if (settings.displayOnlyInContestWorlds() && !contest.counts(player.getWorld().getName())) {
            return null;
        }
        Participant entry = contest.bestOf(player.getUniqueId());
        if (entry == null) {
            return lang.raw("display-watching", "%event%", contest.event().displayName(),
                    "%crops%", cropNames(contest.crops()), "%time%", Durations.format(left));
        }
        Ranking.Placed<Participant> placed = live.get(entry);
        CropType crop = crops.get(entry.crop());
        return lang.raw("display-playing", "%event%", contest.event().displayName(),
                "%crop%", crop == null ? entry.crop() : crop.displayName(),
                "%score%", String.valueOf(entry.score()),
                "%position%", placed == null ? "-" : String.valueOf(placed.position()),
                "%participants%", String.valueOf(contest.entries(entry.crop()).size()),
                "%bracket%", placed == null
                        ? lang.raw("bracket-below-minimum", "%minimum%", String.valueOf(settings.minimumScore()))
                        : bracketName(placed.bracket()),
                "%time%", Durations.format(left));
    }

    private String bracketName(Bracket bracket) {
        return bracket == null ? lang.raw("bracket-none") : bracket.displayName();
    }

    public String cropNames(List<String> ids) {
        List<String> names = new ArrayList<>(ids.size());
        for (String id : ids) {
            CropType crop = crops.get(id);
            names.add(crop == null ? id : crop.displayName());
        }
        return String.join(lang.raw("crop-separator"), names);
    }

    private void notice(Player player, String key, String... placeholders) {
        if (noticeDue(player)) {
            lang.send(player, key, placeholders);
        }
    }

    // refusals are rate-limited: one per few seconds, not one per crop broken
    private boolean noticeDue(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(player.getUniqueId());
        if (last != null && now - last < NOTICE_COOLDOWN) {
            return false;
        }
        lastNotice.put(player.getUniqueId(), now);
        return true;
    }

    public State state() {
        return state;
    }

    /** The contest counting down or running, or null. */
    public Contest contest() {
        return contest;
    }

    public Ranking.Placed<Participant> placing(Participant entry) {
        return live.get(entry);
    }

    public long timeLeft() {
        if (contest == null) {
            return 0;
        }
        long target = state == State.COUNTDOWN ? contest.startAt() : contest.endAt();
        return Math.max(0, target - System.currentTimeMillis());
    }

    public void forget(UUID player) {
        lastNotice.remove(player);
    }
}
