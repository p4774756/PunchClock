package com.example.server.web;

import com.example.server.ClientRelease;
import com.example.server.ServerApp;
import com.example.server.auth.AdminAuthCookie;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusService;
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
import java.util.Collections;
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
        // 假 Fetcher：立刻回固定樣本，不打真實 GitHub（避免 5～11 秒逾時）。
        DevStatusService fakeDevStatus = new DevStatusService(repo -> new DevStatusModels.Snapshot(
                Collections.singletonList(new DevStatusModels.IssueRow(
                        50, "測試票", true, 1_000L, 2_000L, null,
                        Collections.singletonList("queue:1"))),
                Collections.emptyList(),
                Collections.emptySet()
        ), "p4774756/PunchClock");
        app = new ServerApp(ClientRelease.NONE, fakeDevStatus).start(0);
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
        assertTrue(response.body().contains("id=\"graffitiBoard\""));
        assertTrue(response.body().contains("data-open-graffiti"));
        assertTrue(response.body().contains("id=\"roadmapBoard\""));
        assertTrue(response.body().contains("data-open-roadmap"));
        assertTrue(response.body().contains("施工進度"));
        assertTrue(response.body().contains("塗鴉牆"));
        assertTrue(response.body().contains("返回島嶼"));
        assertTrue(response.body().contains("練習籌碼"));
        assertTrue(response.body().contains("不是真錢"));
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
        assertTrue(island.body().contains("from './island-layout.js'"));
        assertTrue(island.body().contains("from './island-casino.js'"));
        assertTrue(island.body().contains("from './island-milu.js'"));
        assertTrue(island.body().contains("from './island-quality.js'"));
        assertTrue(island.body().contains("from './island-graffiti.js'"));
        assertTrue(island.body().contains("from './island-roadmap.js'"));

        HttpResponse<String> logic = send("GET", "/js/island-explore.js", null);
        assertEquals(200, logic.statusCode());
        assertTrue(logic.body().contains("export function canStand"));
        assertTrue(logic.body().contains("export function exploreState"));
        assertTrue(logic.body().contains("export const CASINO"));

        HttpResponse<String> layout = send("GET", "/js/island-layout.js", null);
        assertEquals(200, layout.statusCode());
        assertTrue(layout.body().contains("博弈島"));
        assertTrue(layout.body().contains("中醫推拿島"));
        assertTrue(layout.body().contains("電腦島"));

        HttpResponse<String> casino = send("GET", "/js/island-casino.js", null);
        assertEquals(200, casino.statusCode());
        assertTrue(casino.body().contains("export function mountCasinoLobby"));
        assertTrue(casino.body().contains("export function createCasinoController"));
        assertTrue(casino.body().contains("export function chipWalletView"));
        assertTrue(casino.body().contains("island-blackjack.js"));

        HttpResponse<String> blackjack = send("GET", "/js/island-blackjack.js", null);
        assertEquals(200, blackjack.statusCode());
        assertTrue(blackjack.body().contains("export function handValue"));

        HttpResponse<String> css = send("GET", "/css/casino.css", null);
        assertEquals(200, css.statusCode());
        assertTrue(css.body().contains(".casino-lobby"));

        HttpResponse<String> graffitiJs = send("GET", "/js/island-graffiti.js", null);
        assertEquals(200, graffitiJs.statusCode());
        assertTrue(graffitiJs.body().contains("export function normalizeSignature"));

        HttpResponse<String> graffitiCss = send("GET", "/css/graffiti.css", null);
        assertEquals(200, graffitiCss.statusCode());
        assertTrue(graffitiCss.body().contains("touch-action: none"));

        HttpResponse<String> roadmapJs = send("GET", "/js/island-roadmap.js", null);
        assertEquals(200, roadmapJs.statusCode());
        assertTrue(roadmapJs.body().contains("export function signCopy"));

        HttpResponse<String> roadmapCss = send("GET", "/css/roadmap.css", null);
        assertEquals(200, roadmapCss.statusCode());
        assertTrue(roadmapCss.body().contains(".roadmap-board"));

        HttpResponse<String> miluJs = send("GET", "/js/island-milu.js", null);
        assertEquals(200, miluJs.statusCode());
        assertTrue(miluJs.body().contains("export function miluStep"));

        HttpRequest spriteReq = HttpRequest.newBuilder(URI.create(base + "/pet/milu/walk/left_01.png"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<byte[]> sprite = http.send(spriteReq, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, sprite.statusCode());
        assertTrue(sprite.headers().firstValue("content-type").orElse("").contains("image/png"));
        byte[] png = sprite.body();
        assertTrue(png.length > 8);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);
    }

    @Test
    public void islandStaticAssetsGetDayCache() throws Exception {
        HttpResponse<String> js = send("GET", "/js/island-quality.js", null);
        assertEquals(200, js.statusCode());
        assertTrue(js.headers().firstValue("cache-control").orElse("").contains("no-cache"));

        HttpResponse<String> css = send("GET", "/css/roadmap.css", null);
        assertEquals(200, css.statusCode());
        assertTrue(css.headers().firstValue("cache-control").orElse("").contains("no-cache"));

        HttpResponse<String> pet = send("GET", "/pet/milu/walk/left_01.png", null);
        assertEquals(200, pet.statusCode());
        assertTrue(pet.headers().firstValue("cache-control").orElse("").contains("max-age=86400"));
    }

    @Test
    public void dashboardHtmlStaysShortCache() throws Exception {
        HttpResponse<String> home = send("GET", "/", null);
        assertEquals(200, home.statusCode());
        String cache = home.headers().firstValue("cache-control").orElse("");
        assertTrue(cache.contains("no-cache") || cache.contains("must-revalidate"));
    }

    @Test
    public void guestsCanReadAndSubmitGraffitiWithoutLogin() throws Exception {
        HttpResponse<String> list = send("GET", "/api/graffiti", null);
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("\"items\""));
        assertNoManagementPayload(list.body());

        HttpRequest post = HttpRequest.newBuilder(URI.create(base + "/api/graffiti"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("not-a-png", StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> rejected = http.send(post, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(400, rejected.statusCode());
        assertTrue(rejected.body().contains("PNG"));
        assertFalse(rejected.body().contains("未登入"));

        HttpResponse<String> denied = send("DELETE", "/api/graffiti/missingid", null);
        assertEquals(401, denied.statusCode());
        assertTrue(denied.body().contains("未登入或權限不足"));
    }

    @Test
    public void guestsCanReadRoadmapWithoutLogin() throws Exception {
        HttpResponse<String> roadmap = send("GET", "/api/roadmap", null);
        assertEquals(200, roadmap.statusCode());
        assertTrue(roadmap.body().contains("\"success\""));
        assertTrue(roadmap.body().contains("\"phases\""));
        assertTrue(roadmap.body().contains("\"stale\""));
        assertFalse(roadmap.body().contains("未登入"));
        assertFalse(roadmap.body().contains("**Phase"));
        assertFalse(roadmap.body().contains("## 分期"));
    }

    @Test
    public void guestsCanReadDevStatusWithoutLogin() throws Exception {
        HttpResponse<String> status = send("GET", "/api/dev-status", null);
        assertEquals(200, status.statusCode());
        String body = status.body();
        assertTrue(body.contains("\"success\""));
        assertTrue(body.contains("\"stale\""));
        assertTrue(body.contains("\"now\""));
        assertTrue(body.contains("\"items\""));
        assertTrue(body.contains("\"timeline\""));
        assertTrue(body.contains("\"stats\""));
        assertFalse(body.contains("未登入"));
        assertFalse(body.contains("Authorization"));
        assertFalse(body.contains("PUNCHCLOCK_GITHUB_TOKEN"));
        assertFalse(body.contains("## "));
        assertFalse(body.contains("Closes #"));
        assertFalse(body.contains("agentRunning"));
        assertFalse(body.contains("lastSeen"));
        assertFalse(body.contains("battery"));
    }

    @Test
    public void guestsCanReadTeaHealthWithoutSensitiveFields() throws Exception {
        HttpResponse<String> response = send("GET", "/api/tea-health", null);
        assertEquals(200, response.statusCode());
        String body = response.body();
        assertTrue(body.contains("\"success\""));
        assertTrue(body.contains("heapUsedBytes") || body.contains("processCpuPercent"));
        assertFalse(body.contains("\"clients\""));
        assertFalse(body.contains("diskPath"));
        assertFalse(body.contains("osName"));
        assertFalse(body.contains("osArch"));
        assertFalse(body.contains("relayActiveCount"));
        assertFalse(body.contains("未登入"));
    }

    @Test
    public void guestsCannotReadDevMachines() throws Exception {
        HttpResponse<String> status = send("GET", "/api/dev-machines", null);
        assertEquals(401, status.statusCode());
        assertTrue(status.body().contains("請先登入管理員"));
        assertFalse(status.body().contains("家裡筆電"));
        assertFalse(status.body().contains("commit"));
    }

    @Test
    public void devStatusPageIsPublicShellWithoutMachineData() throws Exception {
        HttpResponse<String> page = send("GET", "/dev-status", null);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("開發機狀態"));
        assertFalse(page.body().contains("家裡筆電"));
        assertFalse(page.body().contains("agentRunning"));
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
