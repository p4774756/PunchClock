package com.example.ui.blackjack;

import com.example.BlackjackProtocol;
import com.example.service.BlackjackSession;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class BlackjackSceneTest {

    private static BlackjackSession[] start(BlackjackSession.Mode mode) {
        BlackjackSession host = BlackjackSession.startInvite("alice", "bob", mode);
        BlackjackProtocol.Message invite = BlackjackProtocol.parse(host.invitePayload());
        BlackjackSession guest = BlackjackSession.fromInvite("bob", "alice", invite.roomId, invite.seed,
                BlackjackSession.modeOf(invite));
        assertTrue(host.applyRemote(BlackjackProtocol.parse(guest.accept())));
        return new BlackjackSession[]{host, guest};
    }

    /** 雙方開局就停牌，直到房主拿到指定結果。 */
    private static BlackjackSession[] standOff(BlackjackSession.Mode mode, BlackjackSession.Outcome hostOutcome) {
        for (int i = 0; i < 5000; i++) {
            BlackjackSession[] pair = start(mode);
            pair[1].applyRemote(BlackjackProtocol.parse(pair[0].stand()));
            pair[0].applyRemote(BlackjackProtocol.parse(pair[1].stand()));
            if (pair[0].outcome() == hostOutcome) {
                return pair;
            }
        }
        throw new AssertionError("no deal gives " + hostOutcome);
    }

    @Test
    public void inviteStatesUseWaitingRoom() {
        BlackjackSession host = BlackjackSession.startInvite("alice", "bob", BlackjackSession.Mode.LUNCH);
        BlackjackProtocol.Message invite = BlackjackProtocol.parse(host.invitePayload());
        BlackjackSession guest = BlackjackSession.fromInvite("bob", "alice", invite.roomId, invite.seed,
                BlackjackSession.modeOf(invite));
        assertEquals(BlackjackScene.WAITING, BlackjackScene.of(host));
        assertEquals(BlackjackScene.INVITED, BlackjackScene.of(guest));
        assertTrue(BlackjackScene.of(host).showsWaitingRoom());
        assertTrue(BlackjackScene.of(guest).showsWaitingRoom());
    }

    @Test
    public void cancelledInviteIsClosedWithoutTable() {
        BlackjackSession host = BlackjackSession.startInvite("alice", "bob", BlackjackSession.Mode.NORMAL);
        BlackjackProtocol.Message invite = BlackjackProtocol.parse(host.invitePayload());
        BlackjackSession guest = BlackjackSession.fromInvite("bob", "alice", invite.roomId, invite.seed,
                BlackjackSession.modeOf(invite));
        String abort = host.abort();
        assertEquals(BlackjackScene.CLOSED, BlackjackScene.of(host));
        assertTrue(guest.applyRemote(BlackjackProtocol.parse(abort)));
        assertEquals(BlackjackScene.CLOSED, BlackjackScene.of(guest));
        assertFalse(guest.canAccept());
    }

    @Test
    public void acceptedGameIsPlayingOnBothSides() {
        BlackjackSession[] pair = start(BlackjackSession.Mode.NORMAL);
        assertEquals(BlackjackScene.PLAYING, BlackjackScene.of(pair[0]));
        assertEquals(BlackjackScene.PLAYING, BlackjackScene.of(pair[1]));
        assertFalse(BlackjackScene.PLAYING.showsWaitingRoom());
    }

    @Test
    public void onlyLunchWinnerGetsToChoose() {
        BlackjackSession[] pair = standOff(BlackjackSession.Mode.LUNCH, BlackjackSession.Outcome.WIN);
        assertEquals(BlackjackScene.LUNCH_CHOOSE, BlackjackScene.of(pair[0]));
        assertEquals(BlackjackScene.LUNCH_WAIT, BlackjackScene.of(pair[1]));

        String lunch = pair[0].lunchPayload("咖哩飯");
        assertTrue(pair[1].applyRemote(BlackjackProtocol.parse(lunch)));
        assertTrue(pair[0].confirmLunchSent("咖哩飯"));
        assertEquals(BlackjackScene.LUNCH_DONE, BlackjackScene.of(pair[0]));
        assertEquals(BlackjackScene.LUNCH_DONE, BlackjackScene.of(pair[1]));
    }

    @Test
    public void lunchDrawAndNormalGamesHaveNoLunchChoice() {
        BlackjackSession[] draw = standOff(BlackjackSession.Mode.LUNCH, BlackjackSession.Outcome.DRAW);
        assertEquals(BlackjackScene.RESULT, BlackjackScene.of(draw[0]));
        assertEquals(BlackjackScene.RESULT, BlackjackScene.of(draw[1]));

        BlackjackSession[] normal = standOff(BlackjackSession.Mode.NORMAL, BlackjackSession.Outcome.WIN);
        assertEquals(BlackjackScene.RESULT, BlackjackScene.of(normal[0]));
        assertEquals(BlackjackScene.RESULT, BlackjackScene.of(normal[1]));
    }

    @Test
    public void loserAndDrawNeverSeeVictoryTitle() {
        BlackjackSession[] pair = standOff(BlackjackSession.Mode.NORMAL, BlackjackSession.Outcome.WIN);
        assertEquals("YOU WIN!", BlackjackScene.resultTitle(pair[0].outcome()));
        assertNotEquals("YOU WIN!", BlackjackScene.resultTitle(pair[1].outcome()));
        assertNotEquals("YOU WIN!", BlackjackScene.resultTitle(BlackjackSession.Outcome.DRAW));
        assertEquals("", BlackjackScene.resultTitle(BlackjackSession.Outcome.NONE));
    }

    @Test
    public void verdictFollowsOutcomeOnBothSides() {
        BlackjackSession[] win = standOff(BlackjackSession.Mode.NORMAL, BlackjackSession.Outcome.WIN);
        assertEquals(BlackjackScene.Verdict.WIN, BlackjackScene.verdict(win[0]));
        assertEquals(BlackjackScene.Verdict.LOSE, BlackjackScene.verdict(win[1]));

        BlackjackSession[] draw = standOff(BlackjackSession.Mode.NORMAL, BlackjackSession.Outcome.DRAW);
        assertEquals(BlackjackScene.Verdict.DRAW, BlackjackScene.verdict(draw[0]));
        assertEquals(BlackjackScene.Verdict.DRAW, BlackjackScene.verdict(draw[1]));

        assertEquals(BlackjackScene.Verdict.NONE, BlackjackScene.verdict(start(BlackjackSession.Mode.NORMAL)[0]));
    }

    @Test
    public void bustIsItsOwnVerdictAndPeerSeesWin() {
        BlackjackSession[] pair = start(BlackjackSession.Mode.NORMAL);
        for (int i = 0; i < 20 && pair[0].phase() == BlackjackSession.Phase.PLAYING; i++) {
            if (pair[0].canHit()) {
                pair[1].applyRemote(BlackjackProtocol.parse(pair[0].hit()));
            } else if (pair[1].canStand()) {
                pair[0].applyRemote(BlackjackProtocol.parse(pair[1].stand()));
            } else {
                break;
            }
        }
        assertTrue(pair[0].myScore() > 21);
        assertEquals(BlackjackScene.Verdict.BUST, BlackjackScene.verdict(pair[0]));
        assertEquals(BlackjackScene.Verdict.WIN, BlackjackScene.verdict(pair[1]));
    }
}
