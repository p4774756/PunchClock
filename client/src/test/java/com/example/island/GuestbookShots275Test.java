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
 * 手動／CI 驗收 #275 簽名簿截圖。
 * 需先啟動 server：PORT=9275，並設 {@code PUNCHCLOCK_BASE=http://127.0.0.1:9275}。
 * 可選 {@code PLAYWRIGHT_BROWSERS_PATH}；未安裝瀏覽器時略過。
 */
public class GuestbookShots275Test {

    @Test
    public void captureGuestbookScreens() throws Exception {
        String base = System.getenv("PUNCHCLOCK_BASE");
        Assume.assumeTrue(base != null && !base.isEmpty());
        String outDir = System.getenv("PUNCHCLOCK_SHOTS_DIR");
        if (outDir == null || outDir.isEmpty()) {
            outDir = System.getProperty("user.home") + "/PunchClock-oki/shots275-self/dev1";
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
                page.waitForTimeout(500);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("office-guestbook-prop-" + tag + ".png")));
                page.locator("[data-open-guestbook]").first().click();
                page.waitForSelector("#guestbookBoard:not([hidden])");
                page.waitForTimeout(400);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("guestbook-open-" + tag + ".png")));
                if ("desktop".equals(tag)) {
                    Page canvas = page;
                    var paint = canvas.locator("[data-guestbook-paint]");
                    if (paint.count() > 0) {
                        var box = paint.boundingBox();
                        if (box != null) {
                            canvas.mouse().move(box.x + 40, box.y + 30);
                            canvas.mouse().down();
                            canvas.mouse().move(box.x + 120, box.y + 80);
                            canvas.mouse().up();
                        }
                    }
                    page.locator("[data-guestbook-nickname]").fill("截圖測試");
                    page.locator("[data-guestbook-message]").fill("Playwright 驗收");
                    page.locator("[data-guestbook-submit]").click();
                    page.waitForTimeout(800);
                    page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("guestbook-doodle-sent-" + tag + ".png")));
                }
                page.close();
            }
            browser.close();
        }
    }
}