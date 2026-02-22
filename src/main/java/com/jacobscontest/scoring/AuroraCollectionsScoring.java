package com.jacobscontest.scoring;

import com.jacobscontest.JacobsContest;
import com.jacobscontest.contest.PlayerContestData;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Scoring via AuroraCollections PAPI placeholders.
 * Snapshots the collection count on lock-in, compares periodically.
 */
public class AuroraCollectionsScoring implements ScoringProvider {

    private final JacobsContest plugin;

    public AuroraCollectionsScoring(JacobsContest plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPlayerLockIn(Player player, PlayerContestData data) {
        long snapshot = getCollectionCount(player, data.getCollectionId());
        data.setCollectionSnapshot(snapshot);
    }

    @Override
    public void updateScores(Map<UUID, PlayerContestData> participants) {
        for (PlayerContestData data : participants.values()) {
            if (!data.isLocked()) continue;
            Player player = Bukkit.getPlayer(data.getPlayerId());
            if (player == null || !player.isOnline()) continue;

            long current = getCollectionCount(player, data.getCollectionId());
            int score = (int) Math.max(0, current - data.getCollectionSnapshot());
            data.setScore(score);
        }
    }

    private long getCollectionCount(Player player, String collectionId) {
        if (collectionId == null) return 0;
        try {
            String category = plugin.getConfig().getString("scoring.aurora.category", "farming");
            String placeholder = "%aurora_collections_" + category + "_" + collectionId + "_count_raw%";
            String resolved = PlaceholderAPI.setPlaceholders(player, placeholder);
            resolved = resolved.replace(",", "").replace(".", "").trim();
            return Long.parseLong(resolved);
        } catch (Exception e) {
            return 0;
        }
    }
}
