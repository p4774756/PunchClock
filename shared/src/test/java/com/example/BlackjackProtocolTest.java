package com.example;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BlackjackProtocolTest {

    @Test
    public void roundTripsInviteAndHit() {
        String invite = BlackjackProtocol.invite("room1", 99L);
        assertTrue(BlackjackProtocol.isMessage(invite));
        BlackjackProtocol.Message parsed = BlackjackProtocol.parse(invite);
        assertNotNull(parsed);
        assertEquals(BlackjackProtocol.KIND_INVITE, parsed.kind);
        assertEquals("room1", parsed.roomId);
        assertEquals(Long.valueOf(99L), parsed.seed);

        String hit = BlackjackProtocol.hit("room1", 4);
        BlackjackProtocol.Message hitMsg = BlackjackProtocol.parse(hit);
        assertNotNull(hitMsg);
        assertEquals(BlackjackProtocol.KIND_HIT, hitMsg.kind);
        assertEquals(Integer.valueOf(4), hitMsg.deckIndex);
    }

    @Test
    public void rejectsBrokenPayloads() {
        assertFalse(BlackjackProtocol.isMessage("hello"));
        assertNull(BlackjackProtocol.parse(BlackjackProtocol.PREFIX + "invite|bad|room|x"));
        assertNull(BlackjackProtocol.parse("GAME|bj|v1|invite|room|not-a-number"));
        assertNull(BlackjackProtocol.parse("GAME|bj|v1|hit|room|-1"));
    }
}
