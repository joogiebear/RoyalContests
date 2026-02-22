package com.jacobscontest.scoring;

import com.jacobscontest.contest.PlayerContestData;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Interface for different scoring strategies.
 * Implementations determine how contest points are calculated.
 */
public interface ScoringProvider {

    /**
     * Called when a player locks into a crop. Take any snapshots needed.
     */
    void onPlayerLockIn(Player player, PlayerContestData data);

    /**
     * Called periodically to update all player scores.
     * Placeholder-based providers update here.
     * Event-based providers may no-op here (they update on events).
     */
    void updateScores(java.util.Map<java.util.UUID, PlayerContestData> participants);

    /**
     * Get an event listener if this provider needs one (for DROPS mode).
     * Return null if not needed.
     */
    default Listener getListener() { return null; }
}
