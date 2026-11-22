package com.example.server.web;

import com.example.server.AdminTestServer;
import com.example.server.auth.AuthService;
import com.example.server.auth.DevStatusTokenAuth;
import com.example.server.store.DevMachineStore;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DevMachineRoutesTest {

    private static final String TOKEN = "unit-test-dev-status-token";

    private Javalin app;
    private HttpClient http;
    private String base;
    private String adminCookie;
    private final AtomicLong clock = new AtomicLong(2_000_000L);

    @Before
    public void setUp() throws Exception {
        DevStatusService fakeStatus = new DevStatusService(repo -> new DevStatusModels.Snapshot(
                Collections.singletonList(new DevStatusModels.IssueRow(
                        126, "測試 issue", true, 1L, 2L, null, Collections.emptyList())),
                Collections.emptyList(),
                Collections.emptySet()
        ), "p4774756/PunchClock");
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        DevMachineStore store = new DevMachineStore(clock::get);
        clock.set(2_000_000L);
        AdminTestServer.IsolatedAuth auth = AdminTestServer.isolatedAuth();
        DevMachineRoutes routes = new DevMachineRoutes(
                auth.authService(),
                new DevStatusTokenAuth(TOKEN),
                store,
                fakeStatus,
                gson
        );
        app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        AdminTestServer.registerLogin(app, auth.authService());
        routes.register(app);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        adminCookie = AdminTestServer.loginCookie(base, auth.adminPassword(), http);
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    public void heartbeatRequiresTokenAndValidatesBody() throws Exception {
        Javalin unconfigured = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        try {
            new DevMachineRoutes(
                    new AuthService(),
                    new DevStatusTokenAuth(""),
                    new DevMachineStore(),
                    new DevStatusService(r -> new DevStatusModels.Snapshot(
                            Collections.emptyList(), Collections.emptyList(), Collections.emptySet())),
                    new Gson()
            ).register(unconfigured);
            String ubase = "http://127.0.0.1:" + unconfigured.port();
            HttpResponse<String> off = http.send(HttpRequest.newBuilder(URI.create(ubase + "/api/dev-machines/heartbeat"))
                    .header("Authorization", "Bearer x")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"machine\":\"home\"}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(503, off.statusCode());
        } finally {
            unconfigured.stop();
        }

        HttpResponse<String> badAuth = sendHeartbeat("Bearer wrong", "{\"machine\":\"home\"}");
        assertEquals(401, badAuth.statusCode());

        HttpResponse<String> badMachine = sendHeartbeat("Bearer " + TOKEN, "{\"machine\":\"Bad-ID\"}");
        assertEquals(400, badMachine.statusCode());

        byte[] huge = new byte[DevMachineStore.MAX_BODY_BYTES + 1];
        HttpResponse<String> big = http.send(HttpRequest.newBuilder(URI.create(base + "/api/dev-machines/heartbeat"))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(huge))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(400, big.statusCode());

        HttpResponse<String> ok = sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"issue\":126,\"branch\":\"ai/issue-126\",\"runStartedAt\":1000,"
                        + "\"agentRunning\":true,\"commit\":\"abcdef1\",\"battery\":90,\"charging\":true}");
        assertEquals(200, ok.statusCode());
        assertTrue(ok.body().contains("\"success\":true"));
    }

    @Test
    public void guestAndAdminListWithViewer() throws Exception {
        clock.set(2_000_000L);
        sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"displayName\":\"小貓\",\"issue\":126,\"branch\":\"ai/issue-126\",\"runStartedAt\":1000,"
                        + "\"agentRunning\":true,\"commit\":\"abcdef1\","
                        + "\"runs\":[{\"issue\":235,\"round\":0,\"branch\":\"ai/issue-235\",\"startedAt\":1000,"
                        + "\"worktree\":\"~/PunchClock-oki/wt-235\",\"commit\":\"abc1234\"}]}");

        HttpResponse<String> guest = send("GET", "/api/dev-machines", null);
        assertEquals(401, guest.statusCode());
        assertTrue(guest.body().contains("\"success\":false"));
        assertTrue(guest.body().contains("請先登入"));
        assertFalse(guest.body().contains("\"machines\""));
        assertFalse(guest.body().contains("\"wt-235\""));
        assertFalse(guest.body().contains("unit-test-dev-status-token"));

        HttpResponse<String> admin = send("GET", "/api/dev-machines", adminCookie);
        assertEquals(200, admin.statusCode());
        assertTrue(admin.body().contains("\"viewer\":\"admin\""));
        assertTrue(admin.body().contains("\"issueTitle\":\"測試 issue\""));
        assertTrue(admin.body().contains("小貓"));
    }

    @Test
    public void runsValidationAndLegacyPayload() throws Exception {
        String threeRuns = "{\"machine\":\"home\",\"runs\":["
                + "{\"issue\":235,\"round\":0,\"branch\":\"ai/issue-235\",\"startedAt\":2000,"
                + "\"worktree\":\"wt-235\",\"commit\":\"abcdef1\"},"
                + "{\"issue\":237,\"round\":2,\"branch\":\"ai/issue-237\",\"startedAt\":3000,"
                + "\"commit\":\"bcdef12\"},"
                + "{\"issue\":239,\"round\":1,\"branch\":\"ai/issue-239\",\"startedAt\":1000}]"
                + ",\"agentRunning\":true}";
        assertEquals(200, sendHeartbeat("Bearer " + TOKEN, threeRuns).statusCode());
        HttpResponse<String> list = send("GET", "/api/dev-machines", adminCookie);
        assertTrue(list.body().contains("\"round\":0"));
        assertTrue(list.body().contains("\"round\":2"));
        int idx239 = list.body().indexOf("\"issue\":239");
        int idx237 = list.body().indexOf("\"issue\":237");
        int idx235 = list.body().indexOf("\"issue\":235");
        assertTrue(idx239 < idx235);
        assertTrue(idx235 < idx237);

        assertEquals(400, sendHeartbeat("Bearer " + TOKEN, "{\"machine\":\"home\",\"runs\":\"x\"}").statusCode());

        StringBuilder many = new StringBuilder("{\"machine\":\"home\",\"runs\":[");
        for (int i = 1; i <= 13; i++) {
            if (i > 1) many.append(',');
            many.append("{\"issue\":").append(i).append(",\"round\":0,\"branch\":\"ai/issue-").append(i).append("\"}");
        }
        many.append("]}");
        assertEquals(400, sendHeartbeat("Bearer " + TOKEN, many.toString()).statusCode());

        assertEquals(400, sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"runs\":[{\"issue\":\"235\",\"round\":0,\"branch\":\"a\"}]}").statusCode());
        assertEquals(400, sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"runs\":[{\"issue\":0,\"round\":0,\"branch\":\"a\"}]}").statusCode());
        assertEquals(400, sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"runs\":[{\"issue\":1,\"round\":10,\"branch\":\"a\"}]}").statusCode());
        assertEquals(400, sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"runs\":[{\"issue\":1,\"round\":-1,\"branch\":\"a\"}]}").statusCode());
        assertEquals(400, sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"runs\":[{\"issue\":1,\"round\":1.5,\"branch\":\"a\"}]}").statusCode());
        assertEquals(400, sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"runs\":[{\"issue\":1,\"round\":0,\"branch\":\"bad branch\"}]}").statusCode());
        assertEquals(400, sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"runs\":[{\"issue\":1,\"round\":0,\"branch\":\"ai;x\"}]}").statusCode());
        assertEquals(400, sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"runs\":[{\"issue\":1,\"round\":0,\"branch\":\"ai/issue-1\","
                        + "\"worktree\":\"bad path\"}]}").statusCode());
        assertEquals(400, sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"runs\":[{\"issue\":1,\"round\":0,\"branch\":\"ai/issue-1\","
                        + "\"worktree\":\"$(touch)\"}]}").statusCode());
        assertEquals(400, sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"runs\":[{\"issue\":1,\"round\":0,\"branch\":\"ai/issue-1\","
                        + "\"commit\":\"nothex\"}]}").statusCode());

        HttpResponse<String> legacy = sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"office\",\"issue\":50,\"branch\":\"ai/issue-50\",\"runStartedAt\":500,"
                        + "\"agentRunning\":false}");
        assertEquals(200, legacy.statusCode());
        HttpResponse<String> body = send("GET", "/api/dev-machines", adminCookie);
        assertTrue(body.body().contains("\"runs\":[]"));
    }

    @Test
    public void offlineMachineOmitsRuns() throws Exception {
        sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"runs\":[{\"issue\":1,\"round\":0,\"branch\":\"ai/issue-1\"}],"
                        + "\"agentRunning\":true}");
        clock.set(2_000_000L + DevMachineStore.ONLINE_WINDOW_MS + 5);
        HttpResponse<String> list = send("GET", "/api/dev-machines", adminCookie);
        assertFalse(list.body().contains("\"runs\""));
    }

    @Test
    public void devStatusRedirectsToHomeWithoutMachineFields() throws Exception {
        HttpResponse<String> page = send("GET", "/dev-status", null);
        assertEquals(301, page.statusCode());
        assertEquals("/", page.headers().firstValue("location").orElse(""));
        assertFalse(page.body().contains("小貓"));
        assertFalse(page.body().contains("agentRunning"));
    }

    private HttpResponse<String> sendHeartbeat(String auth, String json) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + "/api/dev-machines/heartbeat"))
                .header("Authorization", auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if ("GET".equals(method)) {
            builder.GET();
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}