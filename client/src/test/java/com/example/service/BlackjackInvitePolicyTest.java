package com.example.service;

import com.example.BlackjackProtocol;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BlackjackInvitePolicyTest {

    private static BlackjackProtocol.Message parse(String payload) {
        BlackjackProtocol.Message message = BlackjackProtocol.parse(payload);
        assertNotNull(payload, message);
        return message;
    }

    /** bob 收到 alice 的邀請並接受，雙方開局。 */
    private static BlackjackSession[] playing(BlackjackSession.Mode mode, long seed) {
        BlackjackSession alice = BlackjackSession.startInvite("alice", "bob", mode, seed);
        BlackjackProtocol.Message invite = parse(alice.invitePayload());
        BlackjackSession bob = BlackjackSession.fromInvite("bob", "alice", invite.roomId, invite.seed,
                BlackjackSession.modeOf(invite));
        assertTrue(alice.applyRemote(parse(bob.accept())));
        return new BlackjackSession[]{alice, bob};
    }

    private static BlackjackSession[] lunchAwaitingChoice() {
        for (long seed = 0; seed < 5000; seed++) {
            BlackjackSession[] pair = playing(BlackjackSession.Mode.LUNCH, seed);
            if (pair[0].phase() != BlackjackSession.Phase.PLAYING) {
                continue;
            }
            assertTrue(pair[1].applyRemote(parse(pair[0].stand())));
            if (pair[1].phase() != BlackjackSession.Phase.PLAYING) {
                continue;
            }
            assertTrue(pair[0].applyRemote(parse(pair[1].stand())));
            if (pair[0].lunch().status() == LunchChallenge.Status.AWAITING_CHOICE) {
                return pair;
            }
        }
        throw new AssertionError("no seed reaches AWAITING_CHOICE");
    }

    private static String roomOf(String payload) {
        return parse(payload).roomId;
    }

    @Test
    public void noExistingGameOpensInvite() {
        assertEquals(BlackjackInvitePolicy.Decision.OPEN, BlackjackInvitePolicy.onInvite(null, "r1"));
    }

    @Test
    public void sameRoomIsDuplicate() {
        BlackjackSession bob = BlackjackSession.fromInvite("bob", "alice", "room-a", 7L, BlackjackSession.Mode.NORMAL);
        assertEquals(BlackjackInvitePolicy.Decision.DUPLICATE, BlackjackInvitePolicy.onInvite(bob, "room-a"));
    }

    @Test
    public void finishedGameIsReplacedByNewInvite() {
        BlackjackSession[] pair = playing(BlackjackSession.Mode.NORMAL, 3L);
        pair[1].abort();
        assertEquals(BlackjackInvitePolicy.Decision.OPEN, BlackjackInvitePolicy.onInvite(pair[1], "other-room"));
    }

    @Test
    public void busyWhilePlayingRepliesDeclineWithInviteRoomAndKeepsGame() {
        BlackjackSession[] pair = playing(BlackjackSession.Mode.NORMAL, 11L);
        BlackjackSession bob = pair[1];
        BlackjackSession.Phase before = bob.phase();
        String bobRoom = bob.roomId();

        // alice 那邊另開了一場（例如另一台電腦或舊視窗）又來邀請
        BlackjackSession aliceAgain = BlackjackSession.startInvite("alice", "bob", BlackjackSession.Mode.NORMAL, 99L);
        String newRoom = roomOf(aliceAgain.invitePayload());
        assertEquals(BlackjackInvitePolicy.Decision.DECLINE_BUSY, BlackjackInvitePolicy.onInvite(bob, newRoom));

        String decline = BlackjackProtocol.decline(newRoom);
        assertEquals(newRoom, roomOf(decline));
        // 婉拒送回邀請方：那一場關閉，不再卡在等待接受
        assertTrue(aliceAgain.applyRemote(parse(decline)));
        assertEquals(BlackjackSession.Phase.CLOSED, aliceAgain.phase());
        // 進行中的那場完全不受影響（婉拒帶的是新邀請的 roomId）
        assertFalse(pair[0].applyRemote(parse(decline)));
        assertEquals(before, bob.phase());
        assertEquals(bobRoom, bob.roomId());
        assertEquals(BlackjackSession.Phase.PLAYING, pair[0].phase());
    }

    @Test
    public void busyWhileInvitedRepliesDecline() {
        BlackjackSession bob = BlackjackSession.fromInvite("bob", "alice", "room-a", 7L, BlackjackSession.Mode.NORMAL);
        assertEquals(BlackjackInvitePolicy.Decision.DECLINE_BUSY, BlackjackInvitePolicy.onInvite(bob, "room-b"));
        assertEquals(BlackjackSession.Phase.INVITED, bob.phase());
    }

    @Test
    public void busyWhileLunchAwaitingChoiceRepliesDeclineAndKeepsLunch() {
        BlackjackSession[] pair = lunchAwaitingChoice();
        for (BlackjackSession side : pair) {
            assertEquals(BlackjackInvitePolicy.Decision.DECLINE_BUSY,
                    BlackjackInvitePolicy.onInvite(side, "zzz-new-room"));
            assertEquals(LunchChallenge.Status.AWAITING_CHOICE, side.lunch().status());
            assertEquals(BlackjackSession.Phase.SETTLED, side.phase());
        }
    }

    @Test
    public void settledNormalGameIsNotBusy() {
        BlackjackSession[] pair = playing(BlackjackSession.Mode.NORMAL, 5L);
        if (pair[0].phase() == BlackjackSession.Phase.PLAYING) {
            assertTrue(pair[1].applyRemote(parse(pair[0].stand())));
        }
        if (pair[1].phase() == BlackjackSession.Phase.PLAYING) {
            assertTrue(pair[0].applyRemote(parse(pair[1].stand())));
        }
        assertEquals(BlackjackSession.Phase.SETTLED, pair[0].phase());
        assertFalse(BlackjackInvitePolicy.isBusy(pair[0]));
        assertEquals(BlackjackInvitePolicy.Decision.OPEN, BlackjackInvitePolicy.onInvite(pair[0], "rematch-room"));
    }

    @Test
    public void crossedInvitesPickTheSmallerRoomOnBothSidesWithoutDecline() {
        for (int i = 0; i < 50; i++) {
            BlackjackSession alice = BlackjackSession.startInvite("alice", "bob", BlackjackSession.Mode.LUNCH, i);
            BlackjackSession bob = BlackjackSession.startInvite("bob", "alice", BlackjackSession.Mode.LUNCH, i + 1000L);
            String aliceRoom = alice.roomId();
            String bobRoom = bob.roomId();
            if (aliceRoom.equals(bobRoom)) {
                continue;
            }
            BlackjackInvitePolicy.Decision atAlice = BlackjackInvitePolicy.onInvite(alice, bobRoom);
            BlackjackInvitePolicy.Decision atBob = BlackjackInvitePolicy.onInvite(bob, aliceRoom);
            String winner = aliceRoom.compareTo(bobRoom) < 0 ? aliceRoom : bobRoom;
            if (winner.equals(aliceRoom)) {
                assertEquals(BlackjackInvitePolicy.Decision.KEEP_LOCAL, atAlice);
                assertEquals(BlackjackInvitePolicy.Decision.SWITCH_TO_PEER, atBob);
            } else {
                assertEquals(BlackjackInvitePolicy.Decision.SWITCH_TO_PEER, atAlice);
                assertEquals(BlackjackInvitePolicy.Decision.KEEP_LOCAL, atBob);
            }
            // 交錯邀請不回婉拒：兩邊都不是 DECLINE_BUSY
            assertFalse(atAlice == BlackjackInvitePolicy.Decision.DECLINE_BUSY
                    || atBob == BlackjackInvitePolicy.Decision.DECLINE_BUSY);

            // 改用對方那場的一方接受後，雙方在同一個房間開局
            BlackjackSession keeper = winner.equals(aliceRoom) ? alice : bob;
            BlackjackSession switcher = keeper == alice ? bob : alice;
            BlackjackProtocol.Message invite = parse(keeper.invitePayload());
            BlackjackSession joined = BlackjackSession.fromInvite(switcher.localId(), switcher.peerId(),
                    invite.roomId, invite.seed, BlackjackSession.modeOf(invite));
            assertTrue(keeper.applyRemote(parse(joined.accept())));
            assertEquals(joined.roomId(), keeper.roomId());
            assertTrue(keeper.phase() == BlackjackSession.Phase.PLAYING
                    || keeper.phase() == BlackjackSession.Phase.SETTLED);
        }
    }
}
