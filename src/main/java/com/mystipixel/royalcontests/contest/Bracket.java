package com.mystipixel.royalcontests.contest;

import java.util.List;

/**
 * A reward bracket from {@code brackets.yml}.
 *
 * <p>A player reaches a bracket by placing in its top {@code topPercent} of their crop's qualifying
 * players, <em>or</em> in its top {@code topPositions} places. The positions rule is what makes small
 * contests fair: with five players, "top 2%" is nobody, but "top 1 position" is still the winner.
 *
 * @param commands console commands run when the reward is claimed
 * @param messages lines sent to the player when they claim it
 */
public record Bracket(String id, String displayName, double topPercent, int topPositions,
                      List<String> commands, List<String> messages) {

    public boolean reachedBy(int position, int total) {
        if (topPositions > 0 && position <= topPositions) {
            return true;
        }
        return total > 0 && position * 100.0 / total <= topPercent;
    }
}
