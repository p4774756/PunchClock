package com.example.server;

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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerAppClientUpdateTest {

    private static final String TOKEN = "punchclock-dev-secret";
    private static final String SHA =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String DOWNLOAD =
            "https://github.com/p4774756/PunchClock/releases/download/v1.8.0/SKLbaby.jar";

    private Javalin app;
    private HttpClient http;
    private String base;
    private ClientRelease release;

    @Before
    public void setUp() throws Exception {
        release = new ClientRelease("1.8.0", "2026-10-20T07:58:00Z", SHA, 12_345L, DOWNLOAD,
                List.of("聊天視窗只晃一下", "比較舊的修改"));
        app = new ServerApp(release).start(0);
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
    public void heartbeatOffersUpdateOnlyWhenVersionIsNewer() throws Exception {
        JsonObject newer = heartbeat("1.7.0");
        assertTrue(newer.has("update"));
        JsonObject update = newer.getAsJsonObject("update");
        assertEquals("1.8.0", update.get("version").getAsString());
        assertEquals("2026-10-20T07:58:00Z", update.get("releaseTime").getAsString());
        assertEquals(SHA, update.get("sha256").getAsString());
        assertEquals(release.size(), update.get("size").getAsLong());
        assertEquals(DOWNLOAD, update.get("path").getAsString());

        JsonObject same = heartbeat("1.8.0");
        assertFalse(same.has("update"));

        JsonObject newerClient = heartbeat("1.9.0");
        assertFalse(newerClient.has("update"));

        JsonObject dev = heartbeat("dev");
        assertFalse(dev.has("update"));
    }

    @Test
    public void heartbeatListsReleaseBodyLines() throws Exception {
        JsonObject hb = heartbeat("1.7.0");
        JsonArray changes = hb.getAsJsonObject("update").getAsJsonArray("changes");
        assertEquals(2, changes.size());
        assertEquals("聊天視窗只晃一下", changes.get(0).getAsString());
    }

    @Test
    public void clientReleaseJarRouteRemoved() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/client-release/jar"))
                        .header("Authorization", "Bearer " + TOKEN)
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, resp.statusCode());
    }

    private JsonObject heartbeat(String version) throws Exception {
        String json = "{\"clientId\":\"worker-update\",\"status\":\"ONLINE\",\"tasks\":[],\"heartbeatSeq\":1,"
                + "\"appVersion\":\"" + version + "\",\"appReleaseTime\":\"\",\"appSourceDigest\":\"\"}";
        HttpResponse<String> hb = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/heartbeat"))
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, hb.statusCode());
        return JsonParser.parseString(hb.body()).getAsJsonObject();
    }
}