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
 * 手動／CI 驗收 #254 虛擬辦公室截圖。
 * 需先啟動 server：PORT=9254，並設 {@code PUNCHCLOCK_BASE=http://127.0.0.1:9254}。
 * 可選 {@code PUNCHCLOCK_SHOTS_DIR}、{@code PUNCHCLOCK_AUTH_COOKIE}（auth=…）。
 */
public class LabOfficeShots254Test {

    @Test
    public void captureLabOfficeEntryScreens() throws Exception {
        String base = System.getenv("PUNCHCLOCK_BASE");
        Assume.assumeTrue(base != null && !base.isEmpty());
        String outDir = System.getenv("PUNCHCLOCK_SHOTS_DIR");
        if (outDir == null || outDir.isEmpty()) {
            outDir = System.getProperty("user.home") + "/PunchClock-oki/shots254-self/dev1";
        }
        Path dir = Path.of(outDir);
        Files.createDirectories(dir);

        int[][] sizes = {{390, 844}, {1280, 720}};
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            for (int[] wh : sizes) {
                String tag = wh[0] < 500 ? "mobile" : "desktop";
                Page page = browser.newPage(new Browser.NewPageOptions().setViewportSize(wh[0], wh[1]));
                String cookie = System.getenv("PUNCHCLOCK_AUTH_COOKIE");
                if (cookie != null && !cookie.isEmpty()) {
                    String[] parts = cookie.split("=", 2);
                    if (parts.length == 2) {
                        page.context().addCookies(java.util.List.of(
                                new com.microsoft.playwright.options.Cookie(parts[0], parts[1])
                                        .setUrl(base)
                        ));
                    }
                }
                page.navigate(base + "/dev-status", new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("dev-status-" + tag + ".png")));
                var officeTab = page.locator("[data-dev-tab=\"office\"]");
                if (officeTab.count() > 0) {
                    officeTab.first().click();
                    page.waitForTimeout(800);
                    page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("dev-office-" + tag + ".png")));
                }
                page.navigate(base + "/", new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.evaluate("() => { const b = document.querySelector('[data-open-roadmap]'); if (b) b.click(); }");
                page.waitForTimeout(600);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("roadmap-" + tag + ".png")));
                page.close();
            }
            browser.close();
        }
    }
}