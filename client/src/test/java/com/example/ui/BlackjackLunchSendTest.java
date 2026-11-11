package com.example.ui;

import com.example.BlackjackProtocol;
import com.example.service.BlackjackSession;
import com.example.service.LunchChallenge;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 午餐送出中途與對方離開交錯時，結束畫面不該再要求重送。 */
public class BlackjackLunchSendTest {

    /** [0] 為贏家（可決定午餐），[1] 為輸家。 */
    private static BlackjackSession[] awaitingChoice() {
        for (int attempt = 0; attempt < 5000; attempt++) {
            BlackjackSession host = BlackjackSession.startInvite("alice", "bob", BlackjackSession.Mode.LUNCH);
            BlackjackProtocol.Message invite = BlackjackProtocol.parse(host.invitePayload());
            BlackjackSession guest = BlackjackSession.fromInvite("bob", "alice", invite.roomId, invite.seed,
                    BlackjackSession.modeOf(invite));
            host.applyRemote(BlackjackProtocol.parse(guest.accept()));
            if (host.phase() != BlackjackSession.Phase.PLAYING) {
                continue;
            }
            guest.applyRemote(BlackjackProtocol.parse(host.stand()));
            if (guest.phase() != BlackjackSession.Phase.PLAYING) {
                continue;
            }
            host.applyRemote(BlackjackProtocol.parse(guest.stand()));
            if (host.lunch().status() != LunchChallenge.Status.AWAITING_CHOICE) {
                continue;
            }
            return host.lunch().canChoose("alice")
                    ? new BlackjackSession[]{host, guest}
                    : new BlackjackSession[]{guest, host};
        }
        throw new AssertionError("no game reaches AWAITING_CHOICE");
    }

    @Test
    public void successfulSendDecides() {
        BlackjackSession winner = awaitingChoice()[0];
        assertNotNull(winner.lunchPayload("拉麵"));
        assertEquals(BlackjackWindow.LunchSendResult.DECIDED, BlackjackWindow.settleLunchSend(winner, true, "拉麵"));
        assertEquals(LunchChallenge.Status.DECIDED, winner.lunch().status());
    }

    @Test
    public void peerAbortArrivesBeforeSuccessfulSendCompletes() {
        BlackjackSession[] pair = awaitingChoice();
        BlackjackSession winner = pair[0];
        assertNotNull(winner.lunchPayload("拉麵"));
        // 送出中，輸家離開，abort 先被套用
        assertTrue(winner.applyRemote(BlackjackProtocol.parse(pair[1].abort())));
        assertEquals(BlackjackWindow.LunchSendResult.ENDED, BlackjackWindow.settleLunchSend(winner, true, "拉麵"));
        assertEquals(BlackjackSession.Phase.CLOSED, winner.phase());
        assertEquals(LunchChallenge.Status.ABANDONED, winner.lunch().status());
    }

    @Test
    public void peerAbortArrivesBeforeFailedSendCompletes() {
        BlackjackSession[] pair = awaitingChoice();
        BlackjackSession winner = pair[0];
        assertTrue(winner.applyRemote(BlackjackProtocol.parse(pair[1].abort())));
        assertEquals(BlackjackWindow.LunchSendResult.ENDED, BlackjackWindow.settleLunchSend(winner, false, "拉麵"));
    }

    @Test
    public void localWindowClosedWhileSending() {
        BlackjackSession winner = awaitingChoice()[0];
        assertNotNull(winner.abort());
        assertEquals(BlackjackWindow.LunchSendResult.ENDED, BlackjackWindow.settleLunchSend(winner, true, "拉麵"));
    }

    @Test
    public void realSendFailureStillOffersRetryThenSucceeds() {
        BlackjackSession winner = awaitingChoice()[0];
        assertEquals(BlackjackWindow.LunchSendResult.RETRY, BlackjackWindow.settleLunchSend(winner, false, "拉麵"));
        assertEquals(LunchChallenge.Status.AWAITING_CHOICE, winner.lunch().status());
        assertEquals(BlackjackWindow.LunchSendResult.DECIDED, BlackjackWindow.settleLunchSend(winner, true, "拉麵"));
        assertEquals("拉麵", winner.lunch().choice());
    }

    @Test
    public void peerAbortAfterFailedSendEndsRetry() {
        BlackjackSession[] pair = awaitingChoice();
        BlackjackSession winner = pair[0];
        assertEquals(BlackjackWindow.LunchSendResult.RETRY, BlackjackWindow.settleLunchSend(winner, false, "拉麵"));
        assertTrue(winner.applyRemote(BlackjackProtocol.parse(pair[1].abort())));
        assertEquals(BlackjackWindow.LunchSendResult.ENDED, BlackjackWindow.settleLunchSend(winner, true, "拉麵"));
    }
}
