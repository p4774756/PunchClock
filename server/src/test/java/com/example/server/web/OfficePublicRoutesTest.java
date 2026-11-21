package com.example.server.web;

import com.example.server.auth.DevStatusTokenAuth;
import com.example.server.store.DevMachineStore;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusService;
import com.google.gson.Gson;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OfficePublicRoutesTest {

    private static final Pattern ISSUE_HASH = Pattern.compile("#\\d+");
    private static final Set<String> ALLOWED_TOP = Set.of("success", "characters");
    private static final Set<String> ALLOWED_CHAR = Set.of("status", "updatedAt");
    private static final Pattern ALLOWED_STATUS = Pattern.compile("^(工作中( ×\\d+)?|審核中( ×\\d+)?|待機|佇列 \\d+)$");

    private Javalin app;
    private HttpClient http;
    private String base;
    private final DevMachineStore store = new DevMachineStore(() -> 9_000_000L);

    @Before
    public void setUp() {
        DevStatusService status = new DevStatusService(repo -> new DevStatusModels.Snapshot(
                Collections.emptyList(), Collections.emptyList(), Collections.emptySet()));
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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("machine", "tiger");
        body.put("agentRunning", true);
        body.put("issue", 257);
        body.put("model", "composer-2.5");
        body.put("branch", "ai/issue-257");
        body.put("worktree", "/Users/tina/secret");
        store.applyHeartbeat("tiger", body);
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    public void publicOfficeWhitelistWithoutSecrets() throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(base + "/api/office/public"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
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
                assertTrue("unexpected char field: " + key, ALLOWED_CHAR.contains(key));
            }
            String status = row.get("status").getAsString();
            assertTrue("status: " + status, ALLOWED_STATUS.matcher(status).matches());
            String blob = row.toString();
            assertFalse(blob, ISSUE_HASH.matcher(blob).find());
            assertFalse(blob, blob.contains("composer"));
            assertFalse(blob, blob.contains("grok"));
            assertFalse(blob, blob.contains("claude"));
            assertFalse(blob, blob.contains("/Users/"));
            assertFalse(blob, blob.contains("office"));
            assertFalse(blob, blob.contains("branch"));
        }
    }
}