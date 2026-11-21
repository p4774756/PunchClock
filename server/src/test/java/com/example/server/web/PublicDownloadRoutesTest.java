package com.example.server.web;

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

import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PublicDownloadRoutesTest {

    private static final String DIGEST =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private Javalin app;
    private HttpClient http;
    private String base;
    private Path jar;

    @Before
    public void setUp() throws Exception {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @After
    public void tearDown() throws Exception {
        if (app != null) {
            app.stop();
            app = null;
        }
        if (jar != null) {
            Files.deleteIfExists(jar);
            jar = null;
        }
    }

    @Test
    public void downloadPageShowsJarWhenReleaseAvailable() throws Exception {
        startWithRelease();
        HttpResponse<String> page = get("/download");
        assertEquals(200, page.statusCode());
        String body = page.body();
        assertTrue(body.contains("SKLbaby.jar"));
        assertTrue(body.contains("下載 SKLbaby.jar（v1.8.0）"));
        assertTrue(body.contains("Java 11"));
        assertFalse(body.contains("punchclock-client-standalone"));
        assertTrue(body.contains("data-download-missing hidden"));
        assertTrue(body.matches("(?s).*data-download-jar href=\"/download/app.jar\">.*"));

        HttpResponse<byte[]> jarResp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/download/app.jar"))
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, jarResp.statusCode());
        String disposition = jarResp.headers().firstValue("content-disposition").orElse("");
        assertTrue(disposition.contains("filename=\"SKLbaby.jar\""));
        assertFalse(disposition.contains("secret"));
    }

    @Test
    public void downloadPageShowsMissingWhenNoRelease() throws Exception {
        startWithoutRelease();
        HttpResponse<String> page = get("/download");
        assertEquals(200, page.statusCode());
        String body = page.body();
        assertTrue(body.contains("尚未附帶"));
        assertTrue(body.contains("data-download-jar href=\"/download/app.jar\" hidden"));
        assertTrue(body.contains("data-download-missing>"));
        assertFalse(body.contains("data-download-missing hidden"));
        assertTrue(body.contains("java -jar SKLbaby.jar"));
        assertTrue(body.contains("下載 SKLbaby.jar（v）"));

        HttpResponse<String> jarResp = get("/download/app.jar");
        assertEquals(404, jarResp.statusCode());

        HttpResponse<String> css = get("/css/download.css");
        assertEquals(200, css.statusCode());
        String flat = css.body().replaceAll("\\s+", " ");
        assertTrue(flat.contains(
                ".download-btn[hidden], .download-unavailable[hidden] { display: none !important; }"));
    }

    @Test
    public void noReleaseHidesDownloadButtonByComputedStyle() throws Exception {
        Assume.assumeTrue("Playwright headless browser not available", chromiumAvailable());
        startWithoutRelease();
        assertDownloadVisibility(false);
    }

    @Test
    public void availableReleaseHidesMissingMessageByComputedStyle() throws Exception {
        Assume.assumeTrue("Playwright headless browser not available", chromiumAvailable());
        startWithRelease();
        assertDownloadVisibility(true);
    }

    private static Boolean chromiumReady;

    /** 無瀏覽器時略過；不寫死瀏覽器路徑，沿用 PLAYWRIGHT_BROWSERS_PATH。 */
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

    /**
     * HTML 上的 hidden 會被 .download-btn { display: inline-block } 蓋掉，
     * 所以要看 computed style 與 innerText，不能只搜字串 hidden。
     */
    private void assertDownloadVisibility(boolean available) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            try {
                int[][] sizes = {{1280, 900}, {390, 844}};
                for (int[] wh : sizes) {
                    Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(wh[0], wh[1]));
                    try {
                        page.navigate(base + "/download", new Page.NavigateOptions()
                                .setWaitUntil(WaitUntilState.LOAD));
                        int clientWidth = ((Number) page.evaluate(
                                "() => document.documentElement.clientWidth")).intValue();
                        assertEquals(wh[0], clientWidth);
                        String text = page.locator("body").innerText();
                        String btnDisplay = String.valueOf(page.locator("[data-download-jar]")
                                .evaluate("el => getComputedStyle(el).display"));
                        String missingDisplay = String.valueOf(page.locator("[data-download-missing]")
                                .evaluate("el => getComputedStyle(el).display"));
                        boolean btnHiddenAttr = Boolean.TRUE.equals(page.locator("[data-download-jar]")
                                .evaluate("el => el.hasAttribute('hidden')"));
                        boolean missingHiddenAttr = Boolean.TRUE.equals(page.locator("[data-download-missing]")
                                .evaluate("el => el.hasAttribute('hidden')"));
                        assertTrue(text.contains("Java 11"));
                        assertTrue(text.contains("java -jar SKLbaby.jar"));
                        if (available) {
                            assertFalse(btnHiddenAttr);
                            assertTrue(missingHiddenAttr);
                            assertEquals("inline-block", btnDisplay);
                            assertEquals("none", missingDisplay);
                            assertTrue(page.locator("[data-download-jar]").isVisible());
                            assertTrue(page.locator("[data-download-missing]").isHidden());
                            assertTrue(text.contains("下載 SKLbaby.jar（v1.8.0）"));
                            assertFalse(text.contains("尚未附帶"));
                            assertNotNull(page.locator("[data-download-jar]").boundingBox());
                            assertNull(page.locator("[data-download-missing]").boundingBox());
                        } else {
                            assertTrue(btnHiddenAttr);
                            assertFalse(missingHiddenAttr);
                            assertEquals("none", btnDisplay);
                            assertFalse("none".equals(missingDisplay));
                            assertTrue(page.locator("[data-download-jar]").isHidden());
                            assertTrue(page.locator("[data-download-missing]").isVisible());
                            assertFalse(text.contains("下載 SKLbaby.jar"));
                            assertTrue(text.contains("伺服器尚未附帶可下載的桌面端，請向管理員索取。"));
                            assertNull(page.locator("[data-download-jar]").boundingBox());
                            BoundingBox missingBox = page.locator("[data-download-missing]").boundingBox();
                            assertNotNull(missingBox);
                            assertTrue(missingBox.width > 0);
                            assertTrue(missingBox.height > 0);
                        }
                    } finally {
                        page.close();
                    }
                }
            } finally {
                browser.close();
            }
        }
    }

    private void startWithRelease() throws Exception {
        jar = Files.createTempFile("public-download", ".jar");
        writeJar(jar);
        ClientRelease release = ClientRelease.fromJar(jar);
        DevStatusService devStatus = emptyDevStatus();
        app = new ServerApp(release, devStatus).start(0);
        base = "http://127.0.0.1:" + app.port();
    }

    private void startWithoutRelease() {
        DevStatusService devStatus = emptyDevStatus();
        app = new ServerApp(ClientRelease.NONE, devStatus).start(0);
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

    private static void writeJar(Path path) throws Exception {
        Properties props = new Properties();
        props.setProperty("version", "1.8.0");
        props.setProperty("sourceDigest", DIGEST);
        props.setProperty("buildTime", Instant.parse("2026-10-20T07:58:00Z").toString());
        try (OutputStream out = Files.newOutputStream(path);
             JarOutputStream jos = new JarOutputStream(out)) {
            jos.putNextEntry(new JarEntry("version.properties"));
            props.store(jos, "");
            jos.closeEntry();
            jos.putNextEntry(new JarEntry("release-notes.txt"));
            jos.write("note\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
    }
}