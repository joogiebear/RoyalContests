package com.mystipixel.royalcontests.contest;

import java.util.List;

/**
 * A kind of contest, from {@code events.yml}.
 *
 * @param crops       crop ids from crops.yml
 * @param randomCrops if above zero, each contest of this event picks this many of {@code crops} at
 *                    random (Hypixel's three-of-ten)
 * @param worlds      world patterns this event counts in; empty means {@code contest.worlds}
 * @param weight      how often {@code random} selection picks this event, relative to the others
 */
public record ContestEvent(String id, String displayName, List<String> crops, int randomCrops,
                           long durationMillis, long countdownMillis, List<String> worlds, int weight) {
}
