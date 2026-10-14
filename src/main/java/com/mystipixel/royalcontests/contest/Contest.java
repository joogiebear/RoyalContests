package com.mystipixel.royalcontests.contest;

import com.mystipixel.royalcontests.config.Settings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A contest that is counting down or running: the event, the crops chosen for it, when it starts and
 * ends, and everyone's scores.
 *
 * <p>It is a snapshot: a reload that edits the event does not reach into a contest already under way.
 */
public final class Contest {

    private final ContestEvent event;
    private final List<String> crops;
    private final List<String> worlds;
    private final long startAt;
    private final long endAt;
    private final long slot;
    // crop id -> player -> entry
    private final Map<String, Map<UUID, Participant>> entries = new LinkedHashMap<>();
    // the crop each player is locked to, when lock-in is on
    private final Map<UUID, String> locked = new HashMap<>();
    // world name -> counts; asked on every crop break and every second per online player
    private final Map<String, Boolean> countedWorlds = new ConcurrentHashMap<>();

    public Contest(ContestEvent event, List<String> crops, List<String> worlds, long startAt, long slot) {
        this.event = event;
        this.crops = List.copyOf(crops);
        this.worlds = List.copyOf(worlds);
        this.startAt = startAt;
        this.endAt = startAt + event.durationMillis();
        this.slot = slot;
        for (String crop : crops) {
            entries.put(crop, new LinkedHashMap<>());
        }
    }

    public ContestEvent event() {
        return event;
    }

    public List<String> crops() {
        return crops;
    }

    public long startAt() {
        return startAt;
    }

    public long endAt() {
        return endAt;
    }

    public long slot() {
        return slot;
    }

    /** A stable id for this contest, for its results. */
    public String id() {
        return event.id() + "@" + startAt;
    }

    public boolean counts(String world) {
        return countedWorlds.computeIfAbsent(world, name -> Settings.worldMatches(name, worlds));
    }

    public boolean has(String crop) {
        return entries.containsKey(crop);
    }

    public String lockedCrop(UUID player) {
        return locked.get(player);
    }

    public void lock(UUID player, String crop) {
        locked.put(player, crop);
    }

    public Participant entry(UUID player, String crop) {
        Map<UUID, Participant> byPlayer = entries.get(crop);
        return byPlayer == null ? null : byPlayer.get(player);
    }

    public Participant join(UUID player, String name, String crop) {
        return entries.get(crop).computeIfAbsent(player, id -> new Participant(id, name, crop));
    }

    public Collection<Participant> entries(String crop) {
        Map<UUID, Participant> byPlayer = entries.get(crop);
        return byPlayer == null ? List.of() : byPlayer.values();
    }

    /** Every entry a player has, across crops. */
    public List<Participant> entriesOf(UUID player) {
        List<Participant> out = new ArrayList<>();
        for (Map<UUID, Participant> byPlayer : entries.values()) {
            Participant entry = byPlayer.get(player);
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }

    /** A player's best entry, the one shown on their boss bar. */
    public Participant bestOf(UUID player) {
        Participant best = null;
        for (Participant entry : entriesOf(player)) {
            if (best == null || entry.score() > best.score()) {
                best = entry;
            }
        }
        return best;
    }

    public int participantCount() {
        int count = 0;
        for (Map<UUID, Participant> byPlayer : entries.values()) {
            count += byPlayer.size();
        }
        return count;
    }
}
