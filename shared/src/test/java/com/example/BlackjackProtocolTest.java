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
    public void normalModeMatchesLegacyStrings() {
        assertEquals(BlackjackProtocol.invite("room1", 5L),
                BlackjackProtocol.invite("room1", 5L, BlackjackProtocol.MODE_NORMAL));
        assertEquals(BlackjackProtocol.accept("room1"),
                BlackjackProtocol.accept("room1", BlackjackProtocol.MODE_NORMAL));
        assertEquals(BlackjackProtocol.MODE_NORMAL, BlackjackProtocol.parse(BlackjackProtocol.invite("room1", 5L)).mode);
    }

    @Test
    public void lunchInviteStaysReadableByOldParser() {
        String lunch = BlackjackProtocol.invite("room1", 5L, BlackjackProtocol.MODE_LUNCH);
        // 舊版只讀前 6 欄：前面必須跟普通邀請一模一樣
        assertTrue(lunch.startsWith(BlackjackProtocol.invite("room1", 5L) + "|"));
        BlackjackProtocol.Message parsed = BlackjackProtocol.parse(lunch);
        assertTrue(parsed.isLunchMode());
        assertEquals(Long.valueOf(5L), parsed.seed);

        BlackjackProtocol.Message accept = BlackjackProtocol.parse(
                BlackjackProtocol.accept("room1", BlackjackProtocol.MODE_LUNCH));
        assertTrue(accept.isLunchMode());
        assertFalse(BlackjackProtocol.parse(BlackjackProtocol.accept("room1")).isLunchMode());
        // 不認得的模式當普通對戰
        assertFalse(BlackjackProtocol.parse("GAME|bj|v1|invite|room1|5|trio").isLunchMode());
    }

    @Test
    public void lunchChoiceRoundTripsSafely() {
        String payload = BlackjackProtocol.lunch("room1", "牛肉麵|加辣 🌶️");
        assertEquals(6, payload.split("\\|", -1).length);
        BlackjackProtocol.Message parsed = BlackjackProtocol.parse(payload);
        assertNotNull(parsed);
        assertEquals(BlackjackProtocol.KIND_LUNCH, parsed.kind);
        assertEquals("牛肉麵|加辣 🌶️", parsed.choice);
    }

    @Test
    public void lunchChoiceIsCleaned() {
        assertEquals("雞腿 便當", BlackjackProtocol.cleanLunchChoice("  雞腿\n\t 便當\u0000 "));
        assertEquals("", BlackjackProtocol.cleanLunchChoice(" \u3000 "));
        String longName = "麵".repeat(80);
        assertEquals(BlackjackProtocol.MAX_LUNCH_CHARS,
                BlackjackProtocol.cleanLunchChoice(longName).codePointCount(0, BlackjackProtocol.MAX_LUNCH_CHARS));
        assertEquals(BlackjackProtocol.MAX_LUNCH_CHARS, BlackjackProtocol.cleanLunchChoice(longName).length());
    }

    @Test
    public void rejectsBrokenLunchPayloads() {
        assertNull(BlackjackProtocol.parse("GAME|bj|v1|lunch|room1"));
        assertNull(BlackjackProtocol.parse("GAME|bj|v1|lunch|room1|"));
        assertNull(BlackjackProtocol.parse("GAME|bj|v1|lunch|room1|not base64!"));
        // 解出來只有空白
        assertNull(BlackjackProtocol.parse("GAME|bj|v1|lunch|room1|ICAg"));
        assertNull(BlackjackProtocol.parse("GAME|bj|v1|lunch|room1|" + "A".repeat(1000)));
        assertNull(BlackjackProtocol.parse("GAME|bj|v1|whatever|room1"));
    }

    @Test
    public void rejectsBrokenPayloads() {
        assertFalse(BlackjackProtocol.isMessage("hello"));
        assertNull(BlackjackProtocol.parse(BlackjackProtocol.PREFIX + "invite|bad|room|x"));
        assertNull(BlackjackProtocol.parse("GAME|bj|v1|invite|room|not-a-number"));
        assertNull(BlackjackProtocol.parse("GAME|bj|v1|hit|room|-1"));
    }
}
