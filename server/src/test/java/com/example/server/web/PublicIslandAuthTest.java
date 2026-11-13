package com.example.server.web;

import com.example.server.ServerApp;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Phase 1：公開島嶼可逛，管理 API／WS 仍需登入。 */
public class PublicIslandAuthTest {

    private Javalin app;
    private HttpClient http;
    private String base;

    @Before
    public void setUp() {
        app = new ServerApp().start(0);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    public void guestsCanOpenPublicIslandHome() throws Exception {
        HttpResponse<String> response = send("GET", "/", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("id=\"islandCanvas\""));
        assertTrue(response.body().contains("is-guest"));
        assertTrue(response.body().contains("PUNCHCLOCK_AUTHENTICATED = false"));
        assertTrue(response.body().contains("登入後可操作"));
        assertTrue(response.body().contains("href=\"/login\""));
        assertFalse(response.body().contains("is-authenticated"));
    }

    @Test
    public void indexHtmlRedirectsToPublicHome() throws Exception {
        HttpResponse<String> response = send("GET", "/index.html", null);
        assertEquals(302, response.statusCode());
        assertEquals("/", response.headers().firstValue("location").orElse(""));
    }

    @Test
    public void signedInAdminSeesAuthenticatedIsland() throws Exception {
        String cookie = adminCookie();
        HttpResponse<String> response = send("GET", "/", cookie);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("is-authenticated"));
        assertTrue(response.body().contains("PUNCHCLOCK_AUTHENTICATED = true"));
        assertTrue(response.body().contains("href=\"/logout\""));
        assertFalse(response.body().contains("is-guest"));
    }

    @Test
    public void guestsAreDeniedManagementApis() throws Exception {
        assertUnauthorized(send("GET", "/api/status", null));
        assertUnauthorized(send("GET", "/api/health/history", null));
        assertUnauthorized(send("GET", "/api/photos", null));
        assertUnauthorized(send("GET", "/api/marquee", null));
        assertUnauthorized(send("POST", "/api/clients/demo/cancel-schedule", null));
        assertUnauthorized(send("DELETE", "/api/clients/demo", null));
        assertUnauthorized(send("PUT", "/api/marquee", null, "{\"enabled\":false,\"text\":\"\"}"));
    }

    @Test
    public void signedInAdminCanReadStatusAndLogoutRevokesAccess() throws Exception {
        String cookie = adminCookie();
        HttpResponse<String> status = send("GET", "/api/status", cookie);
        assertEquals(200, status.statusCode());
        assertTrue(status.body().contains("\"serverVersion\""));
        assertTrue(status.body().contains("\"clients\""));

        HttpResponse<String> logout = send("GET", "/logout", cookie);
        assertEquals(302, logout.statusCode());
        assertEquals("/", logout.headers().firstValue("location").orElse(""));

        // 登出只清瀏覽器 Cookie；未帶 Cookie 的請求應回到訪客權限
        assertUnauthorized(send("GET", "/api/status", null));
        HttpResponse<String> home = send("GET", "/", null);
        assertEquals(200, home.statusCode());
        assertTrue(home.body().contains("is-guest"));
    }

    @Test
    public void dashboardWebSocketRejectsGuests() throws Exception {
        CompletableFuture<Integer> closed = new CompletableFuture<>();
        CompletableFuture<String> leaked = new CompletableFuture<>();
        URI uri = URI.create(base.replace("http://", "ws://") + "/ws/dashboard");
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        leaked.complete(data.toString());
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        closed.complete(statusCode);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        closed.completeExceptionally(error);
                    }
                })
                .get(5, TimeUnit.SECONDS);

        Integer code = closed.get(5, TimeUnit.SECONDS);
        assertTrue("guest dashboard ws should close", code != null);
        assertFalse("guest must not receive dashboard snapshot", leaked.isDone());
    }

    private void assertUnauthorized(HttpResponse<String> response) {
        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("未登入或權限不足")
                || response.body().contains("Unauthorized")
                || response.body().contains("\"success\":false"));
    }

    private HttpResponse<String> send(String method, String path, String cookie) throws Exception {
        return send(method, path, cookie, null);
    }

    private HttpResponse<String> send(String method, String path, String cookie, String jsonBody) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else if ("PUT".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.PUT(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "{}" : jsonBody));
        } else {
            throw new IllegalArgumentException(method);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String adminCookie() throws Exception {
        HttpResponse<String> login = http.send(
                HttpRequest.newBuilder(URI.create(base + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("password=secret"))
                        .timeout(Duration.ofSeconds(5))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        String setCookie = login.headers().firstValue("set-cookie").orElse("");
        int start = setCookie.indexOf("auth=");
        assertTrue("login should set auth cookie", start >= 0);
        int end = setCookie.indexOf(';', start);
        return end > start ? setCookie.substring(start, end) : setCookie.substring(start);
    }
}
