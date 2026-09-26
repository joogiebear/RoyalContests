package com.mystipixel.royalcontests.config;

import com.mystipixel.royalcontests.hook.Papi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurationsTest {

    @Test
    void parses() {
        assertEquals(1_200_000L, Durations.parseMillis("20m", 0));
        assertEquals(5_400_000L, Durations.parseMillis("1h30m", 0));
        assertEquals(45_000L, Durations.parseMillis("45", 0));
        assertEquals(7L, Durations.parseMillis("soon", 7));
        assertEquals(7L, Durations.parseMillis("", 7));
    }

    @Test
    void formats() {
        assertEquals("20m", Durations.format(1_200_000L));
        assertEquals("1h 30m", Durations.format(5_400_000L));
        assertEquals("0s", Durations.format(0));
    }

    @Test
    void worldPatterns() {
        List<String> gardens = List.of("garden_*");
        assertTrue(Settings.worldMatches("garden_1234-abcd", gardens));
        assertTrue(Settings.worldMatches("GARDEN_x", gardens));
        assertFalse(Settings.worldMatches("world", gardens));
        assertTrue(Settings.worldMatches("SuperiorWorld", List.of("SuperiorWorld")));
        assertFalse(Settings.worldMatches("garden.x", List.of("garden?x")));
    }

    @Test
    void readsNumbersOutOfPlaceholders() {
        assertEquals(1250.0, Papi.parseNumber("1,250"));
        assertEquals(12.0, Papi.parseNumber("Level 12"));
        assertTrue(Double.isNaN(Papi.parseNumber("none")));
    }
}
