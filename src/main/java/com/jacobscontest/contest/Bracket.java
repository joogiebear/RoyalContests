package com.jacobscontest.contest;

import java.util.List;

/**
 * Represents a reward bracket (e.g., DIAMOND, GOLD, BRONZE).
 */
public record Bracket(
        String name,
        int threshold,
        String color,
        String displayName,
        List<String> commands
) {

    /**
     * Check if a player's percentile qualifies for this bracket.
     * @param percentile the player's percentile (0 = best, 100 = worst)
     * @return true if the player qualifies
     */
    public boolean qualifies(double percentile) {
        return percentile <= threshold;
    }
}
