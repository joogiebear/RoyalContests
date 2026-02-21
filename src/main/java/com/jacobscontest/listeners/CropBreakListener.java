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
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

/**
 * Listens for crop breaks during an active contest.
 *
 * Scores based on the actual item drops from the block (respects Fortune, Eco enchants, etc.)
 * Uses BlockBreakEvent with getDrops() to calculate points in a single event.
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

        // Validate crop and lock player in
        if (!plugin.getContestManager().validateAndLockCrop(player, blockType)) {
            return;
        }

        // Calculate points from the drops this block will produce
        // getDrops() respects Fortune and other vanilla enchantments
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand(), player);

        int totalPoints = 0;
        for (ItemStack stack : drops) {
            int points = getPointValue(stack);
            totalPoints += points * stack.getAmount();
        }

        // If no configured drops matched, give 1 point as baseline
        if (totalPoints <= 0) {
            totalPoints = 1;
        }

        plugin.getContestManager().addPlayerScore(player.getUniqueId(), totalPoints);
    }

    /**
     * Get the point value for an item stack from config.
     */
    private int getPointValue(ItemStack stack) {
        if (stack == null) return 0;

        // Check for EcoItems via PDC
        String ecoId = getEcoItemId(stack);
        if (ecoId != null) {
            int customPoints = plugin.getConfig().getInt("point-values." + ecoId, -1);
            if (customPoints > 0) return customPoints;
        }

        // Fall back to vanilla material name
        String materialKey = stack.getType().name();
        return plugin.getConfig().getInt("point-values." + materialKey, 0);
    }

    /**
     * Attempt to get an EcoItems ID from the item's persistent data.
     */
    private String getEcoItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;

        try {
            var meta = stack.getItemMeta();
            var key = new org.bukkit.NamespacedKey("ecoitems", "item");
            var pdc = meta.getPersistentDataContainer();

            if (pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                String ecoId = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
                return "ecoitems:" + ecoId;
            }
        } catch (Exception ignored) {}

        return null;
    }
}
