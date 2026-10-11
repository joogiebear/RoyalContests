package com.mystipixel.royalcontests.hook;

import com.mystipixel.royalcontests.config.Durations;
import com.mystipixel.royalcontests.config.Lang;
import com.mystipixel.royalcontests.contest.Bracket;
import com.mystipixel.royalcontests.contest.BracketTable;
import com.mystipixel.royalcontests.contest.Contest;
import com.mystipixel.royalcontests.contest.ContestService;
import com.mystipixel.royalcontests.contest.Participant;
import com.mystipixel.royalcontests.contest.Ranking;
import com.mystipixel.royalcontests.crop.CropRegistry;
import com.mystipixel.royalcontests.crop.CropType;
import com.mystipixel.royalcontests.storage.Records.Leader;
import com.mystipixel.royalcontests.storage.Records.PlayerStats;
import com.mystipixel.royalcontests.storage.StatsCache;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Locale;

/**
 * {@code %royalcontests_...%}, also registered as {@code %jacobscontest_...%} so scoreboards written
 * for the old plugin keep working.
 *
 * <pre>
 *   active, state, event, crops, time_left
 *   crop, score, position, participants, bracket        (the viewer's current entry)
 *   next_event, next_crops, next_in
 *   unclaimed
 *   pb_&lt;crop&gt;, total_&lt;crop&gt;, contests_&lt;crop&gt;, best_bracket_&lt;crop&gt;
 *   top_&lt;crop&gt;_&lt;n&gt;_name, top_&lt;crop&gt;_&lt;n&gt;_score
 * </pre>
 *
 * <p>Answered from memory (the contest and {@link StatsCache}), never the database: scoreboards ask on
 * the server thread several times a second.
 */
public final class ContestExpansion extends PlaceholderExpansion {

    private final Plugin plugin;
    private final String identifier;
    private final Lang lang;
    private final ContestService contests;
    private final CropRegistry crops;
    private final BracketTable brackets;
    private final StatsCache cache;

    public ContestExpansion(Plugin plugin, String identifier, Lang lang, ContestService contests,
                            CropRegistry crops, BracketTable brackets, StatsCache cache) {
        this.plugin = plugin;
        this.identifier = identifier;
        this.lang = lang;
        this.contests = contests;
        this.crops = crops;
        this.brackets = brackets;
        this.cache = cache;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return "Mystipixel";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        String key = params.toLowerCase(Locale.ROOT);
        Contest contest = contests.contest();
        switch (key) {
            case "active":
                return String.valueOf(contests.state() == ContestService.State.RUNNING);
            case "state":
                return contests.state().name().toLowerCase(Locale.ROOT);
            case "event":
                return contest == null ? "" : contest.event().displayName();
            case "crops":
                return contest == null ? "" : contests.cropNames(contest.crops());
            case "time_left":
            case "timeleft":
                return contest == null ? "" : Durations.format(contests.timeLeft());
            case "next_event":
            case "next_crops":
            case "next_in": {
                ContestService.Planned next = contests.next(System.currentTimeMillis());
                if (next == null) {
                    return "";
                }
                return switch (key) {
                    case "next_event" -> next.event().displayName();
                    case "next_crops" -> contests.cropNames(next.crops());
                    default -> Durations.format(next.startAt() - System.currentTimeMillis());
                };
            }
            default:
                break;
        }
        if (key.startsWith("top_")) {
            return top(key.substring(4));
        }
        if (player == null) {
            return "";
        }
        switch (key) {
            case "unclaimed":
                return String.valueOf(cache.unclaimed(player.getUniqueId()));
            case "crop":
            case "score":
            case "position":
            case "participants":
            case "bracket":
                return entry(player, contest, key);
            default:
                break;
        }
        for (String prefix : new String[]{"best_bracket_", "pb_", "total_", "contests_"}) {
            if (key.startsWith(prefix)) {
                PlayerStats stats = cache.stats(player.getUniqueId(), key.substring(prefix.length()));
                return switch (prefix) {
                    case "pb_" -> stats == null ? "0" : String.valueOf(stats.personalBest());
                    case "total_" -> stats == null ? "0" : String.valueOf(stats.totalHarvested());
                    case "contests_" -> stats == null ? "0" : String.valueOf(stats.contests());
                    default -> {
                        Bracket best = stats == null ? null : brackets.get(stats.bestBracket());
                        yield best == null ? lang.raw("bracket-none") : best.displayName();
                    }
                };
            }
        }
        return null;
    }

    private String entry(OfflinePlayer player, Contest contest, String key) {
        Participant entry = contest == null ? null : contest.bestOf(player.getUniqueId());
        if (entry == null) {
            return switch (key) {
                case "score" -> "0";
                case "position" -> "-";
                default -> "";
            };
        }
        Ranking.Placed<Participant> placed = contests.placing(entry);
        CropType crop = crops.get(entry.crop());
        return switch (key) {
            case "crop" -> crop == null ? entry.crop() : crop.displayName();
            case "score" -> String.valueOf(entry.score());
            case "position" -> placed == null ? "-" : String.valueOf(placed.position());
            case "participants" -> String.valueOf(contest.entries(entry.crop()).size());
            default -> placed == null || placed.bracket() == null ? lang.raw("bracket-none") : placed.bracket().displayName();
        };
    }

    // <crop>_<n>_name or <crop>_<n>_score; crop ids may contain underscores
    private String top(String rest) {
        int last = rest.lastIndexOf('_');
        int middle = last <= 0 ? -1 : rest.lastIndexOf('_', last - 1);
        if (middle <= 0) {
            return "";
        }
        String crop = rest.substring(0, middle);
        String field = rest.substring(last + 1);
        int n;
        try {
            n = Integer.parseInt(rest.substring(middle + 1, last));
        } catch (NumberFormatException notANumber) {
            return "";
        }
        List<Leader> leaders = cache.leaders(crop);
        if (n < 1 || n > leaders.size()) {
            return field.equals("score") ? "0" : lang.raw("leaderboard-nobody");
        }
        Leader leader = leaders.get(n - 1);
        return field.equals("score") ? String.valueOf(leader.score()) : leader.name();
    }
}
