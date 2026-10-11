package com.mystipixel.royalcontests.hook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PapiTest {

    private static final String PLACEHOLDER = "%aurora_collections_farming_wheat_count_raw%";

    @Test
    void readsResolvedNumbers() {
        assertEquals(1250.0, Papi.readNumber(PLACEHOLDER, "1,250"));
        assertEquals(0.0, Papi.readNumber(PLACEHOLDER, "0"));
    }

    @Test
    void unresolvedIsNotANumber() {
        assertTrue(Double.isNaN(Papi.readNumber(PLACEHOLDER, PLACEHOLDER)));
        assertTrue(Double.isNaN(Papi.readNumber(PLACEHOLDER, null)));
        assertTrue(Double.isNaN(Papi.readNumber(PLACEHOLDER, "")));
        assertTrue(Double.isNaN(Papi.readNumber(PLACEHOLDER, "  ")));
        assertTrue(Double.isNaN(Papi.readNumber(PLACEHOLDER, "%statistic_mine_block:WHEAT_2%")));
        assertTrue(Double.isNaN(Papi.readNumber(PLACEHOLDER, "loading")));
    }
}
