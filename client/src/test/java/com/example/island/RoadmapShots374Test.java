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

/** 驗收 #374：Milestone 路線圖、搜尋列、無今日完成側欄。 */
public class RoadmapShots374Test {

    @Test
    public void captureMilestoneRoadmapAndSearch() throws Exception {
        String base = System.getenv("PUNCHCLOCK_BASE");
        Assume.assumeTrue(base != null && !base.isEmpty());
        String outDir = System.getenv("PUNCHCLOCK_SHOTS_DIR");
        if (outDir == null || outDir.isEmpty()) {
            outDir = System.getProperty("user.home") + "/PunchClock-oki/shots374-self/dev1";
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
                        new Page.WaitForSelectorOptions().setTimeout(20_000));
                page.waitForTimeout(1200);
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(dir.resolve("roadmap-" + tag + "-milestone.png")));

                Locator search = page.locator("[data-roadmap-search]");
                search.fill("i349");
                page.waitForTimeout(500);
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(dir.resolve("roadmap-" + tag + "-search-i349.png")));

                search.fill("zzz-no-hit-374");
                page.waitForTimeout(400);
                page.screenshot(new Page.ScreenshotOptions()
                        .setPath(dir.resolve("roadmap-" + tag + "-search-miss.png")));
                page.close();
            }
            browser.close();
        }
    }
}