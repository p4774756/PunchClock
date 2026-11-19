package com.example.server.web;

import com.example.server.auth.AdminAuthCookie;
import com.example.server.auth.AuthService;
import com.example.server.store.MeridianQuestStore;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MeridianQuestRoutesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Javalin app;
    private HttpClient http;
    private String base;

    @Before
    public void setUp() throws Exception {
        MeridianQuestStore store = new MeridianQuestStore(folder.newFile("quest.json").toPath());
        app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        new MeridianQuestRoutes(new AuthService(), store).register(app);
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
    public void guestsReceive401() throws Exception {
        HttpResponse<String> get = send("GET", "/api/meridian-quest/progress", null);
        assertEquals(401, get.statusCode());
        HttpResponse<String> put = send("PUT", "/api/meridian-quest/progress", "{\"version\":1,\"stages\":{}}");
        assertEquals(401, put.statusCode());
    }

    @Test
    public void authGetPutAndRejectOversize() throws Exception {
        String body = "{\"version\":1,\"stages\":{\"LU\":{\"stars\":2,\"bestScore\":800,\"bestTimeMs\":60000,\"clearedAt\":\"2024-01-01\"}}}";
        HttpResponse<String> put = send("PUT", "/api/meridian-quest/progress", body, AdminAuthCookie.header());
        assertEquals(200, put.statusCode());
        assertTrue(put.body().contains("\"stars\":2"));
        assertTrue(!put.body().contains("\"stars\":2.0"));

        HttpResponse<String> get = send("GET", "/api/meridian-quest/progress", null, AdminAuthCookie.header());
        assertEquals(200, get.statusCode());
        assertTrue(get.body().contains("\"progress\""));

        char[] big = new char[9_000];
        java.util.Arrays.fill(big, 'x');
        HttpResponse<String> bad = send("PUT", "/api/meridian-quest/progress", new String(big), AdminAuthCookie.header());
        assertEquals(400, bad.statusCode());

        String regress = "{\"version\":1,\"stages\":{\"LU\":{\"stars\":3,\"bestScore\":500,\"bestTimeMs\":90000,\"clearedAt\":\"2024-01-01\"}}}";
        HttpResponse<String> putLower = send("PUT", "/api/meridian-quest/progress", regress, AdminAuthCookie.header());
        assertEquals(200, putLower.statusCode());
        HttpResponse<String> getAfter = send("GET", "/api/meridian-quest/progress", null, AdminAuthCookie.header());
        assertEquals(200, getAfter.statusCode());
        assertTrue(getAfter.body().contains("\"bestScore\":800"));
        assertTrue(getAfter.body().contains("\"stars\":3"));
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return send(method, path, body, null);
    }

    private HttpResponse<String> send(String method, String path, String body, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5))
                .header("accept", "application/json");
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if ("PUT".equals(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
            builder.header("content-type", "application/json");
        } else {
            builder.GET();
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}