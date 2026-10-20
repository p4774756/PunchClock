package com.example.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PeerChatStoreTest {

    @Test
    public void keepsEntriesPerPeerInOrder() {
        PeerChatStore store = new PeerChatStore();
        store.addMessage("alice", false, "hi", "", 1_000L);
        store.addMessage("alice", true, "hello", "", 2_000L);
        store.addMessage("bob", false, "other", "", 3_000L);

        List<PeerChatStore.Entry> alice = store.entries("alice");
        assertEquals(2, alice.size());
        assertEquals("hi", alice.get(0).text);
        assertFalse(alice.get(0).outgoing);
        assertEquals("hello", alice.get(1).text);
        assertTrue(alice.get(1).outgoing);
        assertEquals(1, store.entries("bob").size());
        assertTrue(store.entries("nobody").isEmpty());
    }

    @Test
    public void dropsOldestBeyondLimit() {
        PeerChatStore store = new PeerChatStore();
        for (int i = 0; i < PeerChatStore.MAX_ENTRIES + 5; i++) {
            store.addMessage("alice", false, "m" + i, "", (long) (i + 1));
        }
        List<PeerChatStore.Entry> entries = store.entries("alice");
        assertEquals(PeerChatStore.MAX_ENTRIES, entries.size());
        assertEquals("m5", entries.get(0).text);
        assertEquals("m" + (PeerChatStore.MAX_ENTRIES + 4), entries.get(entries.size() - 1).text);
    }

    @Test
    public void recordsKindsImagesAndFallbackTime() {
        PeerChatStore store = new PeerChatStore();
        long before = System.currentTimeMillis();
        PeerChatStore.Entry image = store.addMessage("alice", true, "", "abc", null);
        PeerChatStore.Entry poke = store.addPoke("alice", false, 5_000L);
        PeerChatStore.Entry note = store.addNote("alice", "已直傳「a.txt」", 6_000L);

        assertTrue(image.hasImage());
        assertTrue("沒有時間就用現在", image.timeMs >= before);
        assertEquals(PeerChatStore.Kind.POKE, poke.kind);
        assertEquals(5_000L, poke.timeMs);
        assertEquals(PeerChatStore.Kind.NOTE, note.kind);
        assertEquals("已直傳「a.txt」", note.text);
        assertFalse(poke.hasImage());
    }

    @Test
    public void returnsCopyAndSupportsClear() {
        PeerChatStore store = new PeerChatStore();
        store.addMessage("alice", false, "hi", "", 1L);
        List<PeerChatStore.Entry> snapshot = store.entries("alice");
        store.addMessage("alice", false, "later", "", 2L);
        assertEquals("先拿到的快照不會被後來的訊息改動", 1, snapshot.size());

        store.clear("alice");
        assertTrue(store.entries("alice").isEmpty());

        store.addMessage("bob", false, "x", "", 3L);
        store.clearAll();
        assertTrue(store.entries("bob").isEmpty());
    }
}
