package com.jacobscontest.listeners;

import com.jacobscontest.JacobsContest;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Listens for crop breaks and counts actual item drops during an active contest.
 * 
 * Uses BlockBreakEvent to validate the crop and lock the player in,
 * then BlockDropItemEvent to count actual drops (respects Fortune, Eco enchants, pets, etc.)
 */
public class CropBreakListener implements Listener {

    private final JacobsContest plugin;

    // Track which block breaks were valid contest crops (block location -> player UUID)
    // Used to link BlockBreakEvent to the subsequent BlockDropItemEvent
    private final Map<String, UUID> pendingBreaks = new HashMap<>();

    public CropBreakListener(JacobsContest plugin) {
        this.plugin = plugin;
    }

    /**
     * Phase 1: Validate the crop break and lock the player into their crop.
     * We record the break so we can score the drops in BlockDropItemEvent.
     */
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

        // Check if this is a valid contest crop and handle lock-in
        if (!plugin.getContestManager().validateAndLockCrop(player, blockType)) {
            return;
        }

        // Record this as a pending break so we can score drops
        String locKey = block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
        pendingBreaks.put(locKey, player.getUniqueId());

        // Clean up old entries (safety net)
        if (pendingBreaks.size() > 1000) {
            pendingBreaks.clear();
        }
    }

    /**
     * Phase 2: Count actual item drops from the crop break.
     * This fires AFTER BlockBreakEvent and contains the real drops
     * (modified by Fortune, Eco enchants, pets, talismans, etc.)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!plugin.getContestManager().isContestActive()) return;

        Block block = event.getBlock();
        String locKey = event.getBlock().getWorld().getName() + ":" + event.getBlockState().getX() + ":" + event.getBlockState().getY() + ":" + event.getBlockState().getZ();

        UUID playerId = pendingBreaks.remove(locKey);
        if (playerId == null) return; // Not a tracked contest break

        // Count points from all dropped items
        int totalPoints = 0;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            int points = getPointValue(stack);
            totalPoints += points * stack.getAmount();
        }

        if (totalPoints > 0) {
            plugin.getContestManager().addPlayerScore(playerId, totalPoints);
        }
    }

    /**
     * Get the point value for an item stack.
     * Checks custom item IDs (ecoitems:enchanted_wheat) first, then vanilla material.
     */
    private int getPointValue(ItemStack stack) {
        // Check for custom item names in the point-values config
        // This handles EcoItems, custom named items, etc.
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            // Check by custom model data or display name for eco items
            // The config allows "ecoitems:item_id" format
            String customId = getEcoItemId(stack);
            if (customId != null) {
                int customPoints = plugin.getConfig().getInt("point-values." + customId, -1);
                if (customPoints > 0) return customPoints;
            }
        }

        // Fall back to vanilla material name
        String materialKey = stack.getType().name();
        return plugin.getConfig().getInt("point-values." + materialKey, 0);
    }

    /**
     * Attempt to get an EcoItems ID from the item's persistent data.
     * EcoItems stores the item ID in the PDC under the "ecoitems" namespace.
     */
    private String getEcoItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;

        // Try to read the eco item id from PDC
        ItemMeta meta = stack.getItemMeta();
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("ecoitems", "item");
        org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
            String ecoId = pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
            return "ecoitems:" + ecoId;
        }

        return null;
    }
}
