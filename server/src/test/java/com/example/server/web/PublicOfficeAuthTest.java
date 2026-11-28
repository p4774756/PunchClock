package com.example.server.web;

import com.example.server.AdminTestServer;
import com.example.server.ClientRelease;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusService;
import com.example.server.store.RoadmapService;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 首頁辦公室公開、管理 API／WS 仍需登入。 */
public class PublicOfficeAuthTest {


    private AdminTestServer server;
    private HttpClient http;
    private String base;
    private String adminCookie;
    private final AtomicInteger roadmapFetches = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        DevStatusService fakeDevStatus = new DevStatusService(repo -> new DevStatusModels.Snapshot(
                Collections.singletonList(new DevStatusModels.IssueRow(
                        50, "測試票", true, 1_000L, 2_000L, null,
                        Collections.singletonList("queue:1"))),
                Collections.emptyList(),
                Collections.emptySet()
        ), "p4774756/PunchClock");
        RoadmapService fakeRoadmap = new RoadmapService(repo -> {
            roadmapFetches.incrementAndGet();
            return new com.example.server.store.RoadmapModels.Document(
                    4,
                    java.util.Collections.singletonList(new com.example.server.store.RoadmapModels.Phase(
                            4, "測試期", false,
                            java.util.Collections.singletonList(new com.example.server.store.RoadmapModels.Item("子項", false, 1, null)),
                            0, 1, null)),
                    null);
        }, "p4774756/PunchClock");
        server = AdminTestServer.builder()
                .clientRelease(ClientRelease.NONE)
                .devStatusService(fakeDevStatus)
                .roadmapService(fakeRoadmap)
                .start();
        base = server.baseUrl();
        http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        adminCookie = server.loginCookie(http);
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void guestsCanOpenOfficeHome() throws Exception {
        HttpResponse<String> response = send("GET", "/", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("lab-office-page"));
        assertTrue(response.body().contains("office-page.js"));
        assertFalse(response.body().contains("id=\"roadmapBoard\""));
        assertFalse(response.body().contains("id=\"newsBoard\""));
        assertTrue(response.body().contains("loginBoard"));
        assertTrue(response.body().contains("data-login-form"));
        assertFalse(response.body().contains("/css/roadmap.css"));
        assertFalse(response.body().contains("/css/login.css"));
        assertFalse(response.body().contains("/css/download.css"));
        assertTrue(response.body().contains("is-guest"));
        assertTrue(response.body().contains("PUNCHCLOCK_AUTHENTICATED = false"));
        assertFalse(response.body().toLowerCase().contains("island"));
        assertFalse(response.body().contains("神之島"));
        assertTrue(response.body().contains("tasksBoard"));
        assertFalse(response.body().contains("dashboard.js"));
        assertFalse(response.body().contains("dashboard.css"));
    }

