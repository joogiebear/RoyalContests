package com.jacobscontest.storage;

import java.util.List;
import java.util.UUID;

/**
 * A pending reward waiting to be claimed by a player.
 */
public class PendingReward {

    private int id; // Database ID, -1 if not yet saved
    private final UUID playerId;
    private final String playerName;
    private final String eventName;
    private final String crop;
    private final int score;
    private final int personalBest;
    private final String bracket;
    private final String bracketDisplay;
    private final int position;
    private final int totalParticipants;
    private final List<String> commands;
    private final long timestamp;
    private boolean claimed;

    public PendingReward(UUID playerId, String playerName, String eventName, String crop,
                         int score, int personalBest, String bracket, String bracketDisplay,
                         int position, int totalParticipants, List<String> commands) {
        this.id = -1;
        this.playerId = playerId;
        this.playerName = playerName;
        this.eventName = eventName;
        this.crop = crop;
        this.score = score;
        this.personalBest = personalBest;
        this.bracket = bracket;
        this.bracketDisplay = bracketDisplay;
        this.position = position;
        this.totalParticipants = totalParticipants;
        this.commands = commands;
        this.timestamp = System.currentTimeMillis();
        this.claimed = false;
    }

    // Full constructor for loading from DB
    public PendingReward(int id, UUID playerId, String playerName, String eventName, String crop,
                         int score, int personalBest, String bracket, String bracketDisplay,
                         int position, int totalParticipants, List<String> commands,
                         long timestamp, boolean claimed) {
        this.id = id;
        this.playerId = playerId;
        this.playerName = playerName;
        this.eventName = eventName;
        this.crop = crop;
        this.score = score;
        this.personalBest = personalBest;
        this.bracket = bracket;
        this.bracketDisplay = bracketDisplay;
        this.position = position;
        this.totalParticipants = totalParticipants;
        this.commands = commands;
        this.timestamp = timestamp;
        this.claimed = claimed;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public String getEventName() { return eventName; }
    public String getCrop() { return crop; }
    public int getScore() { return score; }
    public int getPersonalBest() { return personalBest; }
    public String getBracket() { return bracket; }
    public String getBracketDisplay() { return bracketDisplay; }
    public int getPosition() { return position; }
    public int getTotalParticipants() { return totalParticipants; }
    public List<String> getCommands() { return commands; }
    public long getTimestamp() { return timestamp; }
    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }

    public String getCropDisplayName() {
        if (crop == null || crop.isEmpty()) return "Unknown";
        return crop.substring(0, 1).toUpperCase() + crop.substring(1).toLowerCase().replace('_', ' ');
    }
}
