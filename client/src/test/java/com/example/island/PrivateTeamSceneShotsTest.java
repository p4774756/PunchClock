package com.example.island;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 驗收首頁私人團隊 3D 一角。需先啟動 server，並設 {@code PUNCHCLOCK_BASE} 與 {@code PUNCHCLOCK_SHOTS_DIR}。
 */
public class PrivateTeamSceneShotsTest {

    private static void applyStaleDayPref(Page page) {
        page.evaluate(
                "() => { try { localStorage.setItem('punchclock-private-team-day', 'day'); } catch (e) {} }");
    }

    private static void shot(
            Playwright pw,
            String base,
            Path dir,
            String engine,
            int w,
            int h,
            String filename,
            boolean staleDayPref) {
        BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions().setHeadless(true);
        Browser browser;
        try {
            if ("webkit".equals(engine)) {
                browser = pw.webkit().launch(launch);
            } else {
                browser = pw.chromium().launch(launch);
            }
        } catch (Exception ex) {
            Assume.assumeNoException(engine + " unavailable", ex);
            return;
        }
        Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(w, h));
        if (staleDayPref) {
            applyStaleDayPref(page);
        }
        page.navigate(base + "/", new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
        page.waitForSelector(".private-team-hud");
        page.waitForSelector("[data-private-team-canvas]");
        Assume.assumeTrue(
                "日夜按鈕應已移除",
                page.querySelector("[data-pt-daynight]") == null);
        page.waitForTimeout(2200);
        page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve(filename)));
        page.close();
        browser.close();
    }

    @Test
    public void capturePrivateTeamNightSuite() throws Exception {
        String base = System.getenv("PUNCHCLOCK_BASE");
        Assume.assumeTrue("1".equals(System.getenv("PUNCHCLOCK_SHOTS_DAYNIGHT")));
        Assume.assumeTrue(base != null && !base.isEmpty());
        String outDir = System.getenv("PUNCHCLOCK_SHOTS_DIR");
        Assume.assumeTrue(outDir != null && !outDir.isEmpty());
        Path dir = Path.of(outDir);
        Files.createDirectories(dir);

        try (Playwright pw = Playwright.create()) {
            shot(pw, base, dir, "chromium", 1280, 800, "desktop-night-1280x800.png", false);
            shot(pw, base, dir, "chromium", 390, 844, "mobile-night-390x844.png", false);
            shot(pw, base, dir, "chromium", 1280, 800, "desktop-stale-day-pref-1280x800.png", true);
            shot(pw, base, dir, "chromium", 390, 844, "mobile-stale-day-pref-390x844.png", true);
            try {
                shot(pw, base, dir, "webkit", 390, 844, "mobile-night-390x844-webkit.png", false);
            } catch (Exception ex) {
                Assume.assumeNoException("Playwright webkit unavailable", ex);
            }
        }
    }

    @Test
    public void capturePrivateTeamScreens() throws Exception {
        String base = System.getenv("PUNCHCLOCK_BASE");
        Assume.assumeTrue(base != null && !base.isEmpty());
        String outDir = System.getenv("PUNCHCLOCK_SHOTS_DIR");
        Assume.assumeTrue(outDir != null && !outDir.isEmpty());
        Path dir = Path.of(outDir);
        Files.createDirectories(dir);

        try (Playwright pw = Playwright.create()) {
            Browser browser;
            try {
                browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            } catch (Exception ex) {
                Assume.assumeNoException("Playwright chromium unavailable", ex);
                return;
            }
            int[][] sizes = {{1280, 800}, {390, 844}};
            for (int[] wh : sizes) {
                String tag = wh[0] < 500 ? "mobile" : "desktop";
                Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(wh[0], wh[1]));
                page.navigate(base + "/", new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                page.waitForSelector(".private-team-hud");
                page.waitForSelector("[data-private-team-canvas]");
                Assume.assumeTrue(
                        "日夜按鈕應已移除",
                        page.querySelector("[data-pt-daynight]") == null);
                page.waitForTimeout(1500);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("private-team-" + tag + ".png")));
                page.close();
            }
            browser.close();
        }
    }
}
