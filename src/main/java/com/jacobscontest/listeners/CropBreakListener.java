package com.jacobscontest.listeners;

import com.jacobscontest.JacobsContest;
import org.bukkit.Bukkit;
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

import java.util.*;

/**
 * Listens for crop breaks during an active contest.
 *
 * Two-phase scoring:
 * Phase 1 (BlockBreakEvent): Validate the crop break, lock player in, flag as pending.
 * Phase 2 (BlockDropItemEvent): Count the ACTUAL drops (after all plugins modify them)
 *         and award points. This captures Fortune, EcoEnchants, EcoSkills, pets, etc.
 *
 * If BlockDropItemEvent never fires (e.g., silk touch, or drops suppressed),
 * a fallback gives 1 point after the tick ends.
 */
public class CropBreakListener implements Listener {

    private final JacobsContest plugin;

    // Players who broke a valid crop this tick, waiting for drop event
    // Maps player UUID -> true (just a flag)
    private final Set<UUID> pendingPlayers = Collections.synchronizedSet(new HashSet<>());

    public CropBreakListener(JacobsContest plugin) {
        this.plugin = plugin;
    }

    /**
     * Phase 1: Validate crop break, lock player in, flag for drop counting.
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

        // Validate crop and lock player in
        if (!plugin.getContestManager().validateAndLockCrop(player, blockType)) {
            return;
        }

        // Flag this player as having a pending drop to score
        pendingPlayers.add(player.getUniqueId());

        // Schedule fallback: if BlockDropItemEvent doesn't fire by next tick, award 1 point
        UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingPlayers.remove(uuid)) {
                // BlockDropItemEvent never fired — give 1 point as fallback
                plugin.getContestManager().addPlayerScore(uuid, 1);
            }
        }, 1L);
    }

    /**
     * Phase 2: Count actual item drops. This fires AFTER all plugins have modified drops
     * (EcoEnchants, EcoSkills, Fortune, pets, talismans, etc.)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!plugin.getContestManager().isContestActive()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Only score if this player has a pending break
        if (!pendingPlayers.remove(uuid)) return;

        // Count points from all dropped items
        int totalPoints = 0;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            int points = getPointValue(stack);
            totalPoints += points * stack.getAmount();
        }

        // Award points (minimum 1 if they broke a valid crop)
        if (totalPoints <= 0) totalPoints = 1;

        plugin.getContestManager().addPlayerScore(uuid, totalPoints);
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
