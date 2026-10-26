package com.example.game;

import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouletteTest {

    @Test
    public void zeroLosesEveryOutsideBet() {
        for (Roulette.BetKind kind : Roulette.BetKind.values()) {
            assertFalse(kind.label, Roulette.wins(kind, 0));
        }
        Roulette.Spin spin = Roulette.spin(Roulette.BetKind.RED, new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        });
        assertEquals(0, spin.number);
        assertFalse(spin.win);
        assertEquals(0, spin.returned(100));
    }

    @Test
    public void redBlackOddEvenAndRangesFollowTheNumber() {
        assertEquals(18, Roulette.RED_NUMBERS.length);
        Set<Integer> reds = new HashSet<>();
        for (int number : Roulette.RED_NUMBERS) {
            assertTrue(reds.add(number));
            assertTrue(number >= 1 && number <= 36);
        }
        assertTrue(Roulette.wins(Roulette.BetKind.RED, 1));
        assertTrue(Roulette.wins(Roulette.BetKind.ODD, 1));
        assertTrue(Roulette.wins(Roulette.BetKind.LOW, 1));
        assertFalse(Roulette.wins(Roulette.BetKind.BLACK, 1));
        assertFalse(Roulette.wins(Roulette.BetKind.EVEN, 1));
        assertFalse(Roulette.wins(Roulette.BetKind.HIGH, 1));

        assertTrue(Roulette.wins(Roulette.BetKind.BLACK, 2));
        assertTrue(Roulette.wins(Roulette.BetKind.EVEN, 2));
        assertTrue(Roulette.wins(Roulette.BetKind.LOW, 2));
        assertTrue(Roulette.wins(Roulette.BetKind.RED, 36));
        assertTrue(Roulette.wins(Roulette.BetKind.EVEN, 36));
        assertTrue(Roulette.wins(Roulette.BetKind.HIGH, 36));
        assertEquals(200, Roulette.spin(Roulette.BetKind.RED, new Random() {
            @Override
            public int nextInt(int bound) {
                return 1;
            }
        }).returned(100));
    }
}
