package com.mystipixel.royalcontests.scoring;

import com.mystipixel.royalcontests.config.Settings;
import com.mystipixel.royalcontests.contest.Contest;
import com.mystipixel.royalcontests.contest.ContestService;
import com.mystipixel.royalcontests.contest.Participant;
import com.mystipixel.royalcontests.crop.CropRegistry;
import com.mystipixel.royalcontests.crop.CropType;
import com.mystipixel.royalcontests.crop.PlacedBlocks;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Decides whether a broken block counts, and hands it to the contest.
 *
 * <p>Every rule lives here, once, whatever the scoring mode. The old plugin split them between a
 * lock-in listener and a listener per mode, and the modes did not agree: drop scoring counted any block
 * at all (breaking dirt scored a point), block scoring counted unripe crops (plant, break, repeat), and
 * neither checked for placed melons. A block counts only if:
 *
 * <ol>
 *   <li>a contest is running, in a world it covers, about this crop;</li>
 *   <li>it is fully grown, for crops that have an age;</li>
 *   <li>no player placed it, for crops marked {@code natural-only};</li>
 *   <li>the player is in survival or adventure — creative breaks nothing that matters.</li>
 * </ol>
 *
 * <p>Then the scoring mode decides the points: {@code blocks} per block, {@code drops} per crop item
 * actually dropped (so Fortune counts), {@code placeholder} from a collection plugin.
 */
public final class HarvestListener implements Listener {

    /** A break waiting for its drops, in drops mode. Keyed by block position. */
    private record PendingDrops(Participant entry, CropType crop, int stackedAbove) {
    }

    private final Settings settings;
    private final CropRegistry crops;
    private final PlacedBlocks placed;
    private final ContestService contests;
    private final Map<Long, PendingDrops> pending = new HashMap<>();

    public HarvestListener(Settings settings, CropRegistry crops, PlacedBlocks placed, ContestService contests) {
        this.settings = settings;
        this.crops = crops;
        this.placed = placed;
        this.contests = contests;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        CropType crop = crops.byBlock(block.getType());
        if (crop == null) {
            return;
        }
        boolean natural = !crop.naturalOnly() || !placed.isPlaced(block);
        // Whatever happens next, this position no longer holds a placed block.
        if (crop.naturalOnly()) {
            placed.unmark(block);
        }
        Contest contest = contests.contest();
        Player player = event.getPlayer();
        if (contests.state() != ContestService.State.RUNNING || contest == null || !contest.has(crop.id())
                || !contest.counts(block.getWorld().getName()) || !natural) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (crop.requireRipe() && block.getBlockData() instanceof Ageable ageable
                && ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }
        int stacked = crop.stacked() ? countStackedAbove(block, crop) : 0;
        int blocks = 1 + stacked;

        switch (settings.scoring()) {
            case BLOCKS -> contests.score(player, crop, (long) crop.points() * blocks);
            case PLACEHOLDER -> contests.score(player, crop, 0);
            case DROPS -> {
                Participant entry = contests.score(player, crop, 0);
                if (entry != null) {
                    pending.put(key(block), new PendingDrops(entry, crop, stacked));
                }
            }
        }
    }

    /**
     * How many blocks of the same crop stand on top of this one and will fall with it — sugar cane and
     * cactus break as a column, and only the bottom block fires a break event.
     */
    private int countStackedAbove(Block block, CropType crop) {
        int count = 0;
        Block above = block.getRelative(BlockFace.UP);
        while (count < 32 && crop.blocks().contains(above.getType())) {
            if (!crop.naturalOnly() || !placed.isPlaced(above)) {
                count++;
            }
            if (crop.naturalOnly()) {
                placed.unmark(above);
            }
            above = above.getRelative(BlockFace.UP);
        }
        return count;
    }

    /**
     * Drops mode: count what actually dropped. Runs at {@code MONITOR}, so a plugin that pockets drops
     * earlier (auto-pickup, sacks) leaves nothing to count — use placeholder scoring with those.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent event) {
        PendingDrops drops = pending.remove(key(event.getBlock()));
        if (drops == null) {
            return;
        }
        long count = 0;
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            if (drops.crop().drops().contains(stack.getType())) {
                count += stack.getAmount();
            }
        }
        // The column above falls as physics drops, which fire no event of their own: count one each.
        drops.entry().add(count + drops.stackedAbove());
    }

    /** Bone meal can be switched off in contest worlds while a contest runs. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        Contest contest = contests.contest();
        if (settings.disableBoneMeal() && contests.state() == ContestService.State.RUNNING && contest != null
                && contest.counts(event.getBlock().getWorld().getName())) {
            event.setCancelled(true);
        }
    }

    /** Forget breaks whose drops never came (creative, cancelled drops), so the map cannot grow. */
    public void clearStale() {
        pending.clear();
    }

    private static long key(Block block) {
        return ((long) block.getX() & 0x3FFFFFF) << 38 | ((long) block.getZ() & 0x3FFFFFF) << 12
                | ((long) block.getY() & 0xFFF);
    }
}
