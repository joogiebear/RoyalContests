package com.jacobscontest.storage;

import java.util.UUID;

/**
 * Persistent stats for a player for a specific crop.
 */
public class PlayerStats {

    private final UUID playerId;
    private final String crop;
    private int personalBest;
    private long totalHarvested;
    private int contestsParticipated;
    private String bestBracket;

    public PlayerStats(UUID playerId, String crop) {
        this.playerId = playerId;
        this.crop = crop;
        this.personalBest = 0;
        this.totalHarvested = 0;
        this.contestsParticipated = 0;
        this.bestBracket = "NONE";
    }

    public PlayerStats(UUID playerId, String crop, int personalBest, long totalHarvested,
                       int contestsParticipated, String bestBracket) {
        this.playerId = playerId;
        this.crop = crop;
        this.personalBest = personalBest;
        this.totalHarvested = totalHarvested;
        this.contestsParticipated = contestsParticipated;
        this.bestBracket = bestBracket;
    }

    public UUID getPlayerId() { return playerId; }
    public String getCrop() { return crop; }

    public int getPersonalBest() { return personalBest; }
    public void setPersonalBest(int personalBest) { this.personalBest = personalBest; }

    public long getTotalHarvested() { return totalHarvested; }
    public void addTotalHarvested(int amount) { this.totalHarvested += amount; }

    public int getContestsParticipated() { return contestsParticipated; }
    public void incrementContests() { this.contestsParticipated++; }

    public String getBestBracket() { return bestBracket; }
    public void setBestBracket(String bestBracket) { this.bestBracket = bestBracket; }

    /**
     * Update stats after a contest. Returns true if this was a new personal best.
     */
    public boolean updateAfterContest(int score, String bracket) {
        boolean newBest = false;
        contestsParticipated++;
        totalHarvested += score;

        if (score > personalBest) {
            personalBest = score;
            newBest = true;
        }

        // Update best bracket if this one is better
        if (isBetterBracket(bracket, bestBracket)) {
            bestBracket = bracket;
        }

        return newBest;
    }

    private static final String[] BRACKET_ORDER = {"DIAMOND", "PLATINUM", "GOLD", "SILVER", "BRONZE", "NONE"};

    private boolean isBetterBracket(String newBracket, String oldBracket) {
        int newIdx = indexOf(newBracket);
        int oldIdx = indexOf(oldBracket);
        return newIdx < oldIdx; // Lower index = better bracket
    }

    private int indexOf(String bracket) {
        for (int i = 0; i < BRACKET_ORDER.length; i++) {
            if (BRACKET_ORDER[i].equalsIgnoreCase(bracket)) return i;
        }
        return BRACKET_ORDER.length;
    }

    public String getCropDisplayName() {
        if (crop == null || crop.isEmpty()) return "Unknown";
        return crop.substring(0, 1).toUpperCase() + crop.substring(1).toLowerCase().replace('_', ' ');
    }
}
