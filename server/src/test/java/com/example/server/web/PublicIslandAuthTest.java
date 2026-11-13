package com.example.server.web;

import com.example.server.ServerApp;
import com.example.server.auth.AdminAuthCookie;
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
        assertTrue(response.body().contains("id=\"islandExplore\""));
        assertTrue(response.body().contains("id=\"islandJoystick\""));
        assertTrue(response.body().contains("id=\"casinoLobby\""));
        assertTrue(response.body().contains("id=\"islandInteract\""));
        assertTrue(response.body().contains("data-open-casino"));
        assertTrue(response.body().contains("返回島嶼"));
        assertTrue(response.body().contains("尚未啟用"));
        assertTrue(response.body().contains("is-guest"));
        assertTrue(response.body().contains("PUNCHCLOCK_AUTHENTICATED = false"));
        assertTrue(response.body().contains("登入後可操作"));
        assertTrue(response.body().contains("href=\"/login\""));
        assertFalse(response.body().contains("is-authenticated"));
    }

    @Test
    public void guestsCanLoadIslandExploreModule() throws Exception {
        HttpResponse<String> island = send("GET", "/js/island.js", null);
        assertEquals(200, island.statusCode());
        assertTrue(island.body().contains("from './island-explore.js'"));
        assertTrue(island.body().contains("from './island-casino.js'"));

        HttpResponse<String> logic = send("GET", "/js/island-explore.js", null);
        assertEquals(200, logic.statusCode());
        assertTrue(logic.body().contains("export function canStand"));
        assertTrue(logic.body().contains("export function exploreState"));
        assertTrue(logic.body().contains("export const CASINO"));

        HttpResponse<String> casino = send("GET", "/js/island-casino.js", null);
        assertEquals(200, casino.statusCode());
        assertTrue(casino.body().contains("export function mountCasinoLobby"));
        assertTrue(casino.body().contains("export function chipWalletView"));

        HttpResponse<String> css = send("GET", "/css/casino.css", null);
        assertEquals(200, css.statusCode());
        assertTrue(css.body().contains(".casino-lobby"));
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
        assertDeniedManagement(send("GET", "/api/status", null));
        assertDeniedManagement(send("GET", "/api/health/history", null));
        assertDeniedManagement(send("GET", "/api/photos", null));
        assertDeniedManagement(send("GET", "/api/photos/demo/file", null));
        assertDeniedManagement(send("GET", "/api/photos/demo/preview", null));
        assertDeniedManagement(send("DELETE", "/api/photos/demo", null));
        assertDeniedManagement(send("DELETE", "/api/photos", null));
        assertDeniedManagement(send("POST", "/api/photos", null));
        assertDeniedManagement(send("GET", "/api/marquee", null));
        assertDeniedManagement(send("PUT", "/api/marquee", null, "{\"enabled\":false,\"text\":\"\"}"));
        assertDeniedManagement(send("POST", "/api/clients/demo/cancel-schedule", null));
        assertDeniedManagement(send("POST", "/api/clients/demo/cancel-task/t-1", null));
        assertDeniedManagement(send("DELETE", "/api/clients/demo", null));
    }

    @Test
    public void forgedAuthCookieIsDeniedManagementApis() throws Exception {
        assertDeniedManagement(send("GET", "/api/status", "auth=forged-not-a-real-session"));
        assertDeniedManagement(send("GET", "/api/photos", "auth=forged-not-a-real-session"));
        assertDeniedManagement(send("GET", "/api/marquee", "auth=forged-not-a-real-session"));
        assertDeniedManagement(send("GET", "/api/health/history", "auth=forged-not-a-real-session"));
    }

    @Test
    public void signedInAdminCanReadStatusAndLogoutRevokesAccess() throws Exception {
        String cookie = adminCookie();
        HttpResponse<String> status = send("GET", "/api/status", cookie);
        assertEquals(200, status.statusCode());
        assertTrue(status.body().contains("\"serverVersion\""));
        assertTrue(status.body().contains("\"clients\""));

        HttpResponse<String> photos = send("GET", "/api/photos", cookie);
        assertEquals(200, photos.statusCode());

        HttpResponse<String> marquee = send("GET", "/api/marquee", cookie);
        assertEquals(200, marquee.statusCode());
        assertTrue(marquee.body().contains("\"enabled\""));

        HttpResponse<String> logout = send("GET", "/logout", cookie);
        assertEquals(302, logout.statusCode());
        assertEquals("/", logout.headers().firstValue("location").orElse(""));

        // 登出只清瀏覽器 Cookie；未帶 Cookie 的請求應回到訪客權限
        assertDeniedManagement(send("GET", "/api/status", null));
        assertDeniedManagement(send("GET", "/api/photos", null));
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

    private void assertDeniedManagement(HttpResponse<String> response) {
        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("未登入或權限不足")
                || response.body().contains("Unauthorized")
                || response.body().contains("\"success\":false"));
        assertNoManagementPayload(response.body());
    }

    private void assertNoManagementPayload(String body) {
        assertFalse("must not leak client roster", body.contains("\"clients\""));
        assertFalse("must not leak serverVersion status", body.contains("\"serverVersion\""));
        assertFalse("must not leak health samples", body.contains("\"samples\""));
        assertFalse("must not leak photo list payload", body.contains("\"photos\""));
        assertFalse("must not leak marquee text field as admin payload", body.contains("\"maxChars\""));
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

    /** 與目前程序 ADMIN_PASSWORD 設定對齊的 auth Cookie；不經 /login、不碰明文密碼。 */
    private String adminCookie() {
        return AdminAuthCookie.header();
    }
}
