package com.mystipixel.royalcontests.crop;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers which crop blocks a player placed, so they never score.
 *
 * <p>Without this, a melon, pumpkin or stack of sugar cane is a point you can place and break forever.
 * Positions live in each chunk's persistent data, so they survive restarts and unload with the chunk —
 * no file of our own, nothing to grow without bound.
 *
 * <p>Only blocks of crops marked {@code natural-only} are recorded. A block that <em>grows</em> into a
 * spot clears any stale mark there, and pistons carry marks along with the blocks they move, so pushing
 * a placed pumpkin does not launder it into a natural one.
 */
public final class PlacedBlocks implements Listener {

    private final NamespacedKey key;
    private final CropRegistry crops;

    public PlacedBlocks(Plugin plugin, CropRegistry crops) {
        this.key = new NamespacedKey(plugin, "placed");
        this.crops = crops;
    }

    /** Pack a block's position within its chunk into one int. */
    private static int pack(Block block) {
        return ((block.getY() + 4096) << 8) | ((block.getX() & 15) << 4) | (block.getZ() & 15);
    }

    public boolean isPlaced(Block block) {
        int[] marks = block.getChunk().getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        if (marks == null) {
            return false;
        }
        int packed = pack(block);
        for (int mark : marks) {
            if (mark == packed) {
                return true;
            }
        }
        return false;
    }

    public void mark(Block block) {
        if (isPlaced(block)) {
            return;
        }
        PersistentDataContainer pdc = block.getChunk().getPersistentDataContainer();
        int[] marks = pdc.getOrDefault(key, PersistentDataType.INTEGER_ARRAY, new int[0]);
        int[] grown = new int[marks.length + 1];
        System.arraycopy(marks, 0, grown, 0, marks.length);
        grown[marks.length] = pack(block);
        pdc.set(key, PersistentDataType.INTEGER_ARRAY, grown);
    }

    public void unmark(Block block) {
        Chunk chunk = block.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        int[] marks = pdc.get(key, PersistentDataType.INTEGER_ARRAY);
        if (marks == null) {
            return;
        }
        int packed = pack(block);
        int kept = 0;
        int[] out = new int[marks.length];
        for (int mark : marks) {
            if (mark != packed) {
                out[kept++] = mark;
            }
        }
        if (kept == marks.length) {
            return;
        }
        if (kept == 0) {
            pdc.remove(key);
        } else {
            int[] trimmed = new int[kept];
            System.arraycopy(out, 0, trimmed, 0, kept);
            pdc.set(key, PersistentDataType.INTEGER_ARRAY, trimmed);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (crops.tracksPlacement(block.getType())) {
            mark(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        unmark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        carry(event.getBlocks(), facing(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        BlockFace facing = facing(event.getBlock());
        carry(event.getBlocks(), facing == null ? null : facing.getOppositeFace());
    }

    private static BlockFace facing(Block piston) {
        return piston.getBlockData() instanceof Directional directional ? directional.getFacing() : null;
    }

    /** Move marks with the blocks a piston moves: clear them all first, then set them one step along. */
    private void carry(List<Block> moved, BlockFace direction) {
        if (direction == null) {
            return;
        }
        List<Block> marked = new ArrayList<>();
        for (Block block : moved) {
            if (isPlaced(block)) {
                marked.add(block);
            }
        }
        for (Block block : marked) {
            unmark(block);
        }
        for (Block block : marked) {
            mark(block.getRelative(direction));
        }
    }
}