    @Test
    public void publicOfficeEndpointWithoutLogin() throws Exception {
        HttpResponse<String> response = send("GET", "/api/office/public", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"characters\""));
        assertTrue(response.body().contains("\"oki\""));
        assertFalse(response.body().contains("#"));
    }

    @Test
    public void graffitiAndRoadmapPanelsLoadOnConsole() throws Exception {
        HttpResponse<String> graffitiJs = send("GET", "/js/graffiti-panel.js", null);
        assertEquals(200, graffitiJs.statusCode());
        assertTrue(graffitiJs.body().contains("createGraffitiController"));
        HttpResponse<String> roadmapJs = send("GET", "/js/roadmap-panel.js", null);
        assertEquals(200, roadmapJs.statusCode());
        assertTrue(roadmapJs.body().contains("createRoadmapController"));
    }

    @Test
    public void dashboardHtmlStaysShortCache() throws Exception {
        HttpResponse<String> home = send("GET", "/", null);
        assertEquals(200, home.statusCode());
        String cache = home.headers().firstValue("cache-control").orElse("");
        assertTrue(cache.contains("no-cache") || cache.contains("must-revalidate"));
    }

    @Test
    public void unversionedJsAndCssMustRevalidate() throws Exception {
        HttpResponse<String> js = send("GET", "/js/office-page.js", null);
        assertEquals(200, js.statusCode());
        String jsCache = js.headers().firstValue("cache-control").orElse("");
        assertTrue(jsCache.contains("no-cache") || jsCache.contains("must-revalidate"));
        assertFalse(jsCache.contains("max-age=86400"));
        HttpResponse<String> css = send("GET", "/css/lab-office.css", null);
        assertEquals(200, css.statusCode());
        String cssCache = css.headers().firstValue("cache-control").orElse("");
        assertTrue(cssCache.contains("no-cache") || cssCache.contains("must-revalidate"));
        assertFalse(cssCache.contains("max-age=86400"));
    }

    @Test
    public void versionedJsModuleGraphAllowsDayCache() throws Exception {
        HttpResponse<String> home = send("GET", "/", null);
        assertEquals(200, home.statusCode());
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("src=\"(/js/v-[^/]+/office-page\\.js)\"")
                .matcher(home.body());
        assertTrue(home.body(), m.find());
        String versioned = m.group(1);
        HttpResponse<String> entry = send("GET", versioned, null);
        assertEquals(200, entry.statusCode());
        assertTrue(entry.headers().firstValue("cache-control").orElse("").contains("max-age=86400"));
        assertTrue(entry.body().contains("mountPrivateTeamPage"));
        String scenePath = versioned.replace("/office-page.js", "/private-team-scene.js");
        HttpResponse<String> scene = send("GET", scenePath, null);
        assertEquals(200, scene.statusCode());
        assertTrue(scene.headers().firstValue("cache-control").orElse("").contains("max-age=86400"));
        assertTrue(scene.body().contains("mountPrivateTeamScene"));
    }

    @Test
    public void guestsCanReadAndSubmitGraffitiWithoutLogin() throws Exception {
        HttpResponse<String> list = send("GET", "/api/graffiti", null);
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("\"items\""));
        assertNoManagementPayload(list.body());
    }

    @Test
    public void guestsCanReadRoadmapWithoutLogin() throws Exception {
        roadmapFetches.set(0);
        HttpResponse<String> roadmap = send("GET", "/api/roadmap", null);
        assertEquals(200, roadmap.statusCode());
        assertTrue(roadmap.body().contains("\"success\""));
        assertFalse(roadmap.body().contains("未登入或權限不足"));
        assertEquals("路線圖應只走記憶體假資料", 1, roadmapFetches.get());
    }

    @Test
    public void guestsCanReadDevStatusWithoutLogin() throws Exception {
        HttpResponse<String> status = send("GET", "/api/dev-status", null);
        assertEquals(200, status.statusCode());
        assertFalse(status.body().contains("未登入"));
        assertFalse(status.body().contains("agentRunning"));
    }

    @Test
    public void guestsAreDeniedDevMachinesApi() throws Exception {
        HttpResponse<String> status = send("GET", "/api/dev-machines", null);
        assertEquals(401, status.statusCode());
        assertTrue(status.body().contains("請先登入"));
    }

    @Test
    public void consoleRedirectsToHomeTasks() throws Exception {
        HttpResponse<String> page = send("GET", "/console", adminCookie);
        assertEquals(302, page.statusCode());
        assertEquals("/?open=tasks", page.headers().firstValue("location").orElse(""));
        HttpResponse<String> home = send("GET", "/", adminCookie);
        assertEquals(200, home.statusCode());
        assertFalse(home.body().contains("dashboard.js"));
        assertTrue(home.body().contains("office-page.js"));
        assertTrue(home.body().contains("clientContainer"));
        assertTrue(home.body().contains("panel-devices"));
        assertTrue(home.body().contains("tasksBoard"));
        assertTrue(home.body().contains("data-tasks-close"));
    }

    @Test
    public void downloadPageIsPublic() throws Exception {
        HttpResponse<String> page = send("GET", "/download", null);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Java"));
        assertFalse(page.body().contains("secret"));
        assertFalse(page.body().contains("Bearer"));
    }

    @Test
    public void loginNextRedirectsWhenAlreadyAuthed() throws Exception {
        HttpResponse<String> response = send("GET", "/login?next=/?open=roadmap", adminCookie);
        assertEquals(302, response.statusCode());
        assertEquals("/?open=roadmap", response.headers().firstValue("location").orElse(""));
        HttpResponse<String> tasksNext = send("GET", "/login?next=/?open=tasks", adminCookie);
        assertEquals(302, tasksNext.statusCode());
        assertEquals("/?open=tasks", tasksNext.headers().firstValue("location").orElse(""));
        HttpResponse<String> console = send("GET", "/login?next=/console", adminCookie);
        assertEquals(302, console.statusCode());
        assertEquals("/console", console.headers().firstValue("location").orElse(""));
    }

    @Test
    public void loginFormKeepsConsoleNext() throws Exception {
        HttpResponse<String> page = send("GET", "/login?next=/console", null);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("name=\"next\" value=\"/console\""));
        assertTrue(page.body().contains("辦公室"));
        assertFalse(page.body().contains("神之島"));
    }

    @Test
    public void atlasPathsAreNotServedAndLoginNextRejected() throws Exception {
        HttpResponse<String> atlasPage = send("GET", "/atlas/tcm-3d/", null);
        assertEquals(404, atlasPage.statusCode());
        HttpResponse<String> atlasRoot = send("GET", "/atlas", null);
        assertEquals(404, atlasRoot.statusCode());
        HttpResponse<String> login = send("GET", "/login?next=/atlas/tcm-3d/", null);
        assertEquals(200, login.statusCode());
        assertFalse(login.body().contains("value=\"/atlas/tcm-3d/\""));
        HttpResponse<String> authed = send("GET", "/login?next=/atlas/tcm-3d/", adminCookie);
        assertEquals(302, authed.statusCode());
        assertEquals("/", authed.headers().firstValue("location").orElse(""));
    }

    @Test
    public void lockedLoginWarningHasNoEmoji() throws Exception {
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> posted = postLogin("not-the-admin-password");
            assertEquals(302, posted.statusCode());
        }
        HttpResponse<String> page = send("GET", "/login", null);
        assertEquals(200, page.statusCode());
        String body = page.body();
        assertTrue(body.contains("[警告] 密碼錯誤"));
        assertFalse(body.contains("id=\"countdown\""));
        assertFalse(body.contains("冷卻鎖定中"));
        assertTrue(body.contains("id=\"password\""));
        assertTrue(body.contains("disabled"));
        assertTrue(body.contains("aria-label=\"回辦公室\""));
        assertTrue(body.contains("roadmap-close-x"));
        assertFalse(body.contains("← 回辦公室"));
        assertTrue(body.contains("私人女秘書"));
        assertFalse(body.contains("管理主控台"));
        assertFalse(body.contains("PunchClock ·"));
        assertFalse(body.contains("⛔"));
        assertTrue(body.contains(">登入</button>"));
        assertFalse(containsEmoji(body));
    }

    @Test
    public void wrongPasswordAlertHasNoEmoji() throws Exception {
        HttpResponse<String> posted = postLogin("not-the-admin-password");
        assertEquals(302, posted.statusCode());
        HttpResponse<String> page = send("GET", "/login?error=1", null);
        assertEquals(200, page.statusCode());
        String body = page.body();
        assertTrue(body.contains("[警告] 密碼錯誤"));
        assertFalse(body.contains("剩餘嘗試次數"));
        assertTrue(body.contains(">登入</button>"));
        assertTrue(body.contains("aria-label=\"回辦公室\""));
        assertTrue(body.contains("roadmap-close-x"));
        assertFalse(body.contains("← 回辦公室"));
        assertFalse(body.contains("⛔"));
        assertFalse(body.contains("冷卻鎖定中"));
        assertFalse(containsEmoji(body));
    }

    @Test
    public void indexHtmlRedirectsToPublicHome() throws Exception {
        HttpResponse<String> response = send("GET", "/index.html", null);
        assertEquals(302, response.statusCode());
        assertEquals("/", response.headers().firstValue("location").orElse(""));
    }

    @Test
    public void signedInAdminSeesAuthenticatedHome() throws Exception {
        String cookie = adminCookie;
        HttpResponse<String> response = send("GET", "/", cookie);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("is-authenticated"));
        assertTrue(response.body().contains("PUNCHCLOCK_AUTHENTICATED = true"));
        assertTrue(response.body().contains("href=\"/logout\""));
        String expectedCsrf = server.csrfHeaderFromCookie(cookie);
        assertTrue(response.body().contains("PUNCHCLOCK_CSRF = \"" + expectedCsrf + "\""));
    }

    @Test
    public void guestsAreDeniedManagementApis() throws Exception {
        assertDeniedManagement(send("GET", "/api/status", null));
        assertDeniedManagement(send("POST", "/api/clients/demo/cancel-schedule", null));
    }

    @Test
    public void signedInAdminCanReadStatusAndLogoutRevokesAccess() throws Exception {
        String cookie = adminCookie;
        HttpResponse<String> status = send("GET", "/api/status", cookie);
        assertEquals(200, status.statusCode());
        assertTrue(status.body().contains("\"clients\""));

        String csrf = server.csrfHeaderFromCookie(cookie);
        HttpResponse<String> logout = sendPostLogout(cookie, csrf);
        assertEquals(302, logout.statusCode());
        assertDeniedManagement(send("GET", "/api/status", cookie));
    }

    @Test
    public void dashboardWebSocketRejectsGuests() throws Exception {
        CompletableFuture<Integer> closed = new CompletableFuture<>();
        URI uri = URI.create(base.replace("http://", "ws://") + "/ws/dashboard");
        http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, new WebSocket.Listener() {
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
        assertTrue("guest dashboard ws should close", closed.get(5, TimeUnit.SECONDS) != null);
    }

    private void assertDeniedManagement(HttpResponse<String> response) {
        assertEquals(401, response.statusCode());
        assertNoManagementPayload(response.body());
    }

    private void assertNoManagementPayload(String body) {
        assertFalse(body.contains("\"serverVersion\""));
        assertFalse(body.contains("\"photos\""));
    }

    @Test
    public void loginPostJsonSuccessSetsCookieWithoutRedirect() throws Exception {
        String form = "password=" + java.net.URLEncoder.encode(server.adminPassword(), StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"success\":true"));
        String setCookie = response.headers().firstValue("Set-Cookie").orElse("");
        assertTrue(setCookie.startsWith("auth="));
        assertFalse(response.headers().firstValue("location").isPresent());
        String cookie = setCookie.split(";", 2)[0];
        String expectedCsrf = server.csrfHeaderFromCookie(cookie);
        assertTrue(response.body().contains("\"csrfToken\":\"" + expectedCsrf + "\""));
        HttpRequest write = HttpRequest.newBuilder(URI.create(base + "/api/clients/x/cancel-schedule"))
                .timeout(Duration.ofSeconds(5))
                .header("Cookie", cookie)
                .header(com.example.server.auth.CsrfTokens.HEADER, expectedCsrf)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> cancelled = http.send(write, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, cancelled.statusCode());
    }

    @Test
    public void loginPostJsonWrongPasswordReturnsMessage() throws Exception {
        HttpResponse<String> response = postLoginJson("not-the-admin-password");
        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("\"success\":false"));
        assertTrue(response.body().contains("密碼錯誤"));
        assertFalse(response.body().contains("remainingAttempts"));
        assertFalse(response.headers().firstValue("location").isPresent());
    }

    @Test
    public void loginPostJsonLockedAfterFiveFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> fail = postLoginJson("not-the-admin-password");
            assertEquals(i < 4 ? 401 : 403, fail.statusCode());
        }
        HttpResponse<String> locked = postLoginJson("not-the-admin-password");
        assertEquals(403, locked.statusCode());
        assertTrue(locked.body().contains("\"locked\":true"));
        assertFalse(locked.body().contains("remainingSec"));
        assertTrue(locked.body().contains("密碼錯誤"));
    }

    @Test
    public void loginPostFormStillRedirectsOnFailure() throws Exception {
        HttpResponse<String> response = postLogin("not-the-admin-password");
        assertEquals(302, response.statusCode());
        assertEquals("/login?error=1", response.headers().firstValue("location").orElse(""));
    }

    private HttpResponse<String> postLoginJson(String password) throws Exception {
        String form = "password=" + java.net.URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> postLogin(String password) throws Exception {
        String form = "password=" + java.net.URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 登入頁允許箭頭與省略號，其餘表情符號不得出現。 */
    private static boolean containsEmoji(String text) {
        return text.codePoints().anyMatch(cp ->
                (cp >= 0x1F300 && cp <= 0x1FAFF)
                        || (cp >= 0x2600 && cp <= 0x27BF)
                        || cp == 0xFE0F);
    }

    @Test
    public void guestSessionEndpointDoesNotIssueCsrf() throws Exception {
        HttpResponse<String> response = send("GET", "/api/auth/session", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"authenticated\":false"));
        assertFalse(response.body().contains("csrfToken"));
    }

    @Test
    public void signedInSessionEndpointReturnsCsrf() throws Exception {
        HttpResponse<String> response = send("GET", "/api/auth/session", adminCookie);
        assertEquals(200, response.statusCode());
        String expectedCsrf = server.csrfHeaderFromCookie(adminCookie);
        assertTrue(response.body().contains("\"authenticated\":true"));
        assertTrue(response.body().contains("\"username\":\"root\""));
        assertTrue(response.body().contains("\"csrfToken\":\"" + expectedCsrf + "\""));
    }

    @Test
    public void postLogoutJsonClearsSessionWhenCsrfValid() throws Exception {
        HttpResponse<String> logout = sendPostLogoutJson(adminCookie, server.csrfHeaderFromCookie(adminCookie));
        assertEquals(200, logout.statusCode());
        assertTrue(logout.body().contains("\"success\":true"));
        assertDeniedManagement(send("GET", "/api/status", adminCookie));
    }

    private HttpResponse<String> sendPostLogoutJson(String cookie, String csrf) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/logout"))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .header(com.example.server.auth.CsrfTokens.HEADER, csrf)
                .POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPostLogout(String cookie, String csrf) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/logout"))
                .timeout(Duration.ofSeconds(5))
                .header(com.example.server.auth.CsrfTokens.HEADER, csrf)
                .POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> send(String method, String path, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            throw new IllegalArgumentException(method);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}