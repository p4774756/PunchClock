package com.example.island;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 驗收施工進度彈窗分頁與 ?open=roadmap 深連結。
 * 需先啟動 server，並設 {@code PUNCHCLOCK_BASE}（例：http://127.0.0.1:9292）。
 * 可選 {@code PUNCHCLOCK_SHOTS_DIR}。
 */
public class RoadmapShots292Test {

    @Test
    public void captureRoadmapTabsAndDevStatusLinks() throws Exception {
        String base = System.getenv("PUNCHCLOCK_BASE");
        Assume.assumeTrue(base != null && !base.isEmpty());
        String outDir = System.getenv("PUNCHCLOCK_SHOTS_DIR");
        if (outDir == null || outDir.isEmpty()) {
            outDir = System.getProperty("user.home") + "/PunchClock-oki/shots292-self/dev1";
        }
        Path dir = Path.of(outDir);
        Files.createDirectories(dir);

        int[][] sizes = {{1280, 720}, {390, 844}};
        boolean[] touch = {false, true};
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            for (int i = 0; i < sizes.length; i++) {
                int w = sizes[i][0];
                int h = sizes[i][1];
                String tag = w < 500 ? "390" : "1280";
                Browser.NewPageOptions opts = new Browser.NewPageOptions().setViewportSize(w, h);
                if (touch[i]) {
                    opts.setHasTouch(true);
                }
                Page page = browser.newPage(opts);
                page.navigate(base + "/?open=roadmap",
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                page.waitForSelector("#roadmapBoard:not([hidden])",
                        new Page.WaitForSelectorOptions().setTimeout(12_000));
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("roadmap-" + tag + "-phases.png")));

                Locator devTab = page.locator("[data-roadmap-tab=\"dev\"]").first();
                if (touch[i] && devTab.count() > 0) {
                    devTab.tap();
                } else if (devTab.count() > 0) {
                    devTab.click();
                }
                page.waitForTimeout(400);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("roadmap-" + tag + "-dev.png")));

                Locator phasesTab = page.locator("[data-roadmap-tab=\"phases\"]").first();
                if (touch[i] && phasesTab.count() > 0) {
                    phasesTab.tap();
                } else if (phasesTab.count() > 0) {
                    phasesTab.click();
                }
                page.waitForTimeout(300);

                page.navigate(base + "/dev-status",
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                Locator progress = page.locator("a.dev-btn-action", new Page.LocatorOptions().setHasText("施工進度"));
                if (progress.count() > 0) {
                    if (touch[i]) {
                        progress.first().tap();
                    } else {
                        progress.first().click();
                    }
                }
                page.waitForSelector("#roadmapBoard:not([hidden])", new Page.WaitForSelectorOptions().setTimeout(8000));
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("dev-status-" + tag + "-roadmap-open.png")));

                page.navigate(base + "/dev-status",
                        new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
                Locator home = page.locator("a.dev-btn-action", new Page.LocatorOptions().setHasText("返回辦公室"));
                if (home.count() > 0) {
                    if (touch[i]) {
                        home.first().tap();
                    } else {
                        home.first().click();
                    }
                }
                page.waitForURL("**/");
                page.waitForTimeout(400);
                boolean roadmapVisible = page.locator("#roadmapBoard:not([hidden])").count() > 0;
                org.junit.Assert.assertFalse("返回辦公室不應自動開施工進度", roadmapVisible);
                page.screenshot(new Page.ScreenshotOptions().setPath(dir.resolve("dev-status-" + tag + "-home-only.png")));
                page.close();
            }
            browser.close();
        }
    }
}