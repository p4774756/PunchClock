package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.auth.DevStatusTokenAuth;
import com.example.server.store.DevMachineStore;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusService;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DevStatusRoutesTest {

    private static final String TOKEN = "route-heartbeat-token";

    private DevMachineStore machineStore;
    private DevStatusService statusService;
    private Javalin app;
    private HttpClient http;
    private String base;

    @Before
    public void setUp() {
        machineStore = new DevMachineStore();
        statusService = new DevStatusService(repo -> githubWithQueued(427), "p4774756/PunchClock");
        app = Javalin.create(config -> config.showJavalinBanner = false);
        new DevStatusRoutes(statusService, machineStore).register(app);
        new DevMachineRoutes(new AuthService(), new DevStatusTokenAuth(TOKEN), machineStore, statusService, new Gson())
                .register(app);
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

    @Test
    public void devStatusMergesFreshHeartbeatRunIntoCurrent() throws Exception {
        long startedAt = System.currentTimeMillis();
        Map<String, Object> hb = new HashMap<>();
        hb.put("machine", "tiger");
        hb.put("agentRunning", true);
        hb.put("runs", List.of(Map.of(
                "issue", 427,
                "round", 1,
                "branch", "ai/issue-427",
                "startedAt", startedAt,
                "role", "dev"
        )));
        HttpResponse<String> beat = http.send(HttpRequest.newBuilder(URI.create(base + "/api/dev-machines/heartbeat"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(hb), StandardCharsets.UTF_8))
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, beat.statusCode());

        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/dev-status"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray current = body.getAsJsonObject("now").getAsJsonArray("current");
        assertEquals(1, current.size());
        assertEquals(427, current.get(0).getAsJsonObject().get("issue").getAsInt());
        assertEquals("開發中 第 1 輪", current.get(0).getAsJsonObject().get("status").getAsString());
        assertFalse(response.body().contains("worktree"));
        assertFalse(response.body().contains("model"));
    }

    private static DevStatusModels.Snapshot githubWithQueued(int issue) {
        DevStatusModels.IssueRow row = new DevStatusModels.IssueRow(
                issue, "測試單", true, 1L, 2L, null, List.of("queue:1"));
        return new DevStatusModels.Snapshot(
                Collections.singletonList(row), Collections.emptyList(), Collections.emptySet());
    }
}