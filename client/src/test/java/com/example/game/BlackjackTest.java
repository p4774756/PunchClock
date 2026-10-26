package com.example.game;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BlackjackTest {

    @Test
    public void aceCountsAsElevenUntilTheHandWouldBust() {
        assertEquals(21, Blackjack.handValue(Arrays.asList(card(1, 0), card(1, 1), card(9, 2))));
        assertEquals(17, Blackjack.handValue(Arrays.asList(card(1, 0), card(6, 1), card(10, 2))));
        assertEquals(12, Blackjack.handValue(Arrays.asList(card(1, 0), card(1, 1))));
        assertEquals(10, Blackjack.handValue(Arrays.asList(card(11, 0))));
        assertEquals(10, Blackjack.handValue(Arrays.asList(card(12, 1))));
        assertEquals(10, Blackjack.handValue(Arrays.asList(card(13, 2))));
    }

    @Test
    public void playerBustLosesImmediately() {
        Blackjack hand = Blackjack.start(Arrays.asList(
                card(10, 0), card(5, 1), card(10, 2), card(6, 3), card(13, 0)));
        hand.hit();
        assertTrue(hand.finished());
        assertEquals(Blackjack.Outcome.PLAYER_BUST, hand.outcome());
        assertEquals(0, hand.returnedFor(100));
        assertThrows(GameRuleException.class, hand::hit);
    }

    @Test
    public void blackjackPaysThreeToTwoAndTiesWhenDealerAlsoHasIt() {
        Blackjack player = Blackjack.start(Arrays.asList(
                card(1, 0), card(9, 1), card(13, 0), card(8, 1)));
        assertEquals(Blackjack.Outcome.PLAYER_BLACKJACK, player.outcome());
        assertEquals(250, player.returnedFor(100));

        Blackjack both = Blackjack.start(Arrays.asList(
                card(1, 0), card(1, 1), card(13, 2), card(12, 3)));
        assertEquals(Blackjack.Outcome.PUSH, both.outcome());
        assertEquals(80, both.returnedFor(80));

        Blackjack dealer = Blackjack.start(Arrays.asList(
                card(9, 0), card(1, 1), card(8, 2), card(13, 3)));
        assertEquals(Blackjack.Outcome.DEALER_BLACKJACK, dealer.outcome());
        assertEquals(0, dealer.returnedFor(40));
    }

    @Test
    public void dealerHitsUntilSeventeenAndStandsOnSoftSeventeen() {
        Blackjack draw = Blackjack.start(Arrays.asList(
                card(10, 0), card(5, 1), card(7, 2), card(6, 3), card(7, 0)));
        draw.stand();
        assertEquals(Blackjack.Outcome.DEALER_WIN, draw.outcome());
        assertEquals(18, draw.dealerValue());

        Blackjack soft = Blackjack.start(Arrays.asList(
                card(10, 0), card(1, 1), card(8, 2), card(6, 3)));
        soft.stand();
        assertEquals(17, soft.dealerValue());
        assertEquals(Blackjack.Outcome.PLAYER_WIN, soft.outcome());
        assertEquals(2, soft.dealerHand().size());
    }

    private static Blackjack.Card card(int rank, int suit) {
        return new Blackjack.Card(rank, suit);
    }
}
