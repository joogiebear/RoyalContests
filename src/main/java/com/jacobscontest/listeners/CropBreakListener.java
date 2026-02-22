package com.jacobscontest.listeners;

import com.jacobscontest.JacobsContest;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Listens for crop breaks to handle player lock-in during contests.
 *
 * Scoring is handled by AuroraCollections placeholder comparison
 * (snapshot at lock-in vs. current value). This listener only handles:
 * 1. Validating the crop is part of the active event
 * 2. Locking the player into their chosen crop
 * 3. Taking the initial AuroraCollections snapshot
 */
public class CropBreakListener implements Listener {

    private final JacobsContest plugin;

    public CropBreakListener(JacobsContest plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getContestManager().isContestActive()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();
        BlockData blockData = block.getBlockData();

        // For ageable crops, only count fully grown
        if (blockData instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) {
                return;
            }
        }

        // Validate crop and lock player in (this also takes the snapshot)
        plugin.getContestManager().validateAndLockCrop(player, blockType);
    }
}
