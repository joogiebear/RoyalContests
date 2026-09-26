package com.mystipixel.royalcontests.contest;

/**
 * Clock arithmetic for the interval schedule. Pure, so it can be tested without a server.
 *
 * <p>Contests start at {@code offset + k * interval} milliseconds since the epoch, for whole k — so
 * with a 1h interval and a 15m offset, every hour at :15, the same on every server and across restarts.
 * {@code k} is the contest's <em>slot</em>, and it is what picks the event and its crops.
 */
public final class Schedule {

    private Schedule() {
    }

    /** The slot whose start is the first at or after {@code now}. */
    public static long nextSlot(long now, long interval, long offset) {
        return Math.floorDiv(now - offset + interval - 1, interval);
    }

    public static long startOf(long slot, long interval, long offset) {
        return slot * interval + offset;
    }
}
