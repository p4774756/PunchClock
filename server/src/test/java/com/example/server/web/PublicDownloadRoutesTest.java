package com.example.server.web;

import com.example.ClientUpdatePolicy;
import com.example.server.ClientRelease;
import com.example.server.ServerApp;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusService;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitUntilState;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PublicDownloadRoutesTest {

    private static final String SHA =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DOWNLOAD =
            "https://github.com/p4774756/PunchClock/releases/download/v1.8.0/SKLbaby.jar";
    private static final long JAR_SIZE_BYTES = 220_200_960L;

    private Javalin app;
    private HttpClient http;
    private String base;

    @Before
    public void setUp() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    @Test
    public void formatClientDownloadSizeMb_usesMbIntegerOrOneDecimal() {
        assertEquals("", PublicDownloadRoutes.formatClientDownloadSizeMb(0));
        assertEquals("210 MB", PublicDownloadRoutes.formatClientDownloadSizeMb(JAR_SIZE_BYTES));
        assertEquals("0.1 MB", PublicDownloadRoutes.formatClientDownloadSizeMb(102_400L));
        assertEquals("1.5 MB", PublicDownloadRoutes.formatClientDownloadSizeMb(1_572_864L));
    }

    @Test
    public void applyHomeDownloadTokens_hidesSizeWhenReleaseUnavailable() {
        String tpl = "<span{{HOME_SIZE_HIDDEN}}>約 {{HOME_CLIENT_SIZE}}</span>";
        String out = PublicDownloadRoutes.applyHomeDownloadTokens(tpl, ClientRelease.NONE);
        assertTrue(out.contains(" hidden"));
        assertTrue(out.contains("約 "));
        assertFalse(out.contains("{{HOME"));
    }

    @Test
    public void applyHomeDownloadTokens_showsSizeWhenReleaseAvailable() {
        ClientRelease release = new ClientRelease("1.8.0", "", SHA, JAR_SIZE_BYTES, DOWNLOAD, List.of());
        String tpl = "<span{{HOME_SIZE_HIDDEN}}>約 {{HOME_CLIENT_SIZE}}</span>";
        String out = PublicDownloadRoutes.applyHomeDownloadTokens(tpl, release);
        assertTrue(out.contains("約 210 MB"));
        assertFalse(out.contains(" hidden"));
    }

    @Test
    public void downloadPageLinksToGithubWhenReleaseAvailable() throws Exception {
        startWithRelease();
        HttpResponse<String> page = get("/download");
        assertEquals(200, page.statusCode());
        String body = page.body();
        assertTrue(body.contains("下載小寶貝"));
        assertTrue(body.contains("SKLbaby.jar"));
        assertTrue(body.contains(">下載 SKLbaby.jar<"));
        assertTrue(body.contains("約 210 MB"));
        assertTrue(body.contains("目前版本：v1.8.0"));
        assertFalse(body.contains("Mac（Apple 晶片"));
        assertFalse(body.contains("安裝檔（macOS"));
        assertTrue(body.contains(DOWNLOAD));
        assertTrue(body.contains("data-download-missing hidden"));
        assertFalse(body.contains("punchclock-client-standalone"));
    }

    @Test
    public void downloadJarRedirectsToGithub() throws Exception {
        startWithRelease();
        HttpResponse<Void> jarResp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/download/app.jar"))
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(302, jarResp.statusCode());
        assertEquals(DOWNLOAD, jarResp.headers().firstValue("Location").orElse(""));
    }

    @Test
    public void downloadJarFallsBackToLatestWhenNoRelease() throws Exception {
        startWithoutRelease();
        HttpResponse<Void> jarResp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/download/SKLbaby.jar"))
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(302, jarResp.statusCode());
        assertEquals(ClientUpdatePolicy.GITHUB_LATEST_JAR_URL, jarResp.headers().firstValue("Location").orElse(""));
    }

    @Test
    public void downloadPageShowsLatestLinkWithoutRelease() throws Exception {
        startWithoutRelease();
        HttpResponse<String> page = get("/download");
        assertEquals(200, page.statusCode());
        String body = page.body();
        assertTrue(body.contains(ClientUpdatePolicy.GITHUB_LATEST_JAR_URL));
        assertTrue(body.contains("data-download-missing hidden"));
        assertTrue(body.contains(">下載 SKLbaby.jar<"));
        assertTrue(body.contains("需要已安裝"));
        assertTrue(body.contains("Java 11"));
        assertTrue(body.contains("class=\"download-size\" hidden"));
    }

    @Test
    public void availableReleaseHidesMissingMessageByComputedStyle() throws Exception {
        Assume.assumeTrue("Playwright headless browser not available", chromiumAvailable());
        startWithRelease();
        assertDownloadVisibility(true);
    }

    private static Boolean chromiumReady;

    private static boolean chromiumAvailable() {
        if (chromiumReady != null) {
            return chromiumReady;
        }
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            browser.close();
            chromiumReady = true;
        } catch (Throwable ex) {
            chromiumReady = false;
        }
        return chromiumReady;
    }

    private void assertDownloadVisibility(boolean available) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            try {
                Page page = browser.newPage();
                try {
                    page.navigate(base + "/download", new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.LOAD));
                    String text = page.locator("body").innerText();
                    boolean missingHiddenAttr = Boolean.TRUE.equals(page.locator("[data-download-missing]")
                            .evaluate("el => el.hasAttribute('hidden')"));
                    assertTrue(missingHiddenAttr);
                    assertTrue(page.locator("[data-download-jar]").isVisible());
                    if (available) {
                        assertTrue(text.contains("下載 SKLbaby.jar"));
                        assertTrue(text.contains("目前版本：v1.8.0"));
                        assertNotNull(page.locator("[data-download-jar]").boundingBox());
                    }
                    assertNull(page.locator("[data-download-missing]").boundingBox());
                } finally {
                    page.close();
                }
            } finally {
                browser.close();
            }
        }
    }

    private void startWithRelease() {
        ClientRelease release = new ClientRelease("1.8.0", "2026-10-20T07:58:00Z", SHA, JAR_SIZE_BYTES, DOWNLOAD, List.of());
        app = new ServerApp(release, emptyDevStatus()).start(0);
        base = "http://127.0.0.1:" + app.port();
    }

    private void startWithoutRelease() {
        app = new ServerApp(ClientRelease.NONE, emptyDevStatus()).start(0);
        base = "http://127.0.0.1:" + app.port();
    }

    private static DevStatusService emptyDevStatus() {
        return new DevStatusService(repo -> new DevStatusModels.Snapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptySet()
        ), "p4774756/PunchClock");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}