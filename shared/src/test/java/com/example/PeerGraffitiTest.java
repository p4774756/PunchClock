package com.example;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PeerGraffitiTest {

    @Test
    public void tagsRoundTrip() {
        assertEquals(PeerGraffiti.Mode.CARD, PeerGraffiti.modeOf(PeerGraffiti.messageText(PeerGraffiti.Mode.CARD)));
        assertEquals(PeerGraffiti.Mode.DIRECT, PeerGraffiti.modeOf(PeerGraffiti.messageText(PeerGraffiti.Mode.DIRECT)));
        assertNull(PeerGraffiti.modeOf("一般訊息"));
        assertNull(PeerGraffiti.modeOf(null));
    }

    @Test
    public void tagOnlyMessagesSkipBodyText() {
        assertTrue(PeerGraffiti.isTagOnly(PeerGraffiti.TAG_CARD));
        assertTrue(PeerGraffiti.isTagOnly("  " + PeerGraffiti.TAG_DIRECT + "  "));
        assertFalse(PeerGraffiti.isTagOnly(PeerGraffiti.TAG_CARD + "\n附註"));
    }
}