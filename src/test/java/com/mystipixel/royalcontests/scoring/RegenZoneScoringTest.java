package com.mystipixel.royalcontests.scoring;

import com.mystipixel.royalcontests.config.Settings;
import com.mystipixel.royalcontests.hook.RegenZonesTestAccess;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegenZoneScoringTest {

    private static final Block BLOCK = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(),
            new Class<?>[]{Block.class}, (self, method, args) -> null);

    @TempDir
    Path folder;

    @Test
    void theShippedDefaultLeavesRegenZonesOut() {
        Settings settings = settings(null);
        assertFalse(settings.countRegenZones());
        int[] lookups = {0};
        HarvestListener listener = listener(settings, lookups, true);
        assertTrue(listener.inUncountedRegenZone(BLOCK));
        assertEquals(1, lookups[0]);
    }

    @Test
    void outsideAZoneStillCounts() {
        int[] lookups = {0};
        assertFalse(listener(settings(null), lookups, false).inUncountedRegenZone(BLOCK));
    }

    @Test
    void countRegenZonesLetsThemCountWithoutALookup() throws IOException {
        Settings settings = settings("scoring:\n  count-regen-zones: true\n");
        assertTrue(settings.countRegenZones());
        int[] lookups = {0};
        assertFalse(listener(settings, lookups, true).inUncountedRegenZone(BLOCK));
        assertEquals(0, lookups[0]);
    }

    @Test
    void withoutRoyalRegenNothingChanges() {
        HarvestListener listener = new HarvestListener(settings(null), null, null, null,
                RegenZonesTestAccess.none());
        assertFalse(listener.inUncountedRegenZone(BLOCK));
    }

    private static HarvestListener listener(Settings settings, int[] lookups, boolean inZone) {
        return new HarvestListener(settings, null, null, null, RegenZonesTestAccess.of(block -> {
            lookups[0]++;
            return inZone;
        }));
    }

    private Settings settings(String serverConfig) {
        if (serverConfig != null) {
            try {
                Files.writeString(folder.resolve("config.yml"), serverConfig);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (self, method, args) -> switch (method.getName()) {
                    case "getDataFolder" -> folder.toFile();
                    case "getResource" -> getClass().getClassLoader().getResourceAsStream((String) args[0]);
                    case "getLogger" -> Logger.getAnonymousLogger();
                    default -> null;
                });
        return new Settings(plugin);
    }
}
