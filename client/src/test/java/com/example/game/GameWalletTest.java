package com.example.game;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GameWalletTest {

    @Test
    public void startsWithTenThousand() {
        GameWallet wallet = GameWallet.fresh();
        assertEquals(10_000, wallet.balance());
        assertFalse(wallet.roundOpen());
        assertTrue(wallet.records().isEmpty());
    }

    @Test
    public void rejectsInvalidBetsWithoutTakingPoints() {
        GameWallet wallet = GameWallet.fresh();
        assertThrows(GameRuleException.class, () -> wallet.begin("老虎機", 0));
        assertThrows(GameRuleException.class, () -> wallet.begin("老虎機", -20));
        assertThrows(GameRuleException.class, () -> wallet.begin("老虎機", 10_001));
        assertEquals(10_000, wallet.balance());
        assertFalse(wallet.roundOpen());
    }

    @Test
    public void settlesOnceAndBlocksAnotherRound() {
        GameWallet wallet = GameWallet.fresh();
        wallet.begin("老虎機", 100);
        assertEquals(9_900, wallet.balance());
        assertThrows(GameRuleException.class, () -> wallet.begin("輪盤", 10));
        assertEquals(9_900, wallet.balance());
        wallet.finish(0, "沒有對中");
        assertEquals(9_900, wallet.balance());
        assertFalse(wallet.roundOpen());
        assertThrows(GameRuleException.class, () -> wallet.finish(100, "再派一次"));
        assertEquals(9_900, wallet.balance());
        assertEquals(-100, wallet.records().get(0).delta);
    }

    @Test
    public void resetRestoresBalanceAfterConfirmationPoint() {
        GameWallet wallet = GameWallet.fresh();
        wallet.begin("21 點", 50);
        assertThrows(GameRuleException.class, wallet::reset);
        assertEquals(9_950, wallet.balance());
        wallet.finish(100, "贏");
        wallet.reset();
        assertEquals(10_000, wallet.balance());
        assertTrue(wallet.records().isEmpty());
        assertFalse(wallet.roundOpen());
    }
}
