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
 * 驗收首頁私人團隊 3D 一角。需先啟動 server 並設 {@code PUNCHCLOCK_BASE}。
 */
public class PrivateTeamShots334Test {

    @Test
    public void capturePrivateTeamScreens() throws Exception {
        String base = System.getenv("PUNCHCLOCK_BASE");
        Assume.assumeTrue(base != null && !base.isEmpty());
        String outDir = System.getenv("PUNCHCLOCK_SHOTS_DIR");
        if (outDir == null || outDir.isEmpty()) {
            outDir = System.getProperty("user.home") + "/PunchClock-oki/shots334-self/dev1";
        }
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
            int[][] sizes = {{1280, 720}, {390, 844}};
            for (int[] wh : sizes) {
                String tag = wh[0] < 500 ? "mobile" : "desktop";
                Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(wh[0], wh[1]));
                page.navigate(base + "/", new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                page.waitForSelector(".private-team-hud");
                page.waitForSelector("[data-private-team-canvas]");
                page.waitForTimeout(1500);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("private-team-" + tag + ".png")));
                page.close();
            }
            browser.close();
        }
    }
}