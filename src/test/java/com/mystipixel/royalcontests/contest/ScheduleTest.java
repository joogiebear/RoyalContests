package com.mystipixel.royalcontests.contest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleTest {

    private static final long HOUR = 3_600_000L;
    private static final long QUARTER = 900_000L;

    @Test
    void nextSlotIsTheFirstStartAtOrAfterNow() {
        assertEquals(10, Schedule.nextSlot(10 * HOUR, HOUR, 0));
        assertEquals(11, Schedule.nextSlot(10 * HOUR + 1, HOUR, 0));
        assertEquals(10 * HOUR + QUARTER, Schedule.startOf(Schedule.nextSlot(10 * HOUR, HOUR, QUARTER), HOUR, QUARTER));
    }

    @Test
    void startsLandOnTheOffset() {
        long start = Schedule.startOf(Schedule.nextSlot(123_456_789L, HOUR, QUARTER), HOUR, QUARTER);
        assertEquals(QUARTER, start % HOUR);
    }
}
