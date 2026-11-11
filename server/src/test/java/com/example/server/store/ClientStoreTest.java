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
        assertEquals("未命名任務", ((Map<?, ?>) firstTask).get("name"));
    }

    @Test
    public void displayTaskName_keepsCustomNameAndTrims() {
        assertEquals("早班", ClientStore.displayTaskName("  早班  "));
        assertEquals("未命名任務", ClientStore.displayTaskName("   "));
        assertEquals("123456789012345678901234", ClientStore.displayTaskName("12345678901234567890123456789"));
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
    public void peerMessageActionIncludesAvatarWhenProvided() {
        assertTrue(store.queuePeerMessage("b", "a", "hello", "abcXYZ012-_").ok);
        List<String> drained = store.drainPendingActions(store.getOrCreateClient("b"));
        assertEquals(1, drained.size());
        String[] parts = drained.get(0).split("\\|", 5);
        assertEquals("MSG", parts[0]);
        assertEquals("a", parts[1]);
        assertEquals("abcXYZ012-_", parts[4]);
        assertEquals("abcXYZ012-_", store.getOrCreateClient("a").get("avatar"));
    }

    @Test
    public void peerMessageWithImageUsesMsgiAction() {
        assertTrue(store.queuePeerMessage("b", "a", "看圖", "avatarX", "imgABC-_").ok);
        List<String> drained = store.drainPendingActions(store.getOrCreateClient("b"));
        assertEquals(1, drained.size());
        String[] parts = drained.get(0).split("\\|", 6);
        assertEquals(6, parts.length);
        assertEquals("MSGI", parts[0]);
        assertEquals("a", parts[1]);
        assertEquals("看圖", new String(
                java.util.Base64.getUrlDecoder().decode(parts[2]),
                java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("avatarX", parts[4]);
        assertEquals("imgABC-_", parts[5]);
    }

    @Test
    public void peerMessageAllowsImageWithoutText() {
        assertTrue(store.queuePeerMessage("b", "a", "", null, "imgABC").ok);
        List<String> drained = store.drainPendingActions(store.getOrCreateClient("b"));
        String[] parts = drained.get(0).split("\\|", 6);
        assertEquals("MSGI", parts[0]);
        assertEquals("", parts[2]);
        assertEquals("", parts[4]);
        assertEquals("imgABC", parts[5]);
    }

    @Test
    public void peerMessageAcceptsSeveralImagesInOneField() {
        assertTrue(store.queuePeerMessage("b", "a", "兩張", null, "imgA,imgB").ok);
        String[] parts = store.drainPendingActions(store.getOrCreateClient("b")).get(0).split("\\|", 6);
        assertEquals("imgA,imgB", parts[5]);
        assertFalse(store.queuePeerMessage("b", "a", "hi", null, "imgA,,imgB").ok);
        assertFalse(store.queuePeerMessage("b", "a", "hi", null, "A,B,C,D,E,F,G").ok);
    }

    @Test
    public void peerMessageRejectsBadOrOversizedImage() {
        assertFalse(store.queuePeerMessage("b", "a", "hi", null, "has/slash").ok);
        String tooBig = "A".repeat(ClientStore.PEER_IMAGE_MAX_CHARS + 1);
        assertFalse(store.queuePeerMessage("b", "a", "hi", null, tooBig).ok);
        assertFalse(store.queuePeerMessage("b", "a", "", null, null).ok);
    }

    @Test
    public void sanitizeClientForApiStripsPendingActions() {
        store.queuePeerMessage("b", "a", "hi", null, "imgABC");
        Map<String, Object> clean = store.sanitizeClientForApi(store.getOrCreateClient("b"));
        assertFalse(clean.containsKey("pendingActions"));
    }

    @Test
    public void peerPokeActionIncludesAvatarWhenProvided() {
        assertTrue(store.queuePeerPoke("b", "a", "abcXYZ012").ok);
        String[] parts = store.drainPendingActions(store.getOrCreateClient("b")).get(0).split("\\|", 4);
        assertEquals("POKE", parts[0]);
        assertEquals("a", parts[1]);
        assertEquals("abcXYZ012", parts[3]);
    }

    @Test
    public void sanitizeAvatarRejectsUnsafePayload() {
        assertEquals("", ClientStore.sanitizeAvatar("has/slash"));
        assertEquals("", ClientStore.sanitizeAvatar(""));
        assertEquals("ok_1-2", ClientStore.sanitizeAvatar("ok_1-2"));
    }

    @Test
    public void sanitizeClientForApiStripsAvatar() {
        Map<String, Object> dirty = new LinkedHashMap<>();
        dirty.put("clientId", "worker-a");
        dirty.put("avatar", "abcXYZ012");
        Map<String, Object> clean = store.sanitizeClientForApi(dirty);
        assertFalse(clean.containsKey("avatar"));
    }

    @Test
    public void peerSnapshotIncludesAvatar() {
        Map<String, Object> client = store.getOrCreateClient("b");
        client.put("avatar", "abcXYZ012");
        store.setClient("b", client);
        store.getOrCreateClient("a");
        assertEquals("abcXYZ012", store.peerSnapshot("a").get(0).get("avatar"));
    }

    @Test
    public void peerSnapshotExcludesSelf() {
        store.getOrCreateClient("a");
        store.getOrCreateClient("b");
        assertEquals(1, store.peerSnapshot("a").size());
        assertEquals("b", store.peerSnapshot("a").get(0).get("clientId"));
    }

    @Test
    public void regularPendingActionExpiresAfterDefaultTtl() {
        long expiredAt = System.currentTimeMillis() - ClientStore.PENDING_ACTION_TTL_MS - 1;
        store.queueClientAction("b", "POKE|a|" + expiredAt, ClientStore.PENDING_ACTION_TTL_MS, expiredAt);
        store.queueClientAction("b", "POKE|a|fresh");
        assertEquals(List.of("POKE|a|fresh"), store.drainPendingActions(store.getOrCreateClient("b")));
    }

    @Test
    public void customTtlOutlivesDefaultTtl() {
        long queuedAt = System.currentTimeMillis() - ClientStore.PENDING_ACTION_TTL_MS - 1;
        store.queueClientAction("b", "RELAY|x", ClientStore.PENDING_ACTION_TTL_MS * 4, queuedAt);
        assertEquals(List.of("RELAY|x"), store.drainPendingActions("b"));
    }

    @Test
    public void replacingClientMetadataKeepsQueuedActions() {
        store.queuePeerMessage("b", "a", "one");
        Map<String, Object> copy = new LinkedHashMap<>(store.getOrCreateClient("b"));
        store.queuePeerMessage("b", "a", "two");
        store.setClient("b", copy);
        List<String> drained = store.drainPendingActions(store.getOrCreateClient("b"));
        assertEquals(2, drained.size());
        assertTrue(store.drainPendingActions("b").isEmpty());
    }

    @Test
    public void queueUsesNormalizedClientId() {
        store.queuePeerMessage(" b ", "a", "hi");
        store.queueClientAction("\uFEFFb", "CANCEL_SCHEDULE");
        assertEquals(2, store.drainPendingActions("b").size());
        assertEquals(List.of("a", "b"), new ArrayList<>(new java.util.TreeSet<>(store.clients().keySet())));
    }

    @Test(timeout = 20_000)
    public void concurrentQueueAndDrainKeepEveryActionOnceInOrder() throws Exception {
        int producers = 6;
        int perProducer = 2_000;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean producing = new java.util.concurrent.atomic.AtomicBoolean(true);
        List<String> received = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int p = 0; p < producers; p++) {
            String from = "p" + p;
            threads.add(new Thread(() -> {
                await(start);
                for (int i = 0; i < perProducer; i++) {
                    store.queueClientAction("b", from + ":" + i);
                    store.appendClientEvent(from, "noise");
                }
            }));
        }
        Thread drainer = new Thread(() -> {
            await(start);
            while (producing.get()) {
                received.addAll(store.drainPendingActions(store.getOrCreateClient("b")));
                store.setClient("b", new LinkedHashMap<>(store.getOrCreateClient("b")));
                store.publicClientsSnapshot();
            }
        });
        threads.forEach(Thread::start);
        drainer.start();
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }
        producing.set(false);
        drainer.join();
        received.addAll(store.drainPendingActions("b"));

        assertEquals(producers * perProducer, received.size());
        assertEquals(received.size(), new java.util.HashSet<>(received).size());
        int[] next = new int[producers];
        for (String action : received) {
            String[] parts = action.split(":");
            int producer = Integer.parseInt(parts[0].substring(1));
            assertEquals("順序錯亂：" + action, next[producer], Integer.parseInt(parts[1]));
            next[producer]++;
        }
    }

    private static void await(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    public void lastResultIsLoggedWhenTaskAlreadyRescheduled() {
        Map<String, Object> existing = store.getOrCreateClient("worker-a");
        Map<String, Object> scheduled = new LinkedHashMap<>();
        scheduled.put("id", "work-out");
        scheduled.put("name", "下班打卡");
        scheduled.put("status", "SCHEDULED");
        scheduled.put("message", "");
        existing.put("tasks", new ArrayList<>(List.of(scheduled)));

        Map<String, Object> next = new LinkedHashMap<>(scheduled);
        next.put("lastResultStatus", "SUCCESS");
        next.put("lastResultMessage", "[成功] 打卡成功");

        store.logTaskTransitions(existing, List.of(next));
        List<?> events = (List<?>) existing.get("eventLog");
        assertEquals(1, events.size());
        assertTrue(String.valueOf(((Map<?, ?>) events.get(0)).get("text")).contains("回報執行結果：成功"));

        existing.put("tasks", new ArrayList<>(List.of(next)));
        store.logTaskTransitions(existing, List.of(next));
        assertEquals(1, ((List<?>) existing.get("eventLog")).size());
    }

    @Test
    public void lastResultIsNotLoggedAgainWhenCurrentStatusAlreadyShowsIt() {
        Map<String, Object> existing = store.getOrCreateClient("worker-a");
        Map<String, Object> checking = new LinkedHashMap<>();
        checking.put("id", "work-out");
        checking.put("name", "下班打卡");
        checking.put("status", "CHECKING_IN");
        existing.put("tasks", new ArrayList<>(List.of(checking)));

        Map<String, Object> success = new LinkedHashMap<>();
        success.put("id", "work-out");
        success.put("name", "下班打卡");
        success.put("status", "SUCCESS");
        success.put("message", "[成功] 打卡成功");
        success.put("lastResultStatus", "SUCCESS");
        success.put("lastResultMessage", "[成功] 打卡成功");

        store.logTaskTransitions(existing, List.of(success));
        List<?> events = (List<?>) existing.get("eventLog");
        assertEquals(1, events.size());
        assertTrue(String.valueOf(((Map<?, ?>) events.get(0)).get("text")).contains("→ 成功"));
        assertFalse(String.valueOf(((Map<?, ?>) events.get(0)).get("text")).contains("回報執行結果"));
    }
}
