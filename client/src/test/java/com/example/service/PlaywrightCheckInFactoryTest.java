package com.example.service;

import com.microsoft.playwright.Playwright;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PlaywrightCheckInFactoryTest {

    @Test
    public void systemBrowsersSkipBundledDownload() {
        assertTrue(PlaywrightCheckInFactory.skipsBundledBrowserDownload("msedge"));
        assertTrue(PlaywrightCheckInFactory.skipsBundledBrowserDownload("edge"));
        assertTrue(PlaywrightCheckInFactory.skipsBundledBrowserDownload("chrome"));
        assertTrue(PlaywrightCheckInFactory.skipsBundledBrowserDownload(null));
        assertTrue(PlaywrightCheckInFactory.skipsBundledBrowserDownload("  MSEdge  "));
        assertTrue(PlaywrightCheckInFactory.skipsBundledBrowserDownload("unknown"));
    }

    @Test
    public void bundledBrowsersAllowDownload() {
        assertFalse(PlaywrightCheckInFactory.skipsBundledBrowserDownload("chromium"));
        assertFalse(PlaywrightCheckInFactory.skipsBundledBrowserDownload("firefox"));
        assertFalse(PlaywrightCheckInFactory.skipsBundledBrowserDownload("webkit"));
    }

    @Test
    public void createOptionsSetsSkipEnvForSystemBrowsers() {
        Playwright.CreateOptions edge = PlaywrightCheckInFactory.playwrightCreateOptions("msedge");
        assertNotNull(edge);
        assertEquals(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"), edge.env);

        Playwright.CreateOptions chrome = PlaywrightCheckInFactory.playwrightCreateOptions("chrome");
        assertNotNull(chrome);
        assertEquals("1", chrome.env.get("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD"));

        assertNotNull(PlaywrightCheckInFactory.playwrightCreateOptions(null));
    }

    @Test
    public void createOptionsOmitsSkipEnvForBundledBrowsers() {
        assertNull(PlaywrightCheckInFactory.playwrightCreateOptions("chromium"));
        assertNull(PlaywrightCheckInFactory.playwrightCreateOptions("firefox"));
        assertNull(PlaywrightCheckInFactory.playwrightCreateOptions("webkit"));
    }
}