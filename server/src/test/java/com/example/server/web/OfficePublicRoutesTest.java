package com.example.server.web;

import com.example.server.auth.DevStatusTokenAuth;
import com.example.server.store.DevMachineStore;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusModels.IssueRow;
import com.example.server.store.DevStatusService;
import com.example.server.store.SecretarySpeech;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OfficePublicRoutesTest {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "ai/issue-|/Users/|battery|charging|worktree", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_TOP = Set.of("success", "characters");
    private static final Set<String> ALLOWED_CHAR_BASE = Set.of("status", "updatedAt");
    private static final Set<String> ALLOWED_TIGER_EXTRA = Set.of(
            "active", "completedToday", "queue", "cpuPercent", "ramUsedGb", "ramTotalGb", "ramPercent");
    private static final Pattern TIGER_STATUS = Pattern.compile("^(工作中( ×\\d+)?|待命中|休息中)$");
    private static final Pattern REVIEWER_STATUS = Pattern.compile("^(審核中( ×\\d+)?|待機)$");

    private Javalin app;
    private HttpClient http;
    private String base;
    private final DevMachineStore store = new DevMachineStore(() -> 9_000_000L);
    private DevStatusService status;

    @Before
    public void setUp() {
        status = new DevStatusService(repo -> emptyGithub());
        startApp();
    }

    private void startApp() {
        if (app != null) {
            app.stop();
        }
        app = Javalin.create(config -> config.showJavalinBanner = false);
        DevMachineRoutes devRoutes = new DevMachineRoutes(
                new com.example.server.auth.AuthService(),
                new DevStatusTokenAuth("test-token"),
                store,
                status,
                new Gson());
        devRoutes.register(app);
        new OfficePublicRoutes(store, status).register(app);
        app.start(0);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    private static DevStatusModels.Snapshot emptyGithub() {
        return new DevStatusModels.Snapshot(Collections.emptyList(), Collections.emptyList(),
                Collections.emptySet());
    }

    private static DevStatusModels.Snapshot githubWithIssue(int issue, String secretaryLine,
                                                              DevStatusModels.PullRow pull) {
        List<DevStatusModels.IssueRow> issues = Collections.singletonList(
                new DevStatusModels.IssueRow(issue, "秘密標題", true, 1L, 2L, null, Collections.emptyList(),
                        secretaryLine));
        List<DevStatusModels.PullRow> pulls = pull == null ? Collections.emptyList()
                : Collections.singletonList(pull);
        return new DevStatusModels.Snapshot(issues, pulls, Collections.emptySet());
    }

  private JsonObject fetchPublic() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/office/public"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        assertNoForbidden(response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private void assertNoForbidden(String body) {
        assertFalse(body, FORBIDDEN.matcher(body).find());
        assertFalse(body, body.contains("\"branch\""));
        assertFalse(body, body.contains("猛虎"));
    }

    @Test
    public void tigerWorkingFromGithubWhenMachineOffline() throws Exception {
        List<DevStatusModels.IssueRow> issues = new ArrayList<>();
        issues.add(new DevStatusModels.IssueRow(100, "公開甲", true, 1L, 2L, null, Collections.emptyList(), null));
        issues.add(new DevStatusModels.IssueRow(101, "公開乙", true, 1L, 2L, null, Collections.emptyList(), null));
        DevStatusModels.Snapshot snap = new DevStatusModels.Snapshot(issues, Collections.emptyList(),
                new HashSet<>(List.of(100, 101)));
        status = new DevStatusService(repo -> snap);
        startApp();

        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        assertEquals("工作中 ×2", tiger.get("status").getAsString());
        assertEquals(2, tiger.getAsJsonArray("active").size());
    }

    @Test
    public void tigerGraceKeepsWorkingAfterHeartbeatGap() throws Exception {
        Map<String, Object> run = new HashMap<>();
        run.put("issue", 501);
        run.put("round", 1);
        run.put("branch", "ai/issue-501");
        run.put("startedAt", 9_000_000L - 60_000L);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("runs", Collections.singletonList(run));
        store.applyHeartbeat("tiger", body);

        Map<String, Object> idleBeat = new LinkedHashMap<>();
        idleBeat.put("machine", "tiger");
        idleBeat.put("agentRunning", false);
        idleBeat.put("runs", Collections.emptyList());
        store.applyHeartbeat("tiger", idleBeat);

        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        assertEquals("工作中", tiger.get("status").getAsString());
        assertEquals(1, tiger.getAsJsonArray("active").size());
    }

    @Test
    public void activeRunShowsTitleWhenSnapshotMissingIssue() throws Exception {
        status = new DevStatusService(new DevStatusService.Fetcher() {
            @Override
            public DevStatusModels.Snapshot fetch(String repo) {
                return emptyGithub();
            }

            @Override
            public IssueRow fetchIssue(String repo, int number) {
                if (number == 571) {
                    return new IssueRow(571, "經絡研習室：載入 AvatarSample_A 成年角色並加上角色切換",
                            true, 0, 0, null, Collections.emptyList(), null);
                }
                return null;
            }
        });
        startApp();

        Map<String, Object> run = new HashMap<>();
        run.put("issue", 571);
        run.put("round", 1);
        run.put("branch", "ai/issue-571");
        run.put("startedAt", 9_000_000L - 120_000L);
        run.put("role", "dev");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("runs", Collections.singletonList(run));
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));

        JsonObject active = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("active").get(0).getAsJsonObject();
        assertTrue(active.has("title"));
        assertEquals("經絡研習室：載入 AvatarSample_A 成年角色並加上角色切換",
                active.get("title").getAsString());
        assertFalse(active.get("phrase").getAsString().contains(SecretarySpeech.ACTIVE_FALLBACK));
    }

    @Test
    public void tigerQueueMergesHeartbeatWaiter() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        body.put("queued", Collections.singletonList(Map.of("issue", 566, "afterIssue", 559)));
        store.applyHeartbeat("tiger", body);

        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        JsonArray queue = tiger.getAsJsonArray("queue");
        assertEquals(1, queue.size());
        assertEquals(566, queue.get(0).getAsJsonObject().get("issue").getAsInt());
    }

    @Test
    public void tigerQueueIncludesPlannedTitleWithoutIssue() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        body.put("queued", List.of(
                Map.of("issue", 573, "afterIssue", 571),
                Map.of("title", "預訂：收尾 Atlas 驗證")));
        store.applyHeartbeat("tiger", body);

        JsonArray queue = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("queue");
        assertEquals(2, queue.size());
        assertEquals(573, queue.get(0).getAsJsonObject().get("issue").getAsInt());
        assertEquals("預訂：收尾 Atlas 驗證", queue.get(1).getAsJsonObject().get("title").getAsString());
        assertFalse(queue.get(1).getAsJsonObject().has("issue"));
    }

    @Test
    public void activeEntryFromMachineChainStageAndFailed() throws Exception {
        status = new DevStatusService(repo -> githubWithIssue(680, null, null));
        startApp();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        body.put("issue", 680);
        body.put("branch", "ai/issue-680");
        body.put("runStartedAt", 9_000_000L - 3_600_000L);
        body.put("stageStartedAt", 9_000_000L - 120_000L);
        body.put("chain", "白虎");
        body.put("stage", "等 CI");
        body.put("verdict", "FAIL");
        store.applyHeartbeat("tiger", body);

        JsonObject active = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("active").get(0).getAsJsonObject();
        assertEquals("白虎", active.get("chain").getAsString());
        assertEquals("等 CI", active.get("stage").getAsString());
        assertTrue(active.get("failed").getAsBoolean());
        assertEquals(2, active.get("minutes").getAsInt());
        assertEquals(9_000_000L - 120_000L, active.get("stageStartedAt").getAsLong());
    }

    @Test
    public void tigerQueueSkipsActiveRunIssue() throws Exception {
        Map<String, Object> run = new HashMap<>();
        run.put("issue", 571);
        run.put("round", 1);
        run.put("branch", "ai/issue-571");
        run.put("startedAt", 9_000_000L - 60_000L);
        List<Map<String, Object>> queued = List.of(
                Map.of("issue", 571, "afterIssue", 580),
                Map.of("issue", 573, "afterIssue", 571));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("runs", Collections.singletonList(run));
        body.put("queued", queued);
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));

        JsonArray queue = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("queue");
        assertEquals(1, queue.size());
        assertEquals(573, queue.get(0).getAsJsonObject().get("issue").getAsInt());
    }

    @Test
    public void tigerOfflineResting() throws Exception {
        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        assertEquals("休息中", tiger.get("status").getAsString());
        assertTrue(TIGER_STATUS.matcher(tiger.get("status").getAsString()).matches());
        assertEquals(0, tiger.getAsJsonArray("active").size());
        assertFalse(tiger.has("cpuPercent"));
        assertFalse(tiger.has("ramPercent"));
    }

    @Test
    public void tigerReviewRunNotStandby() throws Exception {
        Map<String, Object> run = new HashMap<>();
        run.put("issue", 374);
        run.put("round", 2);
        run.put("branch", "ai/issue-374");
        run.put("role", "review");
        run.put("startedAt", 9_000_000L - 300_000L);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        body.put("runs", Collections.singletonList(run));
        body.put("battery", 80);
        body.put("charging", true);
        store.applyHeartbeat("tiger", body);

        assertEquals("工作中", fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .get("status").getAsString());
    }

    @Test
    public void tigerStandbyOnlineWithoutLoop() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        body.put("battery", 80);
        body.put("charging", true);
        store.applyHeartbeat("tiger", body);

        assertEquals("待命中", fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .get("status").getAsString());
    }

    @Test
    public void tigerWorkingWithPhraseAndLinks() throws Exception {
        status = new DevStatusService(repo -> githubWithIssue(385, "正在把工作狀態整理成公開 API",
                new DevStatusModels.PullRow(390, "pr", true, false, false, 0, 0, null, null,
                        new HashSet<>(Collections.singletonList(385)))));
        startApp();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("issue", 385);
        body.put("round", 2);
        body.put("model", "composer-2.5");
        body.put("branch", "ai/issue-385");
        body.put("worktree", "/Users/tina/PunchClock-oki/wt-385");
        body.put("runStartedAt", 9_000_000L - 120_000L);
        store.applyHeartbeat("tiger", body);

        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        assertEquals("工作中", tiger.get("status").getAsString());
        JsonObject active = tiger.getAsJsonArray("active").get(0).getAsJsonObject();
        assertEquals("正在把工作狀態整理成公開 API", active.get("phrase").getAsString());
        assertEquals(385, active.get("issue").getAsInt());
        assertTrue(active.get("issueUrl").getAsString().contains("/issues/385"));
        assertEquals(390, active.get("pr").getAsInt());
        assertEquals(2, active.get("round").getAsInt());
        assertEquals(2, active.get("minutes").getAsInt());
        assertEquals("composer-2.5", active.get("model").getAsString());
        assertFalse(active.get("phrase").getAsString().contains("秘密標題"));
    }

    @Test
    public void phraseFallbackWhenNoSecretaryLine() throws Exception {
        status = new DevStatusService(repo -> githubWithIssue(257, null, null));
        startApp();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("issue", 257);
        store.applyHeartbeat("tiger", body);

        JsonObject active = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("active").get(0).getAsJsonObject();
        assertEquals("秘密標題", active.get("phrase").getAsString());
        assertEquals("秘密標題", active.get("title").getAsString());
    }

    @Test
    public void completedTodayUsesPublicTitleNotSecretaryLine() throws Exception {
        ZoneId taipei = ZoneId.of("Asia/Taipei");
        long now = 9_000_000L;
        long mergedAt = Instant.ofEpochMilli(now).atZone(taipei).toLocalDate()
                .atStartOfDay(taipei).toInstant().toEpochMilli() + 60_000L;
        status = new DevStatusService(repo -> githubWithIssue(40, "公開 issue 標題",
                new DevStatusModels.PullRow(41, "pr", false, false, true, 0, 0, mergedAt, null,
                        new HashSet<>(Collections.singletonList(40)))));
        startApp();

        JsonObject item = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("completedToday").get(0).getAsJsonObject();
        assertEquals("秘密標題", item.get("title").getAsString());
        assertFalse(item.has("phrase"));
    }

    @Test
    public void devDurationMinutesNormalizesUnixSeconds() {
        long mergedAt = 1_730_000_000_000L;
        long devStartSec = 1_729_999_000L;
        assertEquals(Long.valueOf(16L),
                OfficePublicRoutes.OfficePublicPayload.devDurationMinutes(mergedAt, devStartSec));
    }

    @Test
    public void devDurationMinutesRejectsAbsurdSpan() {
        long mergedAt = 1_730_000_000_000L;
        assertNull(OfficePublicRoutes.OfficePublicPayload.devDurationMinutes(mergedAt, 1_000L));
    }

    @Test
    public void completedTodayDurationFromEarliestDevRun() throws Exception {
        ZoneId taipei = ZoneId.of("Asia/Taipei");
        long now = 9_000_000L;
        long devStart = 1_000_000L;
        long mergedAt = devStart + 72 * 60_000L;
        long dayStart = Instant.ofEpochMilli(now).atZone(taipei).toLocalDate()
                .atStartOfDay(taipei).toInstant().toEpochMilli();
        assertTrue(mergedAt >= dayStart);
        status = new DevStatusService(repo -> githubWithIssue(658, "耗時測試標題",
                new DevStatusModels.PullRow(99, "pr", false, false, true, 0, 0, mergedAt, null,
                        new HashSet<>(Collections.singletonList(658)))));
        startApp();
        Map<String, Object> run = new HashMap<>();
        run.put("issue", 658);
        run.put("round", 1);
        run.put("branch", "ai/issue-658");
        run.put("startedAt", devStart);
        run.put("role", "dev");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        body.put("runs", Collections.singletonList(run));
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", body));

        JsonObject item = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("completedToday").get(0).getAsJsonObject();
        assertEquals(72, item.get("durationMinutes").getAsLong());
        assertFalse(item.has("aiWorkMinutes"));
    }

    @Test
    public void completedTodayShowsAiWorkWhenAgentSessionsRecorded() throws Exception {
        ZoneId taipei = ZoneId.of("Asia/Taipei");
        long now = 9_000_000L;
        long devStart = 1_000_000L;
        long mergedAt = devStart + 72 * 60_000L;
        long dayStart = Instant.ofEpochMilli(now).atZone(taipei).toLocalDate()
                .atStartOfDay(taipei).toInstant().toEpochMilli();
        assertTrue(mergedAt >= dayStart);
        status = new DevStatusService(repo -> githubWithIssue(810, "AI 工時",
                new DevStatusModels.PullRow(811, "pr", false, false, true, devStart, 0, mergedAt, null,
                        new HashSet<>(Collections.singletonList(810)))));
        startApp();

        Map<String, Object> devMark = new HashMap<>();
        devMark.put("issue", 810);
        devMark.put("round", 0);
        devMark.put("branch", "ai/issue-810");
        devMark.put("startedAt", devStart);
        devMark.put("role", "dev");
        Map<String, Object> markBody = new LinkedHashMap<>();
        markBody.put("machine", "tiger");
        markBody.put("agentRunning", false);
        markBody.put("runs", Collections.singletonList(devMark));
        store.applyHeartbeat("tiger", markBody);

        long agentStart = now - 15 * 60_000L;
        Map<String, Object> work = new LinkedHashMap<>();
        work.put("machine", "tiger");
        work.put("issue", 810);
        work.put("branch", "ai/issue-810");
        work.put("agentRunning", true);
        work.put("runStartedAt", agentStart);
        store.applyHeartbeat("tiger", work);

        Map<String, Object> idle = new LinkedHashMap<>();
        idle.put("machine", "tiger");
        idle.put("agentRunning", false);
        store.applyHeartbeat("tiger", idle);
        assertEquals(Long.valueOf(15L), store.recordedAgentWorkMinutes(810));

        JsonObject item = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("completedToday").get(0).getAsJsonObject();
        assertEquals(72, item.get("durationMinutes").getAsLong());
        assertEquals(15, item.get("aiWorkMinutes").getAsLong());
    }

    @Test
    public void completedTodayDurationFromRecordedStartAfterRunCleared() throws Exception {
        ZoneId taipei = ZoneId.of("Asia/Taipei");
        long now = 9_000_000L;
        long devStart = 500_000L;
        long mergedAt = devStart + 30 * 60_000L;
        long dayStart = Instant.ofEpochMilli(now).atZone(taipei).toLocalDate()
                .atStartOfDay(taipei).toInstant().toEpochMilli();
        assertTrue(mergedAt >= dayStart);
        status = new DevStatusService(repo -> githubWithIssue(701, "持久耗時",
                new DevStatusModels.PullRow(702, "pr", false, false, true, devStart, 0, mergedAt, null,
                        new HashSet<>(Collections.singletonList(701)))));
        startApp();

        Map<String, Object> withRun = new HashMap<>();
        withRun.put("issue", 701);
        withRun.put("round", 1);
        withRun.put("branch", "ai/issue-701");
        withRun.put("startedAt", devStart);
        withRun.put("role", "dev");
        Map<String, Object> work = new LinkedHashMap<>();
        work.put("machine", "tiger");
        work.put("agentRunning", true);
        work.put("runs", Collections.singletonList(withRun));
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("tiger", work));

        Map<String, Object> cleared = new LinkedHashMap<>();
        cleared.put("machine", "tiger");
        cleared.put("agentRunning", false);
        store.applyHeartbeat("tiger", cleared);

        JsonObject item = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("completedToday").get(0).getAsJsonObject();
        assertEquals(30, item.get("durationMinutes").getAsLong());
    }

    @Test
    public void activeOmitsMergedIssueWhenRunStageRemains() throws Exception {
        status = new DevStatusService(repo -> githubWithIssue(712, "已合併殘留",
                new DevStatusModels.PullRow(713, "pr", false, false, true, 0, 0, 8_999_000L, null,
                        new HashSet<>(Collections.singletonList(712)))));
        startApp();

        Map<String, Object> run = new HashMap<>();
        run.put("issue", 712);
        run.put("round", 2);
        run.put("branch", "ai/issue-712");
        run.put("role", "review");
        run.put("startedAt", 9_000_000L - 60_000L);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        body.put("runs", Collections.singletonList(run));
        store.applyHeartbeat("tiger", body);

        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        assertEquals(0, tiger.getAsJsonArray("active").size());
        assertEquals("待命中", tiger.get("status").getAsString());
    }

    @Test
    public void tigerSelectedByMachineIdRegardlessOfDisplayName() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("displayName", "私人女秘書");
        body.put("agentRunning", false);
        store.applyHeartbeat("tiger", body);

        assertEquals("待命中", fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .get("status").getAsString());
    }

    @Test
    public void onlineDevMachineSuppliesTigerWhenPreferredAbsent() throws Exception {
        Map<String, Object> home = new LinkedHashMap<>();
        home.put("machine", "home");
        home.put("displayName", "私人女秘書");
        home.put("agentRunning", true);
        home.put("issue", 999);
        store.applyHeartbeat("home", home);

        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        assertEquals("工作中", tiger.get("status").getAsString());
        assertEquals(1, tiger.getAsJsonArray("active").size());
    }

    @Test
    public void utf8MachineIdSuppliesTigerWithoutLeakingId() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "猛虎");
        body.put("displayName", "小秘書");
        body.put("agentRunning", true);
        body.put("issue", 497);
        body.put("branch", "ai/issue-497");
        assertEquals(DevMachineStore.ApplyResult.OK, store.applyHeartbeat("猛虎", body));

        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        assertEquals("工作中", tiger.get("status").getAsString());
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/office/public"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertNoForbidden(response.body());
    }

    @Test
    public void prefersConfiguredMachineWhenBothOnline() throws Exception {
        Map<String, Object> home = new LinkedHashMap<>();
        home.put("machine", "home");
        home.put("agentRunning", true);
        home.put("issue", 100);
        store.applyHeartbeat("home", home);

        Map<String, Object> tigerBody = new LinkedHashMap<>();
        tigerBody.put("machine", "tiger");
        tigerBody.put("agentRunning", false);
        store.applyHeartbeat("tiger", tigerBody);

        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        assertEquals("待命中", tiger.get("status").getAsString());
        assertEquals(0, tiger.getAsJsonArray("active").size());
    }

    @Test
    public void activeDevStageOnHeartbeatRun() throws Exception {
        Map<String, Object> run = new HashMap<>();
        run.put("issue", 576);
        run.put("round", 1);
        run.put("branch", "ai/issue-576");
        run.put("startedAt", 9_000_000L - 90_000L);
        run.put("role", "dev");
        run.put("model", "composer-2.5");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("runs", Collections.singletonList(run));
        store.applyHeartbeat("tiger", body);

        JsonObject active = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("active").get(0).getAsJsonObject();
        assertEquals("開發中", active.get("stage").getAsString());
    }

    @Test
    public void activeReviewStageAppearsInTigerActive() throws Exception {
        Map<String, Object> run = new HashMap<>();
        run.put("issue", 374);
        run.put("round", 2);
        run.put("branch", "ai/issue-374");
        run.put("role", "review");
        run.put("startedAt", 9_000_000L - 120_000L);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("runs", Collections.singletonList(run));
        store.applyHeartbeat("tiger", body);

        JsonObject active = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("active").get(0).getAsJsonObject();
        assertEquals("審查中", active.get("stage").getAsString());
    }

    @Test
    public void chainRunWithoutAgentShowsWaitCiNotRest() throws Exception {
        status = new DevStatusService(repo -> githubWithIssue(556, null,
                new DevStatusModels.PullRow(560, "pr", true, false, false, 0, 0, 8_999_000L, null,
                        new HashSet<>(Collections.singletonList(556)))));
        startApp();

        Map<String, Object> run = new HashMap<>();
        run.put("issue", 556);
        run.put("round", 0);
        run.put("branch", "ai/issue-556");
        run.put("startedAt", 9_000_000L - 300_000L);
        run.put("role", "dev");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        body.put("runs", Collections.singletonList(run));
        store.applyHeartbeat("tiger", body);

        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        assertEquals("工作中", tiger.get("status").getAsString());
        assertEquals("等 CI", tiger.getAsJsonArray("active").get(0).getAsJsonObject().get("stage").getAsString());
    }

    @Test
    public void fixingStageAfterReviewFail() throws Exception {
        Map<String, Object> run = new HashMap<>();
        run.put("issue", 501);
        run.put("round", 2);
        run.put("branch", "ai/issue-501");
        run.put("startedAt", 9_000_000L - 60_000L);
        run.put("role", "dev");
        run.put("model", "composer-2.5");
        run.put("verdict", "FAIL");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("verdict", "FAIL");
        body.put("runs", Collections.singletonList(run));
        store.applyHeartbeat("tiger", body);

        assertEquals("修正中", fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("active").get(0).getAsJsonObject().get("stage").getAsString());
    }

    @Test
    public void mergingStageWhenChecksPassed() throws Exception {
        status = new DevStatusService(repo -> githubWithIssue(566, null,
                new DevStatusModels.PullRow(570, "pr", true, false, false, 0, 0, 8_999_000L, null,
                        new HashSet<>(Collections.singletonList(566)))));
        startApp();

        Map<String, Object> run = new HashMap<>();
        run.put("issue", 566);
        run.put("round", 0);
        run.put("branch", "ai/issue-566");
        run.put("startedAt", 9_000_000L - 180_000L);
        run.put("role", "dev");
        run.put("verdict", "PASS");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        body.put("runs", Collections.singletonList(run));
        store.applyHeartbeat("tiger", body);

        assertEquals("合併中", fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("active").get(0).getAsJsonObject().get("stage").getAsString());
    }

    @Test
    public void activeChainAndStageStartedAtMinutes() throws Exception {
        Map<String, Object> run = new HashMap<>();
        run.put("issue", 640);
        run.put("round", 1);
        run.put("branch", "ai/issue-640");
        run.put("startedAt", 9_000_000L - 3_600_000L);
        run.put("stageStartedAt", 9_000_000L - 300_000L);
        run.put("role", "dev");
        run.put("stage", "審查中");
        run.put("chain", "白虎");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("runs", Collections.singletonList(run));
        store.applyHeartbeat("tiger", body);

        JsonObject active = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("active").get(0).getAsJsonObject();
        assertEquals("白虎", active.get("chain").getAsString());
        assertEquals("審查中", active.get("stage").getAsString());
        assertEquals(5, active.get("minutes").getAsInt());
    }

    @Test
    public void activeCiFailFlagOnPublicApi() throws Exception {
        Map<String, Object> run = new HashMap<>();
        run.put("issue", 501);
        run.put("round", 2);
        run.put("branch", "ai/issue-501");
        run.put("startedAt", 9_000_000L - 60_000L);
        run.put("role", "dev");
        run.put("verdict", "FAIL");
        run.put("stage", "等 CI");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        body.put("runs", Collections.singletonList(run));
        store.applyHeartbeat("tiger", body);

        JsonObject active = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("active").get(0).getAsJsonObject();
        assertTrue(active.get("failed").getAsBoolean());
        assertEquals("FAIL", active.get("verdict").getAsString());
    }

    @Test
    public void heartbeatRunStageLiteralOverridesInference() throws Exception {
        Map<String, Object> run = new HashMap<>();
        run.put("issue", 580);
        run.put("round", 1);
        run.put("branch", "ai/issue-580");
        run.put("startedAt", 9_000_000L - 60_000L);
        run.put("role", "dev");
        run.put("stage", "合併中");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("runs", Collections.singletonList(run));
        store.applyHeartbeat("tiger", body);

        assertEquals("合併中", fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("active").get(0).getAsJsonObject().get("stage").getAsString());
    }

    @Test
    public void tigerSystemMetricsWhenOnline() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", false);
        body.put("cpuPercent", 38);
        body.put("ramUsedGb", 11.4);
        body.put("ramTotalGb", 16);
        body.put("ramPercent", 71);
        store.applyHeartbeat("tiger", body);

        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        assertEquals(38.0, tiger.get("cpuPercent").getAsDouble(), 0.01);
        assertEquals(11.4, tiger.get("ramUsedGb").getAsDouble(), 0.01);
        assertEquals(16.0, tiger.get("ramTotalGb").getAsDouble(), 0.01);
        assertEquals(71, tiger.get("ramPercent").getAsInt());
    }

    @Test
    public void publicOfficeWhitelistWithoutSecrets() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("issue", 257);
        body.put("model", "composer-2.5");
        body.put("branch", "ai/issue-257");
        body.put("worktree", "/Users/tina/secret");
        store.applyHeartbeat("tiger", body);

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/office/public"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        assertNoForbidden(response.body());
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        for (String key : root.keySet()) {
            assertTrue("unexpected key: " + key, ALLOWED_TOP.contains(key));
        }
        assertTrue(root.get("success").getAsBoolean());
        JsonObject chars = root.getAsJsonObject("characters");
        for (String id : new String[] { "oki", "tiger", "reviewer" }) {
            assertTrue(chars.has(id));
            JsonObject row = chars.getAsJsonObject(id);
            for (String key : row.keySet()) {
                boolean ok = ALLOWED_CHAR_BASE.contains(key)
                        || ("tiger".equals(id) && ALLOWED_TIGER_EXTRA.contains(key));
                assertTrue("unexpected char field: " + key, ok);
            }
            String st = row.get("status").getAsString();
            if ("tiger".equals(id)) {
                assertTrue(st, TIGER_STATUS.matcher(st).matches());
            } else if ("reviewer".equals(id)) {
                assertTrue(st, REVIEWER_STATUS.matcher(st).matches());
            }
        }
    }
}