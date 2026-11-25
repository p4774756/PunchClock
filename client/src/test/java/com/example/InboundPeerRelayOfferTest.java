package com.example;

import com.example.ui.PeerChatWindow;
import org.junit.Assume;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class InboundPeerRelayOfferTest {

    @Test
    public void openChatWindowReturnsExistingWindowOnly() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Map<String, PeerChatWindow> windows = new HashMap<>();
        PeerChatWindow bob = new PeerChatWindow("bob", silentHost(), null, List.of(), false);
        bob.setVisible(false);
        windows.put("bob", bob);

        assertSame(bob, InboundPeerRelayOffer.openChatWindow(windows, "bob"));
        assertNull(InboundPeerRelayOffer.openChatWindow(windows, "alice"));
        assertNull(InboundPeerRelayOffer.openChatWindow(windows, null));
        assertNull(InboundPeerRelayOffer.openChatWindow(windows, "  "));
        assertNull(InboundPeerRelayOffer.openChatWindow(null, "bob"));
    }

    private static PeerChatWindow.Host silentHost() {
        return new PeerChatWindow.Host() {
            private final AtomicInteger sends = new AtomicInteger();

            @Override
            public boolean canSend(String peerId) {
                return true;
            }

            @Override
            public void sendMessage(String peerId, String text, String imageEncoded, java.util.function.Consumer<Boolean> done) {
                sends.incrementAndGet();
                done.accept(true);
            }

            @Override
            public boolean sendQueuedFiles(String peerId, java.awt.Component owner, List<java.nio.file.Path> files) {
                return false;
            }

            @Override
            public void chatClosed(PeerChatWindow window) {
            }
        };
    }
}