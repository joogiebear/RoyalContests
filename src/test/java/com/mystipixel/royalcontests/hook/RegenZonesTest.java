package com.mystipixel.royalcontests.hook;

import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegenZonesTest {

    static final Block BLOCK = proxy(Block.class, (name, args) -> null);

    @Test
    void withoutRoyalRegenNothingIsInAZone() {
        RegenZones zones = new RegenZones(logger(new ArrayList<>()));
        zones.hook(plugins(null));
        assertFalse(zones.active());
        assertFalse(zones.contains(BLOCK));
    }

    @Test
    void aDisabledRoyalRegenIsIgnored() {
        RegenZones zones = new RegenZones(logger(new ArrayList<>()));
        zones.hook(plugins(plugin(false)));
        assertFalse(zones.active());
    }

    @Test
    void aPluginThatIsNotRoyalRegenIsIgnoredWithAWarning() {
        List<String> warnings = new ArrayList<>();
        RegenZones zones = new RegenZones(logger(warnings));
        zones.hook(plugins(plugin(true)));
        assertFalse(zones.active());
        assertFalse(zones.contains(BLOCK));
        assertEquals(1, warnings.size());
    }

    @Test
    void asksTheLookup() {
        RegenZones inside = new RegenZones(block -> true, logger(new ArrayList<>()));
        RegenZones outside = new RegenZones(block -> false, logger(new ArrayList<>()));
        assertTrue(inside.active());
        assertTrue(inside.contains(BLOCK));
        assertFalse(outside.contains(BLOCK));
    }

    @Test
    void anIncompatibleRoyalRegenStopsBeingAskedAfterOneWarning() {
        List<String> warnings = new ArrayList<>();
        int[] calls = {0};
        RegenZones zones = new RegenZones(block -> {
            calls[0]++;
            throw new NoSuchMethodError("zoneAt");
        }, logger(warnings));
        assertFalse(zones.contains(BLOCK));
        assertFalse(zones.contains(BLOCK));
        assertFalse(zones.active());
        assertEquals(1, calls[0]);
        assertEquals(1, warnings.size());
    }

    private static Plugin plugin(boolean enabled) {
        return proxy(Plugin.class, (name, args) -> name.equals("isEnabled") ? enabled : null);
    }

    private static PluginManager plugins(Plugin regen) {
        return proxy(PluginManager.class, (name, args) ->
                name.equals("getPlugin") && "RoyalRegen".equals(args[0]) ? regen : null);
    }

    static Logger logger(List<String> warnings) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        return logger;
    }

    interface Answer {
        Object answer(String method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, Answer answer) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (self, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    case "toString" -> type.getSimpleName() + " proxy";
                    default -> answer.answer(method.getName(), args);
                });
    }
}
