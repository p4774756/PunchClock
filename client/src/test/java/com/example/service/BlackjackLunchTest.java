package com.example.service;

import com.example.BlackjackProtocol;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BlackjackLunchTest {

    /** 一組已開局的雙方。 */
    private static final class Pair {
        final BlackjackSession host;
        final BlackjackSession guest;

        Pair(BlackjackSession host, BlackjackSession guest) {
            this.host = host;
            this.guest = guest;
        }
    }

    private static Pair start(BlackjackSession.Mode mode, long seed) {
        BlackjackSession host = BlackjackSession.startInvite("alice", "bob", mode, seed);
        BlackjackProtocol.Message invite = BlackjackProtocol.parse(host.invitePayload());
        BlackjackSession guest = BlackjackSession.fromInvite("bob", "alice", invite.roomId, invite.seed,
                BlackjackSession.modeOf(invite));
        assertTrue(host.applyRemote(BlackjackProtocol.parse(guest.accept())));
        return new Pair(host, guest);
    }

    /** 雙方都直接停牌，結果只看開局兩張。 */
    private static Pair standOff(BlackjackSession.Mode mode, long seed) {
        Pair pair = start(mode, seed);
        assertTrue(pair.guest.applyRemote(BlackjackProtocol.parse(pair.host.stand())));
        assertTrue(pair.host.applyRemote(BlackjackProtocol.parse(pair.guest.stand())));
        return pair;
    }

    private static Pair findStandOff(BlackjackSession.Mode mode, BlackjackSession.Outcome hostOutcome) {
        for (long seed = 0; seed < 5000; seed++) {
            Pair pair = standOff(mode, seed);
            if (pair.host.outcome() == hostOutcome) {
                return pair;
            }
        }
        throw new AssertionError("no seed gives " + hostOutcome);
    }

    @Test
    public void lunchInviteCarriesModeToGuest() {
        Pair pair = start(BlackjackSession.Mode.LUNCH, 1L);
        assertTrue(pair.host.isLunch());
        assertTrue(pair.guest.isLunch());
        assertFalse(pair.host.downgraded());
        assertEquals(pair.host.lunch().participantIds(), pair.guest.lunch().participantIds());
        assertEquals(LunchChallenge.Status.PLAYING, pair.guest.lunch().status());
    }

    @Test
    public void bothSidesAgreeOnWinnerAndOnlyWinnerChooses() {
        Pair pair = findStandOff(BlackjackSession.Mode.LUNCH, BlackjackSession.Outcome.WIN);
        assertEquals(BlackjackSession.Outcome.LOSE, pair.guest.outcome());
        assertEquals("alice", pair.host.lunch().winnerId());
        assertEquals("alice", pair.guest.lunch().winnerId());

        assertNull("loser cannot submit", pair.guest.lunchPayload("拉麵"));
        String payload = pair.host.lunchPayload("  牛肉麵 ");
        assertNotNull(payload);
        // 還沒確認送達前，本機不能算已決定
        assertEquals(LunchChallenge.Status.AWAITING_CHOICE, pair.host.lunch().status());

        assertTrue(pair.guest.applyRemote(BlackjackProtocol.parse(payload)));
        assertTrue(pair.host.confirmLunchSent("  牛肉麵 "));
        assertEquals(LunchChallenge.Status.DECIDED, pair.host.lunch().status());
        assertEquals(LunchChallenge.Status.DECIDED, pair.guest.lunch().status());
        assertEquals("牛肉麵", pair.host.lunch().choice());
        assertEquals(pair.host.lunch().choice(), pair.guest.lunch().choice());

        // 重複收到同一則或之後再送都不會改掉結果
        assertFalse(pair.guest.applyRemote(BlackjackProtocol.parse(payload)));
        assertFalse(pair.guest.applyRemote(BlackjackProtocol.parse(
                BlackjackProtocol.lunch(pair.host.roomId(), "便當"))));
        assertEquals("牛肉麵", pair.guest.lunch().choice());
        assertNull(pair.host.lunchPayload("便當"));
    }

    @Test
    public void loserCannotForgeLunchChoice() {
        Pair pair = findStandOff(BlackjackSession.Mode.LUNCH, BlackjackSession.Outcome.WIN);
        String forged = BlackjackProtocol.lunch(pair.host.roomId(), "我說了算");
        assertFalse(pair.host.applyRemote(BlackjackProtocol.parse(forged)));
        assertEquals(LunchChallenge.Status.AWAITING_CHOICE, pair.host.lunch().status());
        assertTrue(pair.host.lunch().canChoose("alice"));
    }

    @Test
    public void drawGivesNobodyTheChoice() {
        Pair pair = findStandOff(BlackjackSession.Mode.LUNCH, BlackjackSession.Outcome.DRAW);
        assertEquals(BlackjackSession.Outcome.DRAW, pair.guest.outcome());
        assertEquals(LunchChallenge.Status.DRAW, pair.host.lunch().status());
        assertEquals(LunchChallenge.Status.DRAW, pair.guest.lunch().status());
        assertNull(pair.host.lunch().winnerId());
        assertNull(pair.host.lunchPayload("壽司"));
        assertNull(pair.guest.lunchPayload("壽司"));
    }

    @Test
    public void normalModeHasNoLunch() {
        Pair pair = findStandOff(BlackjackSession.Mode.NORMAL, BlackjackSession.Outcome.WIN);
        assertNull(pair.host.lunch());
        assertNull(pair.guest.lunch());
        assertNull(pair.host.lunchPayload("牛肉麵"));
        assertFalse(pair.guest.applyRemote(BlackjackProtocol.parse(
                BlackjackProtocol.lunch(pair.guest.roomId(), "牛肉麵"))));
    }

    @Test
    public void oldClientAcceptDowngradesToNormal() {
        BlackjackSession host = BlackjackSession.startInvite("alice", "bob", BlackjackSession.Mode.LUNCH, 7L);
        // 舊版接受時只回 accept|room
        assertTrue(host.applyRemote(BlackjackProtocol.parse(BlackjackProtocol.accept(host.roomId()))));
        assertEquals(BlackjackSession.Phase.PLAYING, host.phase());
        assertFalse(host.isLunch());
        assertTrue(host.downgraded());
        assertNull(host.lunch());
    }

    @Test
    public void unsentChoiceThenLeavingReleasesLoser() {
        Pair pair = findStandOff(BlackjackSession.Mode.LUNCH, BlackjackSession.Outcome.WIN);
        assertNotNull(pair.host.lunchPayload("咖哩"));
        // 送出失敗：不呼叫 confirmLunchSent，接著關窗
        String abort = pair.host.abort();
        assertNotNull("winner leaving must tell the loser", abort);
        assertEquals(LunchChallenge.Status.ABANDONED, pair.host.lunch().status());
        assertTrue(pair.guest.applyRemote(BlackjackProtocol.parse(abort)));
        assertEquals(LunchChallenge.Status.ABANDONED, pair.guest.lunch().status());
        assertEquals(BlackjackSession.Phase.CLOSED, pair.guest.phase());
    }

    @Test
    public void closingAfterDecisionSendsNothing() {
        Pair pair = findStandOff(BlackjackSession.Mode.LUNCH, BlackjackSession.Outcome.LOSE);
        String payload = pair.guest.lunchPayload("火鍋");
        assertTrue(pair.host.applyRemote(BlackjackProtocol.parse(payload)));
        assertTrue(pair.guest.confirmLunchSent("火鍋"));
        assertNull(pair.guest.abort());
        assertNull(pair.host.abort());
        assertEquals("火鍋", pair.host.lunch().choice());
    }

    @Test
    public void rematchUsesFreshRoomAndIgnoresOldMessages() {
        Pair first = findStandOff(BlackjackSession.Mode.LUNCH, BlackjackSession.Outcome.DRAW);
        BlackjackSession next = BlackjackSession.startInvite("alice", "bob", first.host.mode());
        assertNotEquals(first.host.roomId(), next.roomId());
        assertEquals(BlackjackSession.Phase.INVITING, next.phase());
        assertTrue(next.myHand().isEmpty());
        assertEquals(52, next.deckLeft());
        assertTrue(next.isLunch());
        assertEquals(LunchChallenge.Status.PLAYING, next.lunch().status());
        // 上一局遲到的訊息不能影響新的一局
        assertFalse(next.applyRemote(BlackjackProtocol.parse(BlackjackProtocol.accept(first.host.roomId(),
                BlackjackProtocol.MODE_LUNCH))));
        assertFalse(next.applyRemote(BlackjackProtocol.parse(BlackjackProtocol.abort(first.host.roomId()))));
        assertEquals(BlackjackSession.Phase.INVITING, next.phase());
    }
}
