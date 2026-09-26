package com.mystipixel.royalcontests.contest;

import java.util.UUID;

/**
 * One player's entry for one crop in a running contest. Main-thread only.
 */
public final class Participant {

    private final UUID player;
    private final String name;
    private final String crop;
    private long score;
    private long snapshot = Long.MIN_VALUE;
    private long secondStamp;
    private int breaksThisSecond;

    public Participant(UUID player, String name, String crop) {
        this.player = player;
        this.name = name;
        this.crop = crop;
    }

    public UUID player() {
        return player;
    }

    public String name() {
        return name;
    }

    public String crop() {
        return crop;
    }

    public long score() {
        return score;
    }

    public void add(long points) {
        this.score += points;
    }

    public void setScore(long score) {
        this.score = Math.max(0, score);
    }

    /** The placeholder value at lock-in, for placeholder scoring; {@code Long.MIN_VALUE} if not taken. */
    public long snapshot() {
        return snapshot;
    }

    public void setSnapshot(long snapshot) {
        this.snapshot = snapshot;
    }

    /**
     * Count a break against the per-second limit. Returns false once the player is past it this second —
     * that break is not scored.
     */
    public boolean withinRate(int maxPerSecond, long now) {
        if (maxPerSecond <= 0) {
            return true;
        }
        long second = now / 1000;
        if (second != secondStamp) {
            secondStamp = second;
            breaksThisSecond = 0;
        }
        return ++breaksThisSecond <= maxPerSecond;
    }
}
