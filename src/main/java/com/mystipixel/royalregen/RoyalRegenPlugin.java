package com.mystipixel.royalregen;

import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

// Compile-time stand-in for RoyalRegen's main class, left out of the jar (see pom.xml). At runtime the
// real class comes from RoyalRegen. Only the members RoyalRegenLookup calls are declared here.
public final class RoyalRegenPlugin extends JavaPlugin {

    public Zone zoneAt(Block block) {
        throw new UnsupportedOperationException("stub");
    }
}
