package com.example;

import com.example.service.PeerChatStore;
import com.example.ui.PeerChatWindow;
import org.junit.Assume;
import org.junit.Test;

import java.awt.GraphicsEnvironment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class InboundPeerDeliveryTest {

    @Test
    public void deliverAppendsOnlyToExistingWindow() throws Exception {
        Assume.assumeFalse(GraphicsEnvironment.isHeadless());
        Map<String, PeerChatWindow> windows = new HashMap<>();
        PeerChatStore store = new PeerChatStore();
        PeerChatWindow bob = new PeerChatWindow("bob", silentHost(), null, List.of(), false);
        bob.setVisible(false);
        windows.put("bob", bob);
        InboundPeerDelivery.deliver(windows, store, "bob", "嗨", "", 1L);
        assertEquals(1, store.entries("bob").size());
        assertFalse(bob.isVisible());

        InboundPeerDelivery.deliver(windows, store, "alice", "午安", "", 2L);
        assertEquals(1, store.entries("alice").size());
        assertEquals(1, windows.size());
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