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
        body.put("displayName", "小貓");
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("home", body));
        List<Map<String, Object>> list = store.listForApi(100_000L, issue -> "標題");
        assertEquals(1, list.size());
        Map<String, Object> home = list.get(0);
        assertTrue((Boolean) home.get("online"));
        assertEquals(50, home.get("issue"));
        assertEquals("標題", home.get("issueTitle"));
        assertEquals("小貓", home.get("label"));
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
        assertEquals(DevMachineStore.ApplyResult.BAD_MACHINE, store.applyHeartbeat("Bad-ID", payload("x", 1, "a", 1L)));
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
    public void listOnlyReportedWithinStaleWindow() {
        AtomicLong clock = new AtomicLong(100_000L);
        DevMachineStore store = new DevMachineStore(clock::get);
        assertEquals(0, store.listForApi(clock.get(), issue -> "").size());

        store.applyHeartbeat("home", payload("home", 1, "a", 1L));
        store.applyHeartbeat("bear", payload("bear", 2, "b", 1L));
        assertEquals(2, store.listForApi(clock.get(), issue -> "").size());

        clock.set(100_000L + DevMachineStore.STALE_WINDOW_MS + 1);
        assertEquals(0, store.listForApi(clock.get(), issue -> "").size());
    }

    @Test
    public void optionalRoleModelVerdictFields() {
        DevMachineStore store = new DevMachineStore();
        Map<String, Object> body = payload("home", 254, "ai/issue-254", 1000L);
        body.put("role", "review");
        body.put("round", 2);
        body.put("model", "claude-opus-5-thinking-high");
        body.put("verdict", "FAIL");
        List<Map<String, Object>> runs = List.of(Map.of(
                "issue", 254,
                "round", 2,
                "branch", "ai/issue-254",
                "startedAt", 1000L,
                "role", "dev",
                "model", "gpt-5.2-fast",
                "verdict", "PASS"
        ));
        body.put("runs", runs);
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("home", body));
        Map<String, Object> home = store.listForApi(System.currentTimeMillis(), issue -> "").get(0);
        assertEquals("review", home.get("role"));
        assertEquals(2, home.get("round"));
        assertEquals("claude-opus-5-thinking-high", home.get("model"));
        assertEquals("FAIL", home.get("verdict"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> apiRuns = (List<Map<String, Object>>) home.get("runs");
        assertEquals("dev", apiRuns.get(0).get("role"));
        assertEquals("PASS", apiRuns.get(0).get("verdict"));

        Map<String, Object> badRole = new HashMap<>(body);
        badRole.put("role", "hacker");
        assertEquals(DevMachineStore.ApplyResult.BAD_BODY, store.applyHeartbeat("home", badRole));
    }

    @Test
    public void utf8MachineIdAllowed() {
        DevMachineStore store = new DevMachineStore();
        Map<String, Object> body = payload("x", 1, "a", 1L);
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("猛虎", body));
        Map<String, Object> row = store.listForApi(System.currentTimeMillis(), issue -> "").get(0);
        assertEquals("猛虎", row.get("machine"));
    }

    @Test
    public void rejectsIllegalMachineIds() {
        DevMachineStore store = new DevMachineStore();
        Map<String, Object> body = payload("x", 1, "a", 1L);
        assertEquals(DevMachineStore.ApplyResult.BAD_MACHINE, store.applyHeartbeat("Bad-ID", body));
        assertEquals(DevMachineStore.ApplyResult.BAD_MACHINE, store.applyHeartbeat("", body));
        String longId = "a".repeat(33);
        assertEquals(DevMachineStore.ApplyResult.BAD_MACHINE, store.applyHeartbeat(longId, body));
        assertEquals(DevMachineStore.ApplyResult.BAD_MACHINE,
                store.applyHeartbeat("ab\u0001cd", body));
    }

    @Test
    public void heartbeatQueuedField() {
        DevMachineStore store = new DevMachineStore();
        Map<String, Object> body = payload("tiger", 1, "a", 1L);
        body.put("queued", List.of(Map.of("issue", 566, "afterIssue", 559)));
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));
        DevMachineStore.Snapshot snap = store.machineSnapshots().get(0);
        assertEquals(1, snap.queued.size());
        assertEquals(566, snap.queued.get(0).issue);
        assertEquals(Integer.valueOf(559), snap.queued.get(0).afterIssue);
    }

    @Test
    public void heartbeatQueuedPlannedTitleOnly() {
        DevMachineStore store = new DevMachineStore();
        Map<String, Object> body = payload("tiger", 1, "a", 1L);
        body.put("queued", List.of(Map.of("title", "預訂下一批小單")));
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));
        DevMachineStore.QueuedEntry entry = store.machineSnapshots().get(0).queued.get(0);
        assertEquals(0, entry.issue);
        assertEquals("預訂下一批小單", entry.title);
    }

    @Test
    public void machineLevelChainStageStartedAtParsed() {
        DevMachineStore store = new DevMachineStore();
        Map<String, Object> body = payload("tiger", 640, "ai/issue-640", 1000L);
        body.put("chain", "青龍");
        body.put("stage", "審查中");
        body.put("stageStartedAt", 5_000L);
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));
        DevMachineStore.Snapshot snap = store.machineSnapshots().get(0);
        assertEquals("青龍", snap.chain);
        assertEquals("審查中", snap.stage);
        assertEquals(Long.valueOf(5_000L), snap.stageStartedAt);
    }

    @Test
    public void runStageFieldParsedWhenValid() {
        DevMachineStore store = new DevMachineStore();
        List<Map<String, Object>> runs = List.of(Map.of(
                "issue", 576,
                "round", 1,
                "branch", "ai/issue-576",
                "startedAt", 1000L,
                "role", "dev",
                "stage", "等 CI"
        ));
        Map<String, Object> body = payload("tiger", 576, "ai/issue-576", 1000L);
        body.put("runs", runs);
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));
        assertEquals("等 CI", store.machineSnapshots().get(0).runs.get(0).stage);
        Map<String, Object> bad = new HashMap<>(body);
        bad.put("runs", List.of(Map.of(
                "issue", 576,
                "round", 1,
                "branch", "ai/issue-576",
                "startedAt", 1000L,
                "stage", "隨便"
        )));
        assertEquals(DevMachineStore.ApplyResult.BAD_BODY, store.applyHeartbeat("tiger", bad));
    }

    @Test
    public void graceKeepsRunsOnEmptyHeartbeat() {
        AtomicLong clock = new AtomicLong(1_000_000L);
        DevMachineStore store = new DevMachineStore(clock::get);
        Map<String, Object> work = payload("office", 50, "ai/issue-50", 900_000L);
        work.put("runs", List.of(Map.of("issue", 50, "round", 1, "branch", "ai/issue-50", "startedAt", 900_000L)));
        store.applyHeartbeat("office", work);
        Map<String, Object> idle = new HashMap<>();
        idle.put("agentRunning", false);
        idle.put("runs", List.of());
        store.applyHeartbeat("office", idle);
        DevMachineStore.GraceHold grace = store.graceFor("office", clock.get());
        assertEquals(1, grace.runs.size());
    }

    @Test
    public void runChainAndStageStartedAtParsedWhenValid() {
        DevMachineStore store = new DevMachineStore();
        Map<String, Object> run = new HashMap<>();
        run.put("issue", 640);
        run.put("round", 1);
        run.put("branch", "ai/issue-640");
        run.put("startedAt", 1000L);
        run.put("stage", "開發中");
        run.put("chain", "青龍");
        run.put("stageStartedAt", 5_000L);
        Map<String, Object> body = payload("tiger", 640, "ai/issue-640", 1000L);
        body.put("runs", List.of(run));
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));
        DevMachineStore.RunEntry entry = store.machineSnapshots().get(0).runs.get(0);
        assertEquals("青龍", entry.chain);
        assertEquals(Long.valueOf(5_000L), entry.stageStartedAt);

        Map<String, Object> badFields = new HashMap<>(run);
        badFields.put("chain", "黃龍");
        badFields.put("stageStartedAt", -1);
        body.put("runs", List.of(badFields));
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));
        entry = store.machineSnapshots().get(0).runs.get(0);
        assertNull(entry.chain);
        assertNull(entry.stageStartedAt);
        assertEquals("開發中", entry.stage);
    }

    @Test
    public void displayNameAndLocationFromHeartbeat() {
        DevMachineStore store = new DevMachineStore();
        Map<String, Object> body = payload("tiger", 1, "a", 1L);
        body.put("displayName", "私人女秘書");
        body.put("location", "家用");
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));
        Map<String, Object> row = store.listForApi(System.currentTimeMillis(), issue -> "").get(0);
        assertEquals("私人女秘書", row.get("label"));
        assertEquals("家用", row.get("location"));
    }

    @Test
    public void systemMetricsOptionalAndListedWhenOnline() {
        AtomicLong clock = new AtomicLong(100_000L);
        DevMachineStore store = new DevMachineStore(clock::get);
        Map<String, Object> body = payload("tiger", 745, "ai/issue-745", 90_000L);
        body.put("cpuPercent", 38.5);
        body.put("ramUsedGb", 11.4);
        body.put("ramTotalGb", 16);
        body.put("ramPercent", 71);
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));
        DevMachineStore.Snapshot snap = store.machineSnapshots().get(0);
        assertEquals(38.5, snap.cpuPercent, 0.01);
        assertEquals(11.4, snap.ramUsedGb, 0.01);
        assertEquals(16.0, snap.ramTotalGb, 0.01);
        assertEquals(Integer.valueOf(71), snap.ramPercent);

        Map<String, Object> row = store.listForApi(clock.get(), issue -> "").get(0);
        assertEquals(38.5, row.get("cpuPercent"));
        assertEquals(11.4, row.get("ramUsedGb"));
        assertEquals(16.0, row.get("ramTotalGb"));
        assertEquals(71, row.get("ramPercent"));

        clock.set(100_000L + DevMachineStore.ONLINE_WINDOW_MS + 1);
        Map<String, Object> offline = store.listForApi(clock.get(), issue -> "").get(0);
        assertFalse((Boolean) offline.get("online"));
        assertNull(offline.get("cpuPercent"));
        assertNull(offline.get("ramPercent"));
    }

    @Test
    public void systemMetricsMissingIsOk() {
        DevMachineStore store = new DevMachineStore();
        Map<String, Object> body = new HashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));
        DevMachineStore.Snapshot snap = store.machineSnapshots().get(0);
        assertNull(snap.cpuPercent);
        assertNull(snap.ramUsedGb);
    }

    @Test
    public void rejectsInvalidSystemMetrics() {
        DevMachineStore store = new DevMachineStore();
        Map<String, Object> body = new HashMap<>();
        body.put("machine", "tiger");
        body.put("cpuPercent", 150);
        assertEquals(DevMachineStore.ApplyResult.BAD_BODY, store.applyHeartbeat("tiger", body));
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