package com.example.server.store;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DevMachineStoreTest {

    @Test
    public void onlineWithinThreeMinutes() {
        AtomicLong clock = new AtomicLong(100_000L);
        DevMachineStore store = new DevMachineStore(clock::get);
        Map<String, Object> body = payload("home", 50, "ai/issue-50", 90_000L);
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("home", body));
        List<Map<String, Object>> list = store.listForApi(100_000L, issue -> "標題");
        Map<String, Object> home = list.get(0);
        assertTrue((Boolean) home.get("online"));
        assertEquals(50, home.get("issue"));
        assertEquals("標題", home.get("issueTitle"));
    }

    @Test
    public void offlineAfterThreeMinutesHidesWorkFields() {
        AtomicLong clock = new AtomicLong(1_000_000L);
        DevMachineStore store = new DevMachineStore(clock::get);
        store.applyHeartbeat("home", payload("home", 50, "b", 900_000L));
        clock.set(1_000_000L + DevMachineStore.ONLINE_WINDOW_MS + 1);
        Map<String, Object> home = store.listForApi(clock.get(), issue -> "x").get(0);
        assertFalse((Boolean) home.get("online"));
        assertNull(home.get("issue"));
        assertNull(home.get("branch"));
        assertNull(home.get("runStartedAt"));
        assertEquals(1_000_000L, home.get("lastSeen"));
    }

    @Test
    public void repeatReportsWithinTenSecondsStillUpdate() {
        AtomicLong clock = new AtomicLong(10_000L);
        DevMachineStore store = new DevMachineStore(clock::get);
        store.applyHeartbeat("home", payload("home", 1, "a", 5_000L));
        clock.addAndGet(5_000L);
        store.applyHeartbeat("home", payload("home", 2, "b", 5_000L));
        Map<String, Object> home = store.listForApi(clock.get(), issue -> "").get(0);
        assertEquals(2, home.get("issue"));
        assertEquals("b", home.get("branch"));
    }

    @Test
    public void rejectsUnknownMachineAndBadFields() {
        DevMachineStore store = new DevMachineStore();
        assertEquals(DevMachineStore.ApplyResult.BAD_MACHINE, store.applyHeartbeat("laptop", payload("laptop", 1, "a", 1L)));
        Map<String, Object> badBranch = new HashMap<>();
        badBranch.put("machine", "home");
        badBranch.put("branch", "bad branch!");
        assertEquals(DevMachineStore.ApplyResult.BAD_BODY, store.applyHeartbeat("home", badBranch));
        Map<String, Object> badCommit = new HashMap<>();
        badCommit.put("machine", "home");
        badCommit.put("commit", "nope");
        assertEquals(DevMachineStore.ApplyResult.BAD_BODY, store.applyHeartbeat("home", badCommit));
    }

    @Test
    public void listOrderIsFixedAndBounded() {
        DevMachineStore store = new DevMachineStore();
        List<Map<String, Object>> list = store.listForApi(System.currentTimeMillis(), issue -> "");
        assertEquals(2, list.size());
        assertEquals("home", list.get(0).get("machine"));
        assertEquals("office", list.get(1).get("machine"));
        assertEquals("小貓", list.get(0).get("label"));
    }

    private static Map<String, Object> payload(String machine, Integer issue, String branch, long runStartedAt) {
        Map<String, Object> m = new HashMap<>();
        m.put("machine", machine);
        m.put("issue", issue);
        m.put("branch", branch);
        m.put("runStartedAt", runStartedAt);
        m.put("agentRunning", true);
        m.put("commit", "abcdef1");
        m.put("battery", 80);
        m.put("charging", false);
        return m;
    }
}