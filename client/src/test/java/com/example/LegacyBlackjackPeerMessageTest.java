package com.example;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LegacyBlackjackPeerMessageTest {

    @Test
    public void legacyPrefixIsSilentlyIgnored() {
        assertTrue(App.isLegacyBlackjackPeerMessage("GAME|bj|v1|invite|room1|99"));
        assertTrue(App.isLegacyBlackjackPeerMessage("GAME|bj|v1|accept|room1"));
        assertFalse(App.isLegacyBlackjackPeerMessage("GAME|bj|v2|invite|room1|99"));
        assertFalse(App.isLegacyBlackjackPeerMessage("午安"));
        assertFalse(App.isLegacyBlackjackPeerMessage(null));
    }
}