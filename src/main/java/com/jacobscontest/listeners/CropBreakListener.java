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
 * Listens for crop breaks during an active contest.
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

        // For ageable crops (wheat, carrots, potatoes, beetroot, nether wart),
        // only count fully grown crops
        if (blockData instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) {
                return; // Not fully grown, don't count
            }
        }

        // For non-ageable crops like MELON and PUMPKIN, any break counts
        // The ContestManager will validate if this material is part of the active event

        plugin.getContestManager().handleCropBreak(player, blockType);
    }
}
