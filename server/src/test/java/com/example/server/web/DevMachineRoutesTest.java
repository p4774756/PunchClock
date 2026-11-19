package com.example.server.web;

import com.example.server.auth.AdminAuthCookie;
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
    private final AtomicLong clock = new AtomicLong(2_000_000L);

    @Before
    public void setUp() {
        DevStatusService fakeStatus = new DevStatusService(repo -> new DevStatusModels.Snapshot(
                Collections.singletonList(new DevStatusModels.IssueRow(
                        126, "測試 issue", true, 1L, 2L, null, Collections.emptyList())),
                Collections.emptyList(),
                Collections.emptySet()
        ), "p4774756/PunchClock");
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        DevMachineStore store = new DevMachineStore(clock::get);
        clock.set(2_000_000L);
        DevMachineRoutes routes = new DevMachineRoutes(
                new AuthService(),
                new DevStatusTokenAuth(TOKEN),
                store,
                fakeStatus,
                gson
        );
        app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        routes.register(app);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
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

        HttpResponse<String> badMachine = sendHeartbeat("Bearer " + TOKEN, "{\"machine\":\"other\"}");
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
    public void listRequiresAdminAndIncludesIssueTitle() throws Exception {
        clock.set(2_000_000L);
        sendHeartbeat("Bearer " + TOKEN,
                "{\"machine\":\"home\",\"issue\":126,\"branch\":\"ai/issue-126\",\"runStartedAt\":1000,"
                        + "\"agentRunning\":true,\"commit\":\"abcdef1\"}");

        HttpResponse<String> guest = send("GET", "/api/dev-machines", null);
        assertEquals(401, guest.statusCode());
        assertTrue(guest.body().contains("請先登入管理員"));
        assertFalse(guest.body().contains("commit"));
        assertFalse(guest.body().contains("小貓"));

        HttpResponse<String> admin = send("GET", "/api/dev-machines", AdminAuthCookie.header());
        assertEquals(200, admin.statusCode());
        assertTrue(admin.body().contains("\"commit\":\"abcdef1\""));
        assertTrue(admin.body().contains("\"issueTitle\":\"測試 issue\""));
        assertTrue(admin.body().contains("小貓"));
    }

    @Test
    public void devStatusPageAndPublicDevStatusExcludeMachineFields() throws Exception {
        HttpResponse<String> page = send("GET", "/dev-status", null);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("開發機狀態"));
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