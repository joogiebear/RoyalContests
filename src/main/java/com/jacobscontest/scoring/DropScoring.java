package com.jacobscontest.scoring;

import com.jacobscontest.JacobsContest;
import com.jacobscontest.contest.PlayerContestData;
import org.bukkit.Bukkit;
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
 * Scoring based on actual item drops from broken crops.
 * Captures Fortune and most plugin-modified drops via BlockDropItemEvent.
 * Falls back to block.getDrops() if BlockDropItemEvent doesn't fire.
 */
public class DropScoring implements ScoringProvider, Listener {

    private final JacobsContest plugin;
    private final Map<UUID, Integer> pendingBaseline = Collections.synchronizedMap(new HashMap<>());

    public DropScoring(JacobsContest plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPlayerLockIn(Player player, PlayerContestData data) {
        // No snapshot needed for drop scoring
    }

    @Override
    public void updateScores(Map<UUID, PlayerContestData> participants) {
        // Scores updated via events, not polling
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

        // Calculate baseline from block.getDrops()
        Collection<ItemStack> drops = event.getBlock().getDrops(player.getInventory().getItemInMainHand(), player);
        int baselinePoints = 0;
        for (ItemStack stack : drops) {
            baselinePoints += getPointValue(stack) * stack.getAmount();
        }
        if (baselinePoints <= 0) baselinePoints = 1;

        pendingBaseline.put(uuid, baselinePoints);

        final int fallback = baselinePoints;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Integer pending = pendingBaseline.remove(uuid);
            if (pending != null) {
                data.addScore(fallback);
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!plugin.getContestManager().isContestActive()) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Integer baseline = pendingBaseline.remove(uuid);
        if (baseline == null) return;

        PlayerContestData data = plugin.getContestManager().getParticipants().get(uuid);
        if (data == null) return;

        int actualPoints = 0;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            actualPoints += getPointValue(stack) * stack.getAmount();
        }

        data.addScore(Math.max(actualPoints, baseline));
    }

    private int getPointValue(ItemStack stack) {
        if (stack == null) return 0;

        // Check EcoItems
        try {
            var meta = stack.getItemMeta();
            if (meta != null) {
                var key = new org.bukkit.NamespacedKey("ecoitems", "item");
                var pdc = meta.getPersistentDataContainer();
                if (pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
                    String ecoId = "ecoitems:" + pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
                    int pts = plugin.getConfig().getInt("scoring.drops.point-values." + ecoId, -1);
                    if (pts > 0) return pts;
                }
            }
        } catch (Exception ignored) {}

        return plugin.getConfig().getInt("scoring.drops.point-values." + stack.getType().name(), 0);
    }
}
