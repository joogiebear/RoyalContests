package com.mystipixel.royalcontests.hook;

import org.bukkit.block.Block;

import java.util.function.Predicate;
import java.util.logging.Logger;

public final class RegenZonesTestAccess {

    private RegenZonesTestAccess() {
    }

    public static RegenZones of(Predicate<Block> inZone) {
        return new RegenZones(inZone::test, Logger.getAnonymousLogger());
    }

    public static RegenZones none() {
        return new RegenZones(Logger.getAnonymousLogger());
    }
}
