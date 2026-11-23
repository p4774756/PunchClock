package com.example.server.web;

import com.example.server.auth.DevStatusTokenAuth;
import com.example.server.store.DevMachineStore;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusService;
import com.example.server.store.SecretarySpeech;
import com.google.gson.Gson;
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
import static org.junit.Assert.assertTrue;

public class OfficePublicRoutesTest {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "ai/issue-|/Users/|battery|charging|worktree", Pattern.CASE_INSENSITIVE);
    private static final Set<String> ALLOWED_TOP = Set.of("success", "characters");
    private static final Set<String> ALLOWED_CHAR_BASE = Set.of("status", "updatedAt");
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
    public void tigerOfflineResting() throws Exception {
        JsonObject tiger = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger");
        assertEquals("休息中", tiger.get("status").getAsString());
        assertTrue(TIGER_STATUS.matcher(tiger.get("status").getAsString()).matches());
        assertEquals(0, tiger.getAsJsonArray("active").size());
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
                new DevStatusModels.PullRow(390, "pr", false, false, true, 0, 0, 8_999_000L, null,
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
    public void completedTodayUsesSecretaryLine() throws Exception {
        ZoneId taipei = ZoneId.of("Asia/Taipei");
        long now = 9_000_000L;
        long mergedAt = Instant.ofEpochMilli(now).atZone(taipei).toLocalDate()
                .atStartOfDay(taipei).toInstant().toEpochMilli() + 60_000L;
        status = new DevStatusService(repo -> githubWithIssue(40, "今天收工一句",
                new DevStatusModels.PullRow(41, "pr", false, false, true, 0, 0, mergedAt, null,
                        new HashSet<>(Collections.singletonList(40)))));
        startApp();

        JsonArray done = fetchPublic().getAsJsonObject("characters").getAsJsonObject("tiger")
                .getAsJsonArray("completedToday");
        assertEquals(1, done.size());
        assertEquals("今天收工一句", done.get(0).getAsJsonObject().get("phrase").getAsString());
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
                        || ("tiger".equals(id) && ("active".equals(key) || "completedToday".equals(key)));
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