package com.example.service;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LunchChallengeTest {

    @Test
    public void supportsMoreThanTwoParticipants() {
        LunchChallenge lunch = new LunchChallenge("room", Arrays.asList("a", "b", "c"));
        assertEquals(List.of("a", "b", "c"), lunch.participantIds());
        lunch.settle("c");
        assertTrue(lunch.canChoose("c"));
        assertFalse(lunch.canChoose("a"));
        assertFalse(lunch.decide("a", "拉麵"));
        assertTrue(lunch.decide("c", "拉麵"));
        assertEquals(LunchChallenge.Status.DECIDED, lunch.status());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void participantsAreReadOnly() {
        new LunchChallenge("room", Arrays.asList("a", "b")).participantIds().add("c");
    }

    @Test(expected = IllegalArgumentException.class)
    public void winnerMustBeParticipant() {
        new LunchChallenge("room", Arrays.asList("a", "b")).settle("z");
    }

    @Test
    public void blankChoiceIsRejected() {
        LunchChallenge lunch = new LunchChallenge("room", Arrays.asList("a", "b"));
        lunch.settle("a");
        assertFalse(lunch.decide("a", "   "));
        assertEquals(LunchChallenge.Status.AWAITING_CHOICE, lunch.status());
        assertEquals("", lunch.resultText());
    }

    @Test
    public void resultTextNamesWinnerAndMeal() {
        LunchChallenge lunch = new LunchChallenge("room", Arrays.asList("小明", "阿華"));
        lunch.settle("小明");
        lunch.decide("小明", "牛肉麵");
        assertEquals("【午餐爭奪戰結果】\n勝者：小明\n今天午餐：牛肉麵\n由小明決定！", lunch.resultText());
    }

    @Test
    public void settleOnlyOnce() {
        LunchChallenge lunch = new LunchChallenge("room", Arrays.asList("a", "b"));
        lunch.settle(null);
        lunch.settle("a");
        assertEquals(LunchChallenge.Status.DRAW, lunch.status());
        assertFalse(lunch.canChoose("a"));
    }
}
