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
 * 驗收 #296 簽名簿外觀截圖。
 * 需先啟動 server；設 {@code PUNCHCLOCK_BASE} 與可選 {@code PUNCHCLOCK_SHOTS_DIR}。
 */
public class GuestbookShots296Test {

    @Test
    public void captureGuestbookScreens() throws Exception {
        String base = System.getenv("PUNCHCLOCK_BASE");
        Assume.assumeTrue(base != null && !base.isEmpty());
        String outDir = System.getenv("PUNCHCLOCK_SHOTS_DIR");
        if (outDir == null || outDir.isEmpty()) {
            outDir = System.getProperty("user.home") + "/PunchClock-oki/shots296-self/dev1";
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
                page.navigate(base + "/?open=guestbook", new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
                page.waitForSelector("#guestbookBoard:not([hidden])");
                page.waitForTimeout(500);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("guestbook-open-" + tag + ".png")));
                page.close();
            }
            browser.close();
        }
    }
}