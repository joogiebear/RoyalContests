package com.mystipixel.royalcontests.gui;

import com.mystipixel.royalcontests.config.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One open menu. It is its inventory's holder, which is how clicks are recognised as ours; never match
 * menus by title, another inventory can share it.
 */
public final class Menu implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    public Menu(int rows, String title) {
        this.inventory = Bukkit.createInventory(this, Math.clamp(rows, 1, 6) * 9, Text.color(title));
    }

    public void set(int slot, ItemStack item, Consumer<Player> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    public void fill(ItemStack filler) {
        if (filler == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    public int size() {
        return inventory.getSize();
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Cancels every click and drag in our menus, and runs the action for a click on the menu itself. */
    public static final class Clicks implements org.bukkit.event.Listener {

        @EventHandler(ignoreCancelled = false)
        public void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder(false) instanceof Menu menu)) {
                return;
            }
            event.setCancelled(true);
            if (event.getClickedInventory() != menu.inventory || !(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            Consumer<Player> action = menu.actions.get(event.getSlot());
            if (action != null) {
                action.accept(player);
            }
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getView().getTopInventory().getHolder(false) instanceof Menu) {
                event.setCancelled(true);
            }
        }
    }
}
