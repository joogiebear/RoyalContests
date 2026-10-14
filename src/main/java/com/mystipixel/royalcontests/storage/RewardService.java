package com.mystipixel.royalcontests.storage;

import com.mystipixel.royalcontests.config.Lang;
import com.mystipixel.royalcontests.config.Settings;
import com.mystipixel.royalcontests.config.Text;
import com.mystipixel.royalcontests.contest.Bracket;
import com.mystipixel.royalcontests.contest.BracketTable;
import com.mystipixel.royalcontests.storage.Records.PendingReward;
import com.mystipixel.royalcontests.storage.Records.Result;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Results, stats and rewards for a finished contest.
 *
 * <p>A reward is saved with its placeholders already filled in, so editing {@code brackets.yml} later
 * never changes a reward someone has already earned.
 */
public final class RewardService {

    private static final String MESSAGE_PREFIX = "[message] ";

    private final Plugin plugin;
    private final Database database;
    private final Settings settings;
    private final Lang lang;
    private final BracketTable brackets;
    private final StatsCache cache;
    // rewards being claimed right now, so a double-click doesn't reach the database twice
    private final Set<Integer> claiming = new HashSet<>();

    public RewardService(Plugin plugin, Database database, Settings settings, Lang lang,
                         BracketTable brackets, StatsCache cache) {
        this.plugin = plugin;
        this.database = database;
        this.settings = settings;
        this.lang = lang;
        this.brackets = brackets;
        this.cache = cache;
    }

    /** One placing of a finished contest; {@code bracket} is null below the brackets. */
    public record Placing(Result result, String eventName, Bracket bracket, String cropName) {
    }

    /**
     * Record a finished contest: each result row, the players' stats, and a reward for everyone who
     * reached a bracket, in one database task.
     *
     * @param shuttingDown true when the contest is ending because the server is stopping; rewards are
     *                     then always left to claim, never auto-claimed into a player who is leaving
     */
    public void record(List<Placing> placings, boolean shuttingDown) {
        if (placings.isEmpty()) {
            return;
        }
        List<Database.Placing> rows = new ArrayList<>(placings.size());
        for (Placing placing : placings) {
            rows.add(new Database.Placing(placing.result(), reward(placing)));
        }
        database.record(rows, brackets::rank).thenAccept(recorded -> onMain(() -> {
            for (int i = 0; i < rows.size(); i++) {
                announce(placings.get(i), rows.get(i).reward(), recorded.get(i), shuttingDown);
            }
        }));
    }

    private PendingReward reward(Placing placing) {
        Result result = placing.result();
        Bracket bracket = placing.bracket();
        return bracket == null ? null : new PendingReward(-1, result.player(), result.playerName(),
                placing.eventName(), result.crop(), result.score(), result.score(), bracket.id(),
                bracket.displayName(), result.position(), result.total(),
                actions(bracket, result, placing.cropName()), System.currentTimeMillis());
    }

    private void announce(Placing placing, PendingReward reward, Database.Recorded recorded, boolean shuttingDown) {
        Result result = placing.result();
        cache.invalidate(result.player());
        Player player = Bukkit.getPlayer(result.player());
        if (player == null) {
            return;
        }
        if (recorded.newBest()) {
            lang.send(player, "personal-best", "%crop%", placing.cropName(), "%score%", String.valueOf(result.score()));
        }
        if (reward == null || recorded.rewardId() <= 0) {
            return;
        }
        if (settings.autoClaim() && !shuttingDown) {
            claim(player, withId(reward, recorded.rewardId()), ignored -> { });
        } else {
            lang.send(player, "reward-waiting", "%bracket%", placing.bracket().displayName(),
                    "%crop%", placing.cropName());
        }
    }

    private static PendingReward withId(PendingReward r, int id) {
        return new PendingReward(id, r.player(), r.playerName(), r.eventName(), r.crop(), r.score(),
                r.personalBest(), r.bracket(), r.bracketDisplay(), r.position(), r.total(), r.actions(), r.timestamp());
    }

    private static List<String> actions(Bracket bracket, Result result, String cropName) {
        String[] placeholders = {"%player%", result.playerName(), "%uuid%", result.player().toString(),
                "%crop%", result.crop(), "%crop_name%", cropName, "%score%", String.valueOf(result.score()),
                "%position%", String.valueOf(result.position()), "%participants%", String.valueOf(result.total()),
                "%bracket%", bracket.id()};
        List<String> out = new ArrayList<>();
        for (String command : bracket.commands()) {
            // "||" separates actions in storage, so it cannot appear inside one.
            out.add(Text.fill(command, placeholders).replace("||", "|"));
        }
        for (String message : bracket.messages()) {
            out.add(MESSAGE_PREFIX + Text.fill(message, placeholders).replace("||", "|"));
        }
        return out;
    }

    /**
     * Claim one reward. The database flips it to claimed atomically before its actions run, so a reward
     * pays out once however fast someone clicks.
     */
    public void claim(Player player, PendingReward reward, Consumer<Boolean> done) {
        if (!claiming.add(reward.id())) {
            done.accept(false);
            return;
        }
        database.claim(reward.id(), player.getUniqueId()).whenComplete((won, error) -> onMain(() -> {
            claiming.remove(reward.id());
            boolean ok = error == null && Boolean.TRUE.equals(won);
            if (ok) {
                run(player, reward);
                cache.invalidate(player.getUniqueId());
                settings.playSound(player, "claim");
                lang.send(player, "reward-claimed", "%bracket%", reward.bracketDisplay(),
                        "%event%", reward.eventName());
            }
            done.accept(ok);
        }));
    }

    public void claimAll(Player player, Consumer<Integer> done) {
        database.pending(player.getUniqueId()).thenAccept(rewards -> onMain(() -> {
            if (rewards.isEmpty()) {
                done.accept(0);
                return;
            }
            int[] claimed = {0};
            int[] remaining = {rewards.size()};
            for (PendingReward reward : rewards) {
                claim(player, reward, ok -> {
                    if (ok) {
                        claimed[0]++;
                    }
                    if (--remaining[0] == 0) {
                        done.accept(claimed[0]);
                    }
                });
            }
        }));
    }

    private void run(Player player, PendingReward reward) {
        for (String action : reward.actions()) {
            String line = action.replace("%player%", player.getName());
            if (line.startsWith(MESSAGE_PREFIX)) {
                player.sendMessage(Text.color(line.substring(MESSAGE_PREFIX.length())));
            } else if (!line.isBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.startsWith("/") ? line.substring(1) : line);
            }
        }
    }

    public Database database() {
        return database;
    }

    private void onMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Whether a reward belongs to this player, checked before claiming from a menu slot. */
    public static boolean owns(PendingReward reward, UUID player) {
        return reward.player().equals(player);
    }
}
