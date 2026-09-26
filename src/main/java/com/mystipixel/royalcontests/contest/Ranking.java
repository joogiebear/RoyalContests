package com.mystipixel.royalcontests.contest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * Turning scores into places and brackets. Pure, so it can be tested without a server.
 */
public final class Ranking {

    private Ranking() {
    }

    /** One ranked entry. */
    public record Placed<T>(T entry, long score, int position, int total, Bracket bracket) {
    }

    /**
     * Rank entries by score, highest first, and give each the best bracket it reaches.
     *
     * <p>Ties share a place — two players on 500 are both 1st, and the next is 3rd — so a tie never
     * decides a bracket by who happened to be listed first. Only entries scoring at least
     * {@code minimum} are ranked; the rest are left out.
     *
     * @param brackets best first; an entry that reaches none gets a null bracket
     */
    public static <T> List<Placed<T>> rank(List<T> entries, ToLongFunction<T> score, long minimum,
                                           List<Bracket> brackets) {
        List<T> qualified = new ArrayList<>();
        for (T entry : entries) {
            if (score.applyAsLong(entry) >= minimum) {
                qualified.add(entry);
            }
        }
        qualified.sort(Comparator.comparingLong(score).reversed());
        int total = qualified.size();
        List<Placed<T>> out = new ArrayList<>(total);
        int position = 0;
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < total; i++) {
            T entry = qualified.get(i);
            long value = score.applyAsLong(entry);
            if (i == 0 || value != previous) {
                position = i + 1;
                previous = value;
            }
            out.add(new Placed<>(entry, value, position, total, bracketFor(position, total, brackets)));
        }
        return out;
    }

    public static Bracket bracketFor(int position, int total, List<Bracket> brackets) {
        for (Bracket bracket : brackets) {
            if (bracket.reachedBy(position, total)) {
                return bracket;
            }
        }
        return null;
    }
}
