package com.example.service;

import com.example.BlackjackProtocol;
import com.example.BlackjackRules;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BlackjackSessionTest {

    @Test
    public void inviteAcceptDealsSameHands() {
        BlackjackSession host = BlackjackSession.startInvite("alice", "bob");
        BlackjackProtocol.Message invite = BlackjackProtocol.parse(host.invitePayload());
        assertNotNull(invite);

        BlackjackSession guest = BlackjackSession.fromInvite("bob", "alice", invite.roomId, invite.seed);
        String accept = guest.accept();
        assertNotNull(accept);
        assertTrue(host.applyRemote(BlackjackProtocol.parse(accept)));

        assertEquals(BlackjackSession.Phase.PLAYING, host.phase());
        assertEquals(BlackjackSession.Phase.PLAYING, guest.phase());
        assertEquals(host.myHand(), guest.peerHand());
        assertEquals(host.peerHand(), guest.myHand());
        assertTrue(host.myTurn());
        assertFalse(guest.myTurn());
    }

    @Test
    public void hitAndStandSettleTogether() {
        BlackjackSession host = BlackjackSession.startInvite("alice", "bob");
        BlackjackProtocol.Message invite = BlackjackProtocol.parse(host.invitePayload());
        BlackjackSession guest = BlackjackSession.fromInvite("bob", "alice", invite.roomId, invite.seed);
        guest.accept();
        host.applyRemote(BlackjackProtocol.parse(BlackjackProtocol.accept(invite.roomId)));

        String stand = host.stand();
        assertNotNull(stand);
        assertTrue(guest.applyRemote(BlackjackProtocol.parse(stand)));
        assertTrue(guest.myTurn());

        String guestStand = guest.stand();
        assertNotNull(guestStand);
        assertTrue(host.applyRemote(BlackjackProtocol.parse(guestStand)));

        assertEquals(BlackjackSession.Phase.SETTLED, host.phase());
        assertEquals(BlackjackSession.Phase.SETTLED, guest.phase());
        if (host.outcome() == BlackjackSession.Outcome.DRAW) {
            assertEquals(BlackjackSession.Outcome.DRAW, guest.outcome());
        } else if (host.outcome() == BlackjackSession.Outcome.WIN) {
            assertEquals(BlackjackSession.Outcome.LOSE, guest.outcome());
        } else {
            assertEquals(BlackjackSession.Outcome.WIN, guest.outcome());
        }
        assertEquals(BlackjackRules.compare(host.myHand(), host.peerHand()) > 0,
                host.outcome() == BlackjackSession.Outcome.WIN);
    }

    @Test
    public void hitIndexMustMatch() {
        BlackjackSession host = BlackjackSession.startInvite("alice", "bob");
        BlackjackProtocol.Message invite = BlackjackProtocol.parse(host.invitePayload());
        BlackjackSession guest = BlackjackSession.fromInvite("bob", "alice", invite.roomId, invite.seed);
        guest.accept();
        host.applyRemote(BlackjackProtocol.parse(BlackjackProtocol.accept(invite.roomId)));

        // 開局後 nextIndex=4；送錯 index 應中止
        assertTrue(guest.applyRemote(BlackjackProtocol.parse(BlackjackProtocol.hit(invite.roomId, 7))));
        assertEquals(BlackjackSession.Phase.CLOSED, guest.phase());
    }

    @Test
    public void duplicateHitIsIgnoredNotDesync() {
        for (long seed = 0; seed < 500; seed++) {
            BlackjackSession host = BlackjackSession.startInvite("alice", "bob", BlackjackSession.Mode.NORMAL, seed);
            BlackjackProtocol.Message invite = BlackjackProtocol.parse(host.invitePayload());
            BlackjackSession guest = BlackjackSession.fromInvite("bob", "alice", invite.roomId, invite.seed);
            host.applyRemote(BlackjackProtocol.parse(guest.accept()));
            String hit = host.hit();
            if (host.phase() != BlackjackSession.Phase.PLAYING) {
                continue; // 這副牌第一張就爆，換一副
            }
            assertTrue(guest.applyRemote(BlackjackProtocol.parse(hit)));
            assertFalse("resent hit must be ignored", guest.applyRemote(BlackjackProtocol.parse(hit)));
            assertEquals(BlackjackSession.Phase.PLAYING, guest.phase());
            assertEquals(host.myHand(), guest.peerHand());
            assertEquals(host.deckLeft(), guest.deckLeft());
            return;
        }
        throw new AssertionError("no seed without first-hit bust");
    }

    @Test
    public void normalInviteKeepsLegacyFormat() {
        BlackjackSession host = BlackjackSession.startInvite("alice", "bob");
        String payload = host.invitePayload();
        assertEquals(6, payload.split("\\|", -1).length);
        assertFalse(host.isLunch());
        BlackjackProtocol.Message invite = BlackjackProtocol.parse(payload);
        BlackjackSession guest = BlackjackSession.fromInvite("bob", "alice", invite.roomId, invite.seed);
        assertEquals(BlackjackProtocol.accept(invite.roomId), guest.accept());
    }

    @Test
    public void messagesForOtherRoomsAreIgnored() {
        BlackjackSession host = BlackjackSession.startInvite("alice", "bob");
        assertFalse(host.applyRemote(BlackjackProtocol.parse(BlackjackProtocol.accept("otherroom"))));
        assertFalse(host.applyRemote(BlackjackProtocol.parse(BlackjackProtocol.abort("otherroom"))));
        assertEquals(BlackjackSession.Phase.INVITING, host.phase());
    }
}
