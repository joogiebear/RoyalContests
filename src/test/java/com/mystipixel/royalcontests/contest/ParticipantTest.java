package com.mystipixel.royalcontests.contest;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParticipantTest {

    private final Participant entry = new Participant(UUID.randomUUID(), "Steve", "wheat");

    @Test
    void firstReadingIsTheBaseline() {
        entry.reading(5_000);
        assertEquals(5_000, entry.snapshot());
        assertEquals(0, entry.score());
        entry.reading(5_120);
        assertEquals(120, entry.score());
    }

    @Test
    void noReadingMeansNoBaselineAndNoScore() {
        assertEquals(Long.MIN_VALUE, entry.snapshot());
        assertEquals(0, entry.score());
    }
}
