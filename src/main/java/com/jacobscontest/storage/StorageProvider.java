package com.jacobscontest.storage;

import java.util.List;
import java.util.UUID;

/**
 * Interface for data storage. Implementations: SQLite (local) and MySQL.
 */
public interface StorageProvider {

    void init();
    void shutdown();

    // ---- Player Stats ----
    PlayerStats getPlayerStats(UUID uuid, String crop);
    List<PlayerStats> getAllPlayerStats(UUID uuid);
    void savePlayerStats(PlayerStats stats);

    // ---- Pending Rewards ----
    void savePendingReward(PendingReward reward);
    List<PendingReward> getPendingRewards(UUID uuid);
    void markRewardClaimed(int rewardId);
    void deleteReward(int rewardId);
}
