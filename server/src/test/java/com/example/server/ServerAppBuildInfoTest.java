package com.example.server;

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
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ServerAppBuildInfoTest {

    private static final String TOKEN = "punchclock-dev-secret";

    private Javalin app;
    private HttpClient http;
    private String base;

    @Before
    public void setUp() {
        app = new ServerApp().start(0);
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
    public void pingReportsVersionAndBuildTimeFromPom() throws Exception {
        HttpResponse<String> ping = http.send(
                HttpRequest.newBuilder(URI.create(base + "/ping")).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ping.statusCode());
        JsonObject body = JsonParser.parseString(ping.body()).getAsJsonObject();
        assertTrue(body.get("version").getAsString().matches("\\d+\\.\\d+\\.\\d+"));
        assertNotNull(Instant.parse(body.get("buildTime").getAsString()));
    }

    @Test
    public void heartbeatAppReleaseTimeIsShownInStatus() throws Exception {
        String json = "{\"clientId\":\"worker-a\",\"status\":\"ONLINE\",\"tasks\":[],\"heartbeatSeq\":1,"
                + "\"appVersion\":\"1.8.0\",\"appReleaseTime\":\"2026-09-24T07:58:00Z\"}";
        HttpResponse<String> hb = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/heartbeat"))
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, hb.statusCode());

        HttpResponse<String> status = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/status"))
                        .header("Cookie", adminCookie())
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, status.statusCode());
        JsonObject payload = JsonParser.parseString(status.body()).getAsJsonObject();
        assertNotNull(Instant.parse(payload.get("serverBuildTime").getAsString()));
        JsonObject client = payload.getAsJsonArray("clients").get(0).getAsJsonObject();
        assertEquals("2026-09-24T07:58:00Z", client.get("appReleaseTime").getAsString());
    }

    private String adminCookie() throws Exception {
        HttpResponse<String> login = http.send(
                HttpRequest.newBuilder(URI.create(base + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("password=secret"))
                        .timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        String setCookie = login.headers().firstValue("set-cookie").orElse("");
        int start = setCookie.indexOf("auth=");
        assertTrue("login should set auth cookie", start >= 0);
        int end = setCookie.indexOf(';', start);
        return end > start ? setCookie.substring(start, end) : setCookie.substring(start);
    }
}
