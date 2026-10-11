package com.mystipixel.royalcontests.gui;

import com.mystipixel.royalcontests.config.Durations;
import com.mystipixel.royalcontests.config.Lang;
import com.mystipixel.royalcontests.config.Settings;
import com.mystipixel.royalcontests.config.YamlFile;
import com.mystipixel.royalcontests.contest.Bracket;
import com.mystipixel.royalcontests.contest.BracketTable;
import com.mystipixel.royalcontests.contest.Contest;
import com.mystipixel.royalcontests.contest.ContestService;
import com.mystipixel.royalcontests.contest.Participant;
import com.mystipixel.royalcontests.contest.Ranking;
import com.mystipixel.royalcontests.crop.CropRegistry;
import com.mystipixel.royalcontests.crop.CropType;
import com.mystipixel.royalcontests.storage.Records.Leader;
import com.mystipixel.royalcontests.storage.Records.PendingReward;
import com.mystipixel.royalcontests.storage.Records.PlayerStats;
import com.mystipixel.royalcontests.storage.RewardService;
import com.mystipixel.royalcontests.storage.StatsCache;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The player menus, all described in {@code menus.yml}: the contest hub, rewards to claim, personal
 * stats, the calendar and the leaderboards.
 */
public final class Menus {

    private final Plugin plugin;
    private final YamlFile file;
    private final Settings settings;
    private final Lang lang;
    private final ContestService contests;
    private final CropRegistry crops;
    private final BracketTable brackets;
    private final RewardService rewards;
    private final StatsCache cache;

