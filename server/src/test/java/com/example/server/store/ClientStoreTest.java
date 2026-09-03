package com.example.server.store;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClientStoreTest {

    private ClientStore store;

    @Before
    public void setUp() {
        store = new ClientStore();
        store.clients().clear();
    }

    @Test
    public void maskTargetUrl_masksHostAndPath() {
        assertEquals("https://se***.example/***",
                ClientStore.maskTargetUrl("https://secret.example/checkin?token=abc"));
        assertEquals("https://t*.ya***.com/***",
                ClientStore.maskTargetUrl("https://tw.yahoo.com/"));
    }

    @Test
    public void sanitizeKeepsButtonId() {
        Map<String, Object> dirty = new LinkedHashMap<>();
        dirty.put("clientId", "worker-a");
        dirty.put("targetUrl", "https://secret.example/checkin");
        dirty.put("buttonId", "#btn-checkin");
        List<Map<String, Object>> tasks = new ArrayList<>();
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", "t1");
        task.put("targetUrl", "https://secret.example/a");
        task.put("buttonId", "#a");
        tasks.add(task);
        dirty.put("tasks", tasks);

        Map<String, Object> clean = store.sanitizeClientForApi(dirty);
        assertEquals("https://se***.example/***", clean.get("targetUrl"));
        assertEquals("#btn-checkin", clean.get("buttonId"));
        Object firstTask = ((List<?>) clean.get("tasks")).get(0);
        assertTrue(firstTask instanceof Map);
        assertEquals("#a", ((Map<?, ?>) firstTask).get("buttonId"));
    }

    @Test
    public void queueAndDrainPendingActions() {
        store.queueClientAction("unit-test-worker", "CANCEL_SCHEDULE");
        store.queueClientAction("unit-test-worker", "CANCEL_TASK:abc");
        Map<String, Object> existing = store.getOrCreateClient("unit-test-worker");
        List<String> drained = store.drainPendingActions(existing);
        assertEquals(2, drained.size());
        assertEquals("CANCEL_SCHEDULE", drained.get(0));
        assertEquals("CANCEL_TASK:abc", drained.get(1));
        assertTrue(store.drainPendingActions(existing).isEmpty());
    }

    @Test
    public void peerMessageCannotTargetSelf() {
        ClientStore.PeerResult result = store.queuePeerMessage("a", "a", "hello");
        assertFalse(result.ok);
        assertEquals("不能發送訊息給自己", result.message);
    }

    @Test
    public void peerMessageActionIncludesSentTimestamp() {
        long before = System.currentTimeMillis();
        assertTrue(store.queuePeerMessage("b", "a", "hello").ok);
        Map<String, Object> existing = store.getOrCreateClient("b");
        List<String> drained = store.drainPendingActions(existing);
        assertEquals(1, drained.size());
        String action = drained.get(0);
        String[] parts = action.split("\\|", 4);
        assertEquals(4, parts.length);
        assertEquals("MSG", parts[0]);
        assertEquals("a", parts[1]);
        long sentAt = Long.parseLong(parts[3]);
        assertTrue(sentAt >= before);
        assertTrue(sentAt <= System.currentTimeMillis() + 1000);
    }

    @Test
    public void peerPokeActionIncludesSentTimestamp() {
        long before = System.currentTimeMillis();
        assertTrue(store.queuePeerPoke("b", "a").ok);
        Map<String, Object> existing = store.getOrCreateClient("b");
        List<String> drained = store.drainPendingActions(existing);
        assertEquals(1, drained.size());
        String[] parts = drained.get(0).split("\\|", 3);
        assertEquals(3, parts.length);
        assertEquals("POKE", parts[0]);
        assertEquals("a", parts[1]);
        long sentAt = Long.parseLong(parts[2]);
        assertTrue(sentAt >= before);
        assertTrue(sentAt <= System.currentTimeMillis() + 1000);
    }

    @Test
    public void peerSnapshotExcludesSelf() {
        store.getOrCreateClient("a");
        store.getOrCreateClient("b");
        assertEquals(1, store.peerSnapshot("a").size());
        assertEquals("b", store.peerSnapshot("a").get(0).get("clientId"));
    }
}
