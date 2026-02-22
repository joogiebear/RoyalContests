package com.jacobscontest.scoring;

import com.jacobscontest.JacobsContest;
import com.jacobscontest.contest.PlayerContestData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Simplest scoring: 1 point per crop block broken.
 * Works everywhere, no dependencies. Does not count Fortune or modifiers.
 */
public class BlockBreakScoring implements ScoringProvider, Listener {

    private final JacobsContest plugin;

    public BlockBreakScoring(JacobsContest plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPlayerLockIn(Player player, PlayerContestData data) {
        // No setup needed
    }

    @Override
    public void updateScores(Map<UUID, PlayerContestData> participants) {
        // Scores updated via events
    }

    @Override
    public Listener getListener() {
        return this;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getContestManager().isContestActive()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerContestData data = plugin.getContestManager().getParticipants().get(uuid);
        if (data == null || !data.isLocked()) return;

        // Verify it's the right crop
        if (data.getLockedCrop() != event.getBlock().getType()) return;

        data.addScore(1);
    }
}
