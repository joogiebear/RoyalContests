package com.jacobscontest.contest;

import org.bukkit.Material;

import java.util.UUID;

/**
 * Tracks a player's state during an active contest.
 */
public class PlayerContestData {

    private final UUID playerId;
    private final String playerName;
    private Material lockedCrop;
    private long collectionSnapshot; // AuroraCollections count at lock-in time
    private int score; // Current score (collection_now - snapshot)

    public PlayerContestData(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.lockedCrop = null;
        this.collectionSnapshot = 0;
        this.score = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Material getLockedCrop() {
        return lockedCrop;
    }

    public boolean isLocked() {
        return lockedCrop != null;
    }

    public void lockCrop(Material crop) {
        this.lockedCrop = crop;
    }

    public long getCollectionSnapshot() {
        return collectionSnapshot;
    }

    public void setCollectionSnapshot(long snapshot) {
        this.collectionSnapshot = snapshot;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void addScore(int amount) {
        this.score += amount;
    }

    public String getCropName() {
        if (lockedCrop == null) return "none";
        String name = lockedCrop.name().toLowerCase().replace('_', ' ');
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public String getCropId() {
        if (lockedCrop == null) return "none";
        return lockedCrop.name().toLowerCase();
    }

    /**
     * Get the AuroraCollections collection ID for this crop.
     * Reads from config's collection-ids mapping, falls back to material name.
     */
    public String getCollectionId() {
        if (lockedCrop == null) return null;
        // Try config mapping first
        String configId = com.jacobscontest.JacobsContest.getInstance()
                .getConfig().getString("collection-ids." + lockedCrop.name());
        if (configId != null) return configId;
        // Fallback
        return lockedCrop.name().toLowerCase();
    }
}
