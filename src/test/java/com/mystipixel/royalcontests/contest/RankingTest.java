package com.mystipixel.royalcontests.contest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RankingTest {

    private static final Bracket DIAMOND = new Bracket("diamond", "D", 2, 1, List.of(), List.of());
    private static final Bracket GOLD = new Bracket("gold", "G", 10, 3, List.of(), List.of());
    private static final Bracket BRONZE = new Bracket("bronze", "B", 60, 0, List.of(), List.of());
    private static final List<Bracket> LADDER = List.of(DIAMOND, GOLD, BRONZE);

    @Test
    void positionsRescueSmallContests() {
        // Five players: 1st is 20%, which is no percentage bracket above bronze — but it is position 1.
        var placed = Ranking.rank(List.of(500L, 400L, 300L, 200L, 100L), s -> s, 0, LADDER);
        assertEquals(DIAMOND, placed.get(0).bracket());
        assertEquals(GOLD, placed.get(1).bracket());
        assertEquals(GOLD, placed.get(2).bracket());
        assertNull(placed.get(3).bracket());                // 4/5 = 80%: past bronze's 60%
        assertNull(placed.get(4).bracket());
    }

    @Test
    void tiesShareAPlace() {
        var placed = Ranking.rank(List.of(500L, 500L, 100L), s -> s, 0, LADDER);
        assertEquals(1, placed.get(0).position());
        assertEquals(1, placed.get(1).position());
        assertEquals(3, placed.get(2).position());
        assertEquals(DIAMOND, placed.get(1).bracket());
    }

    @Test
    void minimumExcludesFromTheRanking() {
        var placed = Ranking.rank(List.of(50L, 150L, 99L), s -> s, 100, LADDER);
        assertEquals(1, placed.size());
        assertEquals(1, placed.get(0).total());
        assertEquals(150L, placed.get(0).score());
    }

    @Test
    void percentThresholds() {
        assertNull(Ranking.bracketFor(100, 100, List.of(DIAMOND, GOLD)));
        assertEquals(DIAMOND, Ranking.bracketFor(2, 100, LADDER));
        assertEquals(GOLD, Ranking.bracketFor(10, 100, LADDER));
        assertEquals(BRONZE, Ranking.bracketFor(60, 100, LADDER));
        assertNull(Ranking.bracketFor(61, 100, LADDER));
    }
}
