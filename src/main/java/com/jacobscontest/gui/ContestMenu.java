package com.jacobscontest.gui;

import com.jacobscontest.JacobsContest;
import com.jacobscontest.contest.ContestEvent;
import com.jacobscontest.contest.ContestManager;
import com.jacobscontest.storage.PendingReward;
import com.jacobscontest.storage.PlayerStats;
import com.jacobscontest.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Main contest menu — 9x4 (36 slots).
 * Row 1: Empty/decorative
 * Row 2: Info Sign | Current Event | Claim Rewards
 * Row 3: Empty/decorative
 * Row 4: Back button in middle
 */
public class ContestMenu {

    public static final String MENU_TITLE = "§8Jacob's Contest";
    private final JacobsContest plugin;

    public ContestMenu(JacobsContest plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, Component.text(MENU_TITLE));

        // Fill background
        ItemStack filler = createItem(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, filler);
        }

        // Row 2, Slot 11 (col 2) — Info Sign
        inv.setItem(11, createInfoItem(player));

        // Row 2, Slot 13 (col 4, center) — Current/Upcoming Events
        inv.setItem(13, createEventsItem());

        // Row 2, Slot 15 (col 6) — Claim Rewards
        inv.setItem(15, createClaimItem(player));

        // Row 4, Slot 31 (bottom row center) — Close Button
        inv.setItem(31, createItem(Material.BARRIER, "&cClose", List.of("&7Click to close this menu")));

        player.openInventory(inv);
    }

    private ItemStack createInfoItem(Player player) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Welcome to Jacob's Contest!");
        lore.add("");
        lore.add("&eHow it works:");
        lore.add("&7• Contests rotate automatically");
        lore.add("&7• Break crops to earn points");
        lore.add("&7• First crop you break locks you in");
        lore.add("&7• Top % of players earn brackets:");
        lore.add("  &b◆ Diamond &7(Top 2%)");
        lore.add("  &7◆ Platinum &7(Top 5%)");
        lore.add("  &6◆ Gold &7(Top 10%)");
        lore.add("  &f◆ Silver &7(Top 30%)");
        lore.add("  &c◆ Bronze &7(Top 60%)");
        lore.add("");

        // Player's overall stats
        List<PlayerStats> allStats = plugin.getStorage().getAllPlayerStats(player.getUniqueId());
        if (!allStats.isEmpty()) {
            lore.add("&eYour Stats:");
            for (PlayerStats stats : allStats) {
                lore.add("  &6" + stats.getCropDisplayName() + "&7: PB &f" + stats.getPersonalBest()
                        + " &7| Best &f" + stats.getBestBracket()
                        + " &7| Contests &f" + stats.getContestsParticipated());
            }
        } else {
            lore.add("&7No contest history yet!");
        }

        return createItem(Material.OAK_SIGN, "&e&lContest Info", lore);
    }

    private ItemStack createEventsItem() {
        ContestManager manager = plugin.getContestManager();
        List<String> lore = new ArrayList<>();

        if (manager.isContestActive()) {
            ContestEvent event = manager.getActiveEvent();
            lore.add("&a&lCURRENTLY ACTIVE");
            lore.add("");
            lore.add("&fEvent: " + event.displayName());
            lore.add("&fCrops: &6" + event.cropsDisplayString());
            lore.add("&fTime Left: &6" + ContestManager.formatTime(manager.getTimeRemaining()));
            lore.add("&fParticipants: &6" + manager.getParticipants().size());
        } else if (manager.isCountdownActive()) {
            lore.add("&e&lSTARTING SOON");
            lore.add("");
            lore.add("&fA contest is about to begin!");
        } else {
            lore.add("&7No contest active right now");
        }

        lore.add("");
        lore.add("&eUpcoming Events:");
        Map<String, ContestEvent> events = manager.getEvents();
        int count = 0;
        for (Map.Entry<String, ContestEvent> entry : events.entrySet()) {
            if (count >= 5) break;
            ContestEvent e = entry.getValue();
            lore.add("  &7• " + e.displayName() + " &8(" + e.cropsDisplayString() + ")");
            count++;
        }

        Material icon = manager.isContestActive() ? Material.GOLDEN_HOE : Material.IRON_HOE;
        return createItem(icon, "&6&lEvents", lore);
    }

    private ItemStack createClaimItem(Player player) {
        List<PendingReward> rewards = plugin.getStorage().getPendingRewards(player.getUniqueId());
        List<String> lore = new ArrayList<>();

        if (rewards.isEmpty()) {
            lore.add("&7No unclaimed rewards!");
            lore.add("");
            lore.add("&7Participate in contests to");
            lore.add("&7earn rewards!");
        } else {
            lore.add("&aYou have &e" + rewards.size() + " &aunclaimed reward(s)!");
            lore.add("");
            for (PendingReward r : rewards) {
                lore.add("  &7• " + r.getEventName() + " &8- " + r.getBracketDisplay());
            }
            lore.add("");
            lore.add("&eClick to view and claim!");
        }

        Material icon = rewards.isEmpty() ? Material.CHEST : Material.ENDER_CHEST;
        return createItem(icon, "&a&lClaim Rewards", lore);
    }

    public static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.toComponent(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> loreComponents = new ArrayList<>();
                for (String line : lore) {
                    loreComponents.add(MessageUtil.toComponent(line));
                }
                meta.lore(loreComponents);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }
}
