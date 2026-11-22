package com.example;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuietlyIgnoredPeerTextTest {

    @Test
    public void legacyPrefixIsSilentlyIgnored() {
        assertTrue(App.isQuietlyIgnoredPeerText("GAME|bj|v1|invite|room1|99"));
        assertTrue(App.isQuietlyIgnoredPeerText("GAME|bj|v1|accept|room1"));
        assertFalse(App.isQuietlyIgnoredPeerText("GAME|bj|v2|invite|room1|99"));
        assertFalse(App.isQuietlyIgnoredPeerText("午安"));
        assertFalse(App.isQuietlyIgnoredPeerText(null));
    }
}
