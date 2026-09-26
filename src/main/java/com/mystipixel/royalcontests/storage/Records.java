package com.mystipixel.royalcontests.storage;

import java.util.List;
import java.util.UUID;

/**
 * The rows RoyalContests stores. Plain records: the database layer builds them, everything else reads
 * them.
 */
public final class Records {

    private Records() {
    }

    /** A player's lifetime record for one crop. */
    public record PlayerStats(UUID player, String crop, long personalBest, long totalHarvested,
                              int contests, String bestBracket) {
    }

    /**
     * A reward earned and not yet claimed.
     *
     * @param actions console commands to run on claim; an entry starting {@code [message] } is sent to
     *                the player instead
     */
    public record PendingReward(int id, UUID player, String playerName, String eventName, String crop,
                                long score, long personalBest, String bracket, String bracketDisplay,
                                int position, int total, List<String> actions, long timestamp) {
    }

    /** One player's result in one crop of one contest. */
    public record Result(String contestId, String event, String crop, UUID player, String playerName,
                         long score, int position, int total, String bracket, long finishedAt) {
    }

    /** One line of a crop's all-time leaderboard. */
    public record Leader(UUID player, String name, long score) {
    }
}
