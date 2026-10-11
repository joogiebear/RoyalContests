package com.mystipixel.royalcontests.hook;

import com.mystipixel.royalregen.RoyalRegenPlugin;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

// The only class that touches RoyalRegen's types, so it is loaded only when RoyalRegen is installed.
final class RoyalRegenLookup implements RegenZones.Lookup {

    private final RoyalRegenPlugin regen;

    RoyalRegenLookup(Plugin regen) {
        this.regen = (RoyalRegenPlugin) regen;
    }

    @Override
    public boolean inZone(Block block) {
        return regen.isEnabled() && regen.zoneAt(block) != null;
    }
}
