package com.jacobscontest.scoring;

import com.jacobscontest.JacobsContest;
import com.jacobscontest.contest.PlayerContestData;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Scoring via any custom PAPI placeholder.
 * Supports a configurable template with %crop% replaced at runtime.
 * Works with any stats/collection plugin that has PAPI support.
 */
public class PlaceholderScoring implements ScoringProvider {

    private final JacobsContest plugin;

    public PlaceholderScoring(JacobsContest plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPlayerLockIn(Player player, PlayerContestData data) {
        long snapshot = getPlaceholderValue(player, data.getCollectionId());
        data.setCollectionSnapshot(snapshot);
    }

    @Override
    public void updateScores(Map<UUID, PlayerContestData> participants) {
        for (PlayerContestData data : participants.values()) {
            if (!data.isLocked()) continue;
            Player player = Bukkit.getPlayer(data.getPlayerId());
            if (player == null || !player.isOnline()) continue;

            long current = getPlaceholderValue(player, data.getCollectionId());
            int score = (int) Math.max(0, current - data.getCollectionSnapshot());
            data.setScore(score);
        }
    }

    private long getPlaceholderValue(Player player, String collectionId) {
        if (collectionId == null) return 0;
        try {
            String template = plugin.getConfig().getString("scoring.placeholder.template",
                    "%aurora_collections_farming_%crop%_count_raw%");
            String placeholder = template.replace("%crop%", collectionId);
            String resolved = PlaceholderAPI.setPlaceholders(player, placeholder);
            resolved = resolved.replace(",", "").replace(".", "").trim();
            return Long.parseLong(resolved);
        } catch (Exception e) {
            return 0;
        }
    }
}
