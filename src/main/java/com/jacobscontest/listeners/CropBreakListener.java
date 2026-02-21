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
 * Hybrid scoring approach to handle all scenarios:
 *
 * 1. BlockBreakEvent (MONITOR): Validate crop, lock player, calculate BASELINE score
 *    using block.getDrops() — captures vanilla + Fortune drops.
 *
 * 2. BlockDropItemEvent (HIGHEST): Count ACTUAL drops BEFORE virtual bag plugins
 *    might intercept them. If actual drops give MORE points than baseline, use those.
 *    This captures Eco enchants, skills, pets, talismans, etc.
 *
 * This means:
 * - Fortune: ✅ captured by block.getDrops()
 * - Eco buffs: ✅ captured by BlockDropItemEvent (if it fires)
 * - Virtual bag steals drops: ✅ still have baseline from getDrops()
 * - Both: ✅ we take the higher of the two scores
 */
public class CropBreakListener implements Listener {

    private final JacobsContest plugin;

    // Tracks pending break data per player: UUID -> baseline points
    private final Map<UUID, Integer> pendingBaseline = Collections.synchronizedMap(new HashMap<>());

    public CropBreakListener(JacobsContest plugin) {
        this.plugin = plugin;
    }

    /**
     * Phase 1: Validate crop, lock player, calculate baseline score.
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

        // Calculate baseline score from block.getDrops() (vanilla + Fortune)
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand(), player);
        int baselinePoints = 0;
        for (ItemStack stack : drops) {
            int points = getPointValue(stack);
            baselinePoints += points * stack.getAmount();
        }
        if (baselinePoints <= 0) baselinePoints = 1;

        // Store baseline and wait for BlockDropItemEvent
        UUID uuid = player.getUniqueId();
        pendingBaseline.put(uuid, baselinePoints);

        // Fallback: if BlockDropItemEvent doesn't fire, award baseline after this tick
        final int fallbackPoints = baselinePoints;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Integer pending = pendingBaseline.remove(uuid);
            if (pending != null) {
                // BlockDropItemEvent never fired — use baseline
                plugin.getContestManager().addPlayerScore(uuid, fallbackPoints);
            }
        }, 1L);
    }

    /**
     * Phase 2: Count actual drops BEFORE virtual bags intercept them.
     * Uses HIGHEST priority to see drops before bag plugins (which typically use MONITOR).
     * If actual drops > baseline, use actual. Otherwise keep baseline.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!plugin.getContestManager().isContestActive()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Only process if this player has a pending break
        Integer baseline = pendingBaseline.remove(uuid);
        if (baseline == null) return;

        // Count points from actual dropped items
        int actualPoints = 0;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            int points = getPointValue(stack);
            actualPoints += points * stack.getAmount();
        }

        // Use whichever is higher: actual drops (includes Eco buffs) or baseline (includes Fortune)
        int finalPoints = Math.max(actualPoints, baseline);
        if (finalPoints <= 0) finalPoints = 1;

        plugin.getContestManager().addPlayerScore(uuid, finalPoints);
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
