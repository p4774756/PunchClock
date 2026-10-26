package com.example.ui.game;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GameCompanionTest {

    @Test
    public void linesDoNotPushThePlayerToKeepBetting() {
        String[] banned = {"必勝", "穩贏", "加碼", "回本", "再押", "充值", "提領"};
        assertFalse(GameCompanion.lines().isEmpty());
        for (String line : GameCompanion.lines()) {
            for (String word : banned) {
                assertFalse(line, line.contains(word));
            }
            assertTrue(line.length() > 0);
        }
    }
}
