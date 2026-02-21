package com.jacobscontest.gui;

import com.jacobscontest.JacobsContest;
import com.jacobscontest.storage.PendingReward;
import com.jacobscontest.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/**
 * Handles all GUI click interactions for contest menus.
 */
public class MenuListener implements Listener {

    private final JacobsContest plugin;

    public MenuListener(JacobsContest plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().title().toString();

        // Check if it's one of our menus (by plain text content)
        if (title.contains("Jacob's Contest")) {
            event.setCancelled(true);
            handleMainMenuClick(player, event.getSlot());
        } else if (title.contains("Claim Rewards")) {
            event.setCancelled(true);
            handleClaimMenuClick(player, event);
        }
    }

    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 11 -> {
                // Info sign — just informational, no action
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            }
            case 13 -> {
                // Events — just informational
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            }
            case 15 -> {
                // Claim Rewards — open claim menu
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                new ClaimMenu(plugin).open(player);
            }
            case 31 -> {
                // Close button
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            }
        }
    }

    private void handleClaimMenuClick(Player player, InventoryClickEvent event) {
        int slot = event.getSlot();
        int size = event.getInventory().getSize();

        // Back button (center of last row)
        if (slot == size - 5) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            new ContestMenu(plugin).open(player);
            return;
        }

        // Claim All button (right side of last row)
        if (slot == size - 2) {
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.EMERALD_BLOCK) {
                claimAll(player);
                return;
            }
        }

        // Check if clicking a reward item (not filler)
        if (event.getCurrentItem() == null) return;
        Material type = event.getCurrentItem().getType();
        if (type == Material.BLACK_STAINED_GLASS_PANE || type == Material.GRAY_DYE
                || type == Material.ARROW || type == Material.EMERALD_BLOCK) return;

        // Find the reward at this slot
        List<PendingReward> rewards = plugin.getStorage().getPendingRewards(player.getUniqueId());
        if (slot >= rewards.size()) return;

        PendingReward reward = rewards.get(slot);
        claimReward(player, reward);
    }

    private void claimAll(Player player) {
        List<PendingReward> rewards = plugin.getStorage().getPendingRewards(player.getUniqueId());
        if (rewards.isEmpty()) return;

        int count = 0;
        for (PendingReward reward : rewards) {
            // Execute all reward commands
            for (String cmd : reward.getCommands()) {
                String finalCmd = cmd
                        .replace("%player%", player.getName())
                        .replace("%crop%", reward.getCrop())
                        .replace("%score%", String.valueOf(reward.getScore()))
                        .replace("%bracket%", reward.getBracket())
                        .replace("%position%", String.valueOf(reward.getPosition()));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            }
            plugin.getStorage().markRewardClaimed(reward.getId());
            count++;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.sendMessage(MessageUtil.toComponent(
                "&e&lJacob's Contest &8» &aClaimed &6" + count + " &areward(s)!"));

        // Refresh the menu
        new ClaimMenu(plugin).open(player);
    }

    private void claimReward(Player player, PendingReward reward) {
        // Execute all reward commands
        for (String cmd : reward.getCommands()) {
            String finalCmd = cmd
                    .replace("%player%", player.getName())
                    .replace("%crop%", reward.getCrop())
                    .replace("%score%", String.valueOf(reward.getScore()))
                    .replace("%bracket%", reward.getBracket())
                    .replace("%position%", String.valueOf(reward.getPosition()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
        }

        // Mark as claimed in storage
        plugin.getStorage().markRewardClaimed(reward.getId());

        // Effects
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.sendMessage(MessageUtil.toComponent(
                "&e&lJacob's Contest &8» &aClaimed rewards from " + reward.getEventName() + "!"));

        // Refresh the menu
        new ClaimMenu(plugin).open(player);
    }
}
