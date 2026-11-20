package com.example.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URL;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/**
 * 本機 HTML fixture 的 headless Playwright 整合測試；無瀏覽器時略過。
 */
public class AutomationServicePlaywrightIT {

    private static boolean headlessPlaywrightAvailable;

    @BeforeClass
    public static void detectPlaywright() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            browser.close();
            headlessPlaywrightAvailable = true;
        } catch (Exception ex) {
            headlessPlaywrightAvailable = false;
        }
    }

    @Test
    public void executeCheckIn_headlessLocalHtmlFixture() throws Exception {
        Assume.assumeTrue("Playwright headless browser not available", headlessPlaywrightAvailable);

        URL resource = AutomationServicePlaywrightIT.class.getResource("/automation/checkin-success.html");
        Assume.assumeNotNull(resource);
        String fileUrl = Path.of(resource.toURI()).toUri().toString();

        AutomationService service = new AutomationService(new HeadlessPlaywrightCheckInFactory());

        boolean ok = service.executeCheckIn(fileUrl, "#punch-btn", "chromium", msg -> { });

        assertTrue(ok);
    }
}