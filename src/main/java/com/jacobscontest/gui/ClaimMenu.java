package com.jacobscontest.gui;

import com.jacobscontest.JacobsContest;
import com.jacobscontest.storage.PendingReward;
import com.jacobscontest.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.jacobscontest.gui.ContestMenu.createItem;

/**
 * Claim rewards menu — shows all pending rewards with crop icons.
 * Click to claim each one.
 */
public class ClaimMenu {

    public static final String MENU_TITLE = "§8Claim Rewards";
    private final JacobsContest plugin;

    public ClaimMenu(JacobsContest plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        List<PendingReward> rewards = plugin.getStorage().getPendingRewards(player.getUniqueId());

        // Size: at least 36 (4 rows), up to 54 (6 rows) if lots of rewards
        int rows = Math.min(6, Math.max(4, (int) Math.ceil((rewards.size() + 9) / 9.0)));
        int size = rows * 9;

        Inventory inv = Bukkit.createInventory(null, size, Component.text(MENU_TITLE));

        // Fill background
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }

        // Place reward items
        if (rewards.isEmpty()) {
            inv.setItem(13, createItem(Material.GRAY_DYE, "&7No Rewards",
                    List.of("&7You have no unclaimed rewards.", "", "&7Participate in a contest to earn some!")));
        } else {
            int slot = 0;
            for (PendingReward reward : rewards) {
                if (slot >= size - 9) break; // Leave last row for nav
                inv.setItem(slot, createRewardItem(reward));
                slot++;
            }
        }

        // Bottom row: Back button in middle
        int backSlot = size - 5; // Center of last row
        inv.setItem(backSlot, createItem(Material.ARROW, "&e← Back",
                List.of("&7Return to the main menu")));

        player.openInventory(inv);
    }

    private ItemStack createRewardItem(PendingReward reward) {
        Material cropIcon = getCropIcon(reward.getCrop());
        List<String> lore = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");
        String dateStr = sdf.format(new Date(reward.getTimestamp()));

        lore.add("&8" + dateStr);
        lore.add("");
        lore.add("&7Event: &f" + reward.getEventName());
        lore.add("&7Crop: &6" + reward.getCropDisplayName());
        lore.add("");
        lore.add("&7Your Score: &e" + reward.getScore());
        lore.add("&7Personal Best: &a" + reward.getPersonalBest());
        lore.add("&7Position: &f#" + reward.getPosition() + " &7of &f" + reward.getTotalParticipants());
        lore.add("");
        lore.add("&7Bracket: " + reward.getBracketDisplay());
        lore.add("");
        lore.add("&7Rewards:");
        for (String cmd : reward.getCommands()) {
            // Show a simplified version of the command
            String display = cmd.replace("eco give " + reward.getPlayerName() + " ", "")
                    .replace("eco give %player% ", "");
            lore.add("  &a• &f" + display);
        }
        lore.add("");
        lore.add("&e▶ Click to claim!");

        String title = reward.getBracketDisplay() + " &8— " + reward.getCropDisplayName();
        return createItem(cropIcon, title, lore);
    }

    private Material getCropIcon(String crop) {
        if (crop == null) return Material.WHEAT;
        return switch (crop.toUpperCase()) {
            case "WHEAT" -> Material.WHEAT;
            case "CARROT", "CARROTS" -> Material.CARROT;
            case "POTATO", "POTATOES" -> Material.POTATO;
            case "BEETROOT", "BEETROOTS" -> Material.BEETROOT;
            case "NETHER_WART" -> Material.NETHER_WART;
            case "MELON", "MELON_SLICE" -> Material.MELON_SLICE;
            case "PUMPKIN" -> Material.PUMPKIN;
            default -> Material.WHEAT;
        };
    }
}
