package com.example;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class BlackjackRulesTest {

    @Test
    public void shuffledDeckIsDeterministic() {
        List<BlackjackRules.Card> a = BlackjackRules.shuffledDeck(42L);
        List<BlackjackRules.Card> b = BlackjackRules.shuffledDeck(42L);
        List<BlackjackRules.Card> c = BlackjackRules.shuffledDeck(43L);
        assertEquals(BlackjackRules.DECK_SIZE, a.size());
        assertEquals(a, b);
        assertNotEquals(a.get(0), c.get(0));
    }

    @Test
    public void handValueTreatsAcesSoft() {
        // A + 9 = 20
        assertEquals(20, BlackjackRules.handValue(Arrays.asList(card(0), card(8))));
        // A + A + 9 = 21
        assertEquals(21, BlackjackRules.handValue(Arrays.asList(card(0), card(13), card(8))));
        // A + 10 + 10 → A 當 1 → 21
        assertEquals(21, BlackjackRules.handValue(Arrays.asList(card(0), card(9), card(10))));
    }

    @Test
    public void bustAndCompare() {
        List<BlackjackRules.Card> bust = Arrays.asList(card(9), card(10), card(11)); // 10+J+Q = 30
        List<BlackjackRules.Card> eighteen = Arrays.asList(card(7), card(10)); // 8+J
        assertTrue(BlackjackRules.isBust(bust));
        assertFalse(BlackjackRules.isBust(eighteen));
        assertEquals(-1, BlackjackRules.compare(bust, eighteen));
        assertEquals(1, BlackjackRules.compare(eighteen, bust));
        assertEquals(0, BlackjackRules.compare(eighteen, eighteen));
    }

    private static BlackjackRules.Card card(int index) {
        return new BlackjackRules.Card(index);
    }
}
