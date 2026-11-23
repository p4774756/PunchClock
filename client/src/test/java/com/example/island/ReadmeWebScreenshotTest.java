package com.example.island;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Assume;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * README 用網站截圖。需 {@code PUNCHCLOCK_BASE}、{@code README_SHOTS_DIR}；
 * 可選 {@code README_ADMIN_PASSWORD}（預設 secret）登入主控台。
 */
public class ReadmeWebScreenshotTest {

    @Test
    public void captureWebReadmeShots() throws Exception {
        String base = System.getenv("PUNCHCLOCK_BASE");
        String dir = System.getenv("README_SHOTS_DIR");
        Assume.assumeTrue(base != null && !base.isBlank());
        Assume.assumeTrue(dir != null && !dir.isBlank());
        Path root = Path.of(dir, "web");
        Files.createDirectories(root);

        String password = System.getenv("README_ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            password = "secret";
        }
        String cookie = loginCookie(base, password);

        int[][] sizes = {{1280, 720}, {390, 844}};
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            for (int[] wh : sizes) {
                String tag = wh[0] < 500 ? "mobile" : "desktop";
                shot(browser, wh[0], wh[1], base + "/", root.resolve("office-" + tag + ".jpg"), false, 2500);
                shot(browser, wh[0], wh[1], base + "/download", root.resolve("download-" + tag + ".jpg"), false, 400);
                shotLogin(browser, wh[0], wh[1], base, root.resolve("login-" + tag + ".jpg"));
                shotAtlas(browser, wh[0], wh[1], base, root.resolve("atlas-" + tag + ".jpg"));
            }

            Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(1280, 720));
            if (cookie != null) {
                String[] parts = cookie.split("=", 2);
                if (parts.length == 2) {
                    page.context().addCookies(List.of(new Cookie(parts[0], parts[1]).setUrl(base)));
                }
            }
            Assume.assumeTrue("admin login failed", cookie != null);
            page.navigate(base + "/console#devices",
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            page.waitForTimeout(1200);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(root.resolve("console-tasks-desktop.jpg"))
                    .setType(ScreenshotType.JPEG)
                    .setQuality(88)
                    .setFullPage(false));
            page.evaluate("() => { const b = document.querySelector('[data-open-roadmap]'); if (b) b.click(); }");
            page.waitForTimeout(1200);
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(root.resolve("roadmap-desktop.jpg"))
                    .setType(ScreenshotType.JPEG)
                    .setQuality(88)
                    .setFullPage(false));
            page.close();
            browser.close();
        }
    }

    private static void shotLogin(Browser browser, int w, int h, String base, Path file) {
        Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(w, h));
        page.navigate(base + "/login", new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForSelector("input[type='password']", new Page.WaitForSelectorOptions().setTimeout(15_000));
        page.waitForTimeout(300);
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(file)
                .setType(ScreenshotType.JPEG)
                .setQuality(88)
                .setFullPage(false));
        page.close();
    }

    private static void shotAtlas(Browser browser, int w, int h, String base, Path file) {
        Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(w, h));
        page.navigate(base + "/atlas/tcm-3d/?set=common-10",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForTimeout(w < 500 ? 5000 : 6000);
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(file)
                .setType(ScreenshotType.JPEG)
                .setQuality(88)
                .setFullPage(false));
        page.close();
    }

    private static void shot(Browser browser, int w, int h, String url, Path file, boolean auth, int waitMs) {
        Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(w, h));
        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        page.waitForTimeout(waitMs);
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(file)
                .setType(ScreenshotType.JPEG)
                .setQuality(88)
                .setFullPage(false));
        page.close();
    }

    private static String loginCookie(String base, String password) throws Exception {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        String body = "password=" + java.net.URLEncoder.encode(password, StandardCharsets.UTF_8)
                + "&next=%2Fconsole";
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(base + "/login"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        for (String header : response.headers().allValues("Set-Cookie")) {
            if (header.startsWith("auth=")) {
                return header.split(";", 2)[0];
            }
        }
        return null;
    }
}