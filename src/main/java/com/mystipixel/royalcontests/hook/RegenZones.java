package com.mystipixel.royalcontests.hook;

import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.util.logging.Logger;

/** Whether a block is inside a RoyalRegen zone. Always false when RoyalRegen isn't installed. */
public final class RegenZones {

    interface Lookup {
        boolean inZone(Block block);
    }

    private final Logger logger;
    private Lookup lookup;

    public RegenZones(Logger logger) {
        this.logger = logger;
    }

    RegenZones(Lookup lookup, Logger logger) {
        this.lookup = lookup;
        this.logger = logger;
    }

    /** Hook the RoyalRegen that is enabled now, or nothing. Called on enable and on reload. */
    public void hook(PluginManager plugins) {
        lookup = null;
        Plugin regen = plugins.getPlugin("RoyalRegen");
        if (regen == null || !regen.isEnabled()) {
            return;
        }
        try {
            lookup = new RoyalRegenLookup(regen);
        } catch (LinkageError | ClassCastException incompatible) {
            logger.warning("This RoyalRegen doesn't offer the zone lookup RoyalContests uses; harvests in its "
                    + "zones will count. (" + incompatible + ")");
        }
    }

    public boolean active() {
        return lookup != null;
    }

    public boolean contains(Block block) {
        if (lookup == null) {
            return false;
        }
        try {
            return lookup.inZone(block);
        } catch (LinkageError incompatible) {
            // a RoyalRegen whose zoneAt(Block) changed shape; stop asking rather than fail every break
            lookup = null;
            logger.warning("RoyalRegen's zone lookup changed; harvests in its zones will count until "
                    + "RoyalContests is updated. (" + incompatible + ")");
            return false;
        }
    }
}
