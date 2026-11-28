package com.example.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ServerAppBuildInfoTest {

    private static final String TOKEN = "punchclock-dev-secret";

    private AdminTestServer server;
    private HttpClient http;
    private String base;
    private String adminCookie;

    @Before
    public void setUp() throws Exception {
        server = AdminTestServer.builder().start();
        base = server.baseUrl();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        adminCookie = server.loginCookie(http);
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
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
                        .header("Cookie", adminCookie)
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, status.statusCode());
        JsonObject payload = JsonParser.parseString(status.body()).getAsJsonObject();
        assertNotNull(Instant.parse(payload.get("serverBuildTime").getAsString()));
        JsonObject client = payload.getAsJsonArray("clients").get(0).getAsJsonObject();
        assertEquals("2026-09-24T07:58:00Z", client.get("appReleaseTime").getAsString());
    }

    @Test
    public void officeHomeAndConsoleHtmlShareReplacedServerBuildTokens() throws Exception {
        HttpResponse<String> home = http.send(
                HttpRequest.newBuilder(URI.create(base + "/")).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> console = http.send(
                HttpRequest.newBuilder(URI.create(base + "/console")).GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, home.statusCode());
        assertEquals(302, console.statusCode());
        assertEquals("/?open=tasks", console.headers().firstValue("location").orElse(""));

        String homeBody = home.body();
        assertFalse(homeBody.contains("{{SERVER_VERSION}}"));
        assertFalse(homeBody.contains("{{SERVER_BUILD_TIME}}"));
        assertFalse(homeBody.contains("{{SERVER_ASSET_QUERY}}"));

        assertTrue(homeBody.contains("PUNCHCLOCK_SERVER_VERSION = \""));
        assertTrue(homeBody.contains("PUNCHCLOCK_SERVER_BUILD_TIME = \""));
        assertTrue(homeBody.contains("/css/private-team.css?v="));
        assertTrue(homeBody.contains("/js/office-page.js?v="));
        assertEquals("202611281231", ServerApp.compactAssetBuildStamp("2026-11-28 12:31"));
        assertEquals("4.0.0.202611281231", "4.0.0" + "." + ServerApp.compactAssetBuildStamp("2026-11-28 12:31"));
        assertEquals("", ServerApp.compactAssetBuildStamp("開發版"));
        assertFalse(homeBody.contains("{{AUTH_JS_BOOL}}"));
        assertFalse(homeBody.contains("{{AUTH_USERNAME}}"));
    }

}