    public Menus(Plugin plugin, Settings settings, Lang lang, ContestService contests, CropRegistry crops,
                 BracketTable brackets, RewardService rewards, StatsCache cache) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "menus.yml", true);
        this.settings = settings;
        this.lang = lang;
        this.contests = contests;
        this.crops = crops;
        this.brackets = brackets;
        this.rewards = rewards;
        this.cache = cache;
    }

    public void reload() {
        file.reload();
    }

    private ConfigurationSection section(String path) {
        return file.yaml().getConfigurationSection(path);
    }

    private Menu menu(String path, int fallbackRows) {
        return new Menu(file.yaml().getInt(path + ".rows", fallbackRows),
                file.yaml().getString(path + ".title", "&8Contests"));
    }

    private void finish(Menu menu, Player player) {
        menu.fill(Items.filler(file.yaml().getString("filler", "black_stained_glass_pane")));
        menu.open(player);
    }

    private int slot(String path, int fallback) {
        return file.yaml().getInt(path + ".slot", fallback);
    }

    private String when(long millis) {
        String pattern = file.yaml().getString("time-format", "HH:mm");
        try {
            return DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));
        } catch (IllegalArgumentException badPattern) {
            return DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis));
        }
    }

    public void openMain(Player player) {
        Menu menu = menu("main", 4);
        String base = "main.items.";
        menu.set(file.yaml().getInt("main.contest-slot", 11), contestItem(player), null);
        menu.set(slot(base + "calendar", 13), Items.build(section(base + "calendar"), Material.CLOCK),
                this::openCalendar);
        menu.set(slot(base + "claim", 15), Items.build(section(base + "claim"), Material.CHEST,
                "%unclaimed%", String.valueOf(cache.unclaimed(player.getUniqueId()))), this::openClaim);
        menu.set(slot(base + "stats", 21), Items.build(section(base + "stats"), Material.BOOK), this::openStats);
        menu.set(slot(base + "leaderboards", 23), Items.build(section(base + "leaderboards"), Material.GOLD_INGOT),
                this::openLeaderboards);
        menu.set(slot(base + "close", 31), Items.build(section(base + "close"), Material.BARRIER),
                Player::closeInventory);
        // Optional, only when configured: leave for another plugin's menu, e.g. a server menu hub.
        String back = file.yaml().getString(base + "back.command", "");
        if (!back.isBlank()) {
            menu.set(slot(base + "back", 27), Items.build(section(base + "back"), Material.ARROW),
                    p -> runCommand(p, back));
        }
        finish(menu, player);
    }

    // run next tick, so the command can safely open another menu
    private void runCommand(Player player, String command) {
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
    }

    private org.bukkit.inventory.ItemStack contestItem(Player player) {
        String base = "main.items.";
        Contest contest = contests.contest();
        String time = Durations.format(contests.timeLeft());
        if (contests.state() == ContestService.State.RUNNING && contest != null) {
            Participant entry = contest.bestOf(player.getUniqueId());
            Ranking.Placed<Participant> placed = entry == null ? null : contests.placing(entry);
            CropType crop = entry == null ? null : crops.get(entry.crop());
            return Items.build(section(base + "contest-running"), Material.WHEAT,
                    "%event%", contest.event().displayName(), "%crops%", contests.cropNames(contest.crops()),
                    "%time%", time,
                    "%your-crop%", crop == null ? lang.raw("none") : crop.displayName(),
                    "%your-score%", entry == null ? "0" : String.valueOf(entry.score()),
                    "%your-position%", placed == null ? "-" : String.valueOf(placed.position()),
                    "%your-bracket%", placed == null || placed.bracket() == null
                            ? lang.raw("bracket-none") : placed.bracket().displayName());
        }
        if (contests.state() == ContestService.State.COUNTDOWN && contest != null) {
            return Items.build(section(base + "contest-countdown"), Material.CLOCK,
                    "%event%", contest.event().displayName(), "%crops%", contests.cropNames(contest.crops()),
                    "%time%", time);
        }
        ContestService.Planned next = contests.next(System.currentTimeMillis());
        if (next == null) {
            return Items.build(section(base + "contest-manual"), Material.HAY_BLOCK);
        }
        return Items.build(section(base + "contest-idle"), Material.HAY_BLOCK,
                "%event%", next.event().displayName(), "%crops%", contests.cropNames(next.crops()),
                "%time%", Durations.format(next.startAt() - System.currentTimeMillis()),
                "%at%", when(next.startAt()));
    }

    public void openClaim(Player player) {
        rewards.database().pending(player.getUniqueId()).thenAccept(list -> Bukkit.getScheduler().runTask(plugin,
                () -> showClaim(player, list)));
    }

    private void showClaim(Player player, List<PendingReward> list) {
        if (!player.isOnline()) {
            return;
        }
        Menu menu = menu("claim", 6);
        int capacity = menu.size() - 9;
        for (int i = 0; i < Math.min(capacity, list.size()); i++) {
            PendingReward reward = list.get(i);
            CropType crop = crops.get(reward.crop());
            menu.set(i, Items.build(section("claim.entry"), crop == null ? Material.WHEAT : crop.icon(), Material.WHEAT,
                    "%bracket%", reward.bracketDisplay(), "%event%", reward.eventName(),
                    "%crop%", crop == null ? reward.crop() : crop.displayName(),
                    "%score%", String.valueOf(reward.score()), "%position%", String.valueOf(reward.position()),
                    "%participants%", String.valueOf(reward.total()), "%date%", when(reward.timestamp())),
                    who -> rewards.claim(who, reward, ok -> openClaim(who)));
        }
        if (list.isEmpty()) {
            menu.set(slot("claim.empty", 22), Items.build(section("claim.empty"), Material.GRAY_DYE), null);
        } else {
            menu.set(slot("claim.claim-all", menu.size() - 2), Items.build(section("claim.claim-all"),
                    Material.EMERALD_BLOCK, "%count%", String.valueOf(list.size())),
                    who -> rewards.claimAll(who, count -> {
                        lang.send(who, "claimed-all", "%count%", String.valueOf(count));
                        openClaim(who);
                    }));
        }
        menu.set(slot("claim.back", menu.size() - 5), Items.build(section("claim.back"), Material.ARROW), this::openMain);
        finish(menu, player);
    }

    public void openStats(Player player) {
        Menu menu = menu("stats", 4);
        int slot = 0;
        for (CropType crop : crops.all()) {
            if (slot >= menu.size() - 9) {
                break;
            }
            PlayerStats stats = cache.stats(player.getUniqueId(), crop.id());
            Bracket best = stats == null ? null : brackets.get(stats.bestBracket());
            menu.set(slot++, Items.build(section("stats.entry"), crop.icon(), Material.WHEAT,
                    "%crop%", crop.displayName(),
                    "%best%", stats == null ? "0" : String.valueOf(stats.personalBest()),
                    "%total%", stats == null ? "0" : String.valueOf(stats.totalHarvested()),
                    "%contests%", stats == null ? "0" : String.valueOf(stats.contests()),
                    "%bracket%", best == null ? lang.raw("bracket-none") : best.displayName()), null);
        }
        menu.set(slot("stats.back", menu.size() - 5), Items.build(section("stats.back"), Material.ARROW), this::openMain);
        finish(menu, player);
    }

    public void openCalendar(Player player) {
        Menu menu = menu("calendar", 3);
        List<ContestService.Planned> upcoming = contests.upcoming(settings.calendarSize());
        long now = System.currentTimeMillis();
        int slot = 0;
        for (ContestService.Planned planned : upcoming) {
            if (slot >= menu.size() - 9) {
                break;
            }
            CropType first = crops.get(planned.crops().get(0));
            menu.set(slot++, Items.build(section("calendar.entry"), first == null ? Material.CLOCK : first.icon(),
                    Material.CLOCK, "%event%", planned.event().displayName(),
                    "%crops%", contests.cropNames(planned.crops()),
                    "%in%", Durations.format(planned.startAt() - now), "%at%", when(planned.startAt()),
                    "%duration%", Durations.format(planned.event().durationMillis())), null);
        }
        if (upcoming.isEmpty()) {
            menu.set(slot("calendar.empty", 13), Items.build(section("calendar.empty"), Material.GRAY_DYE), null);
        }
        menu.set(slot("calendar.back", menu.size() - 5), Items.build(section("calendar.back"), Material.ARROW), this::openMain);
        finish(menu, player);
    }

    public void openLeaderboards(Player player) {
        Menu menu = menu("leaderboards", 4);
        String lineFormat = file.yaml().getString("leaderboards.line", "&e#%position% &f%player% &7- &6%score%");
        int shown = file.yaml().getInt("leaderboards.lines", 5);
        int slot = 0;
        for (CropType crop : crops.all()) {
            if (slot >= menu.size() - 9) {
                break;
            }
            List<Leader> leaders = cache.leaders(crop.id());
            StringBuilder lines = new StringBuilder();
            for (int i = 0; i < Math.min(shown, leaders.size()); i++) {
                if (i > 0) {
                    lines.append('\n');
                }
                Leader leader = leaders.get(i);
                lines.append(com.mystipixel.royalcontests.config.Text.fill(lineFormat, "%position%",
                        String.valueOf(i + 1), "%player%", leader.name(), "%score%", String.valueOf(leader.score())));
            }
            menu.set(slot++, Items.build(section("leaderboards.entry"), crop.icon(), Material.WHEAT,
                    "%crop%", crop.displayName(),
                    "%top%", lines.isEmpty() ? lang.raw("leaderboard-empty") : lines.toString()), null);
        }
        menu.set(slot("leaderboards.back", menu.size() - 5), Items.build(section("leaderboards.back"), Material.ARROW),
                this::openMain);
        finish(menu, player);
    }
}
