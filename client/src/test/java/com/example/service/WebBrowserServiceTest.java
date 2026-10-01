package com.example.service;

import org.junit.Test;

import static org.junit.Assert.*;

public class WebBrowserServiceTest {

    @Test
    public void normalizeUrlAddsHttpsWhenSchemeMissing() {
        assertEquals("https://www.google.com.tw", WebBrowserService.normalizeUrl("www.google.com.tw"));
        assertEquals("https://example.com/a?b=1", WebBrowserService.normalizeUrl("  example.com/a?b=1 "));
    }

    @Test
    public void normalizeUrlKeepsHttpAndHttps() {
        assertEquals("http://intranet.local", WebBrowserService.normalizeUrl("http://intranet.local"));
        assertEquals("HTTPS://Example.com", WebBrowserService.normalizeUrl("HTTPS://Example.com"));
    }

    @Test
    public void normalizeUrlRejectsOtherSchemesAndBlank() {
        assertEquals("", WebBrowserService.normalizeUrl(null));
        assertEquals("", WebBrowserService.normalizeUrl("   "));
        assertEquals("", WebBrowserService.normalizeUrl("file:///etc/passwd"));
        assertEquals("", WebBrowserService.normalizeUrl("javascript://alert(1)"));
        assertEquals("", WebBrowserService.normalizeUrl("https://"));
        assertEquals("", WebBrowserService.normalizeUrl("two words"));
    }

    @Test
    public void openWithInvalidUrlDoesNotStart() {
        WebBrowserService service = new WebBrowserService();
        service.open("file:///tmp", true, false, null, null);
        assertFalse(service.isRunning());
    }

    @Test
    public void describeErrorExplainsMissingBrowser() {
        String msg = WebBrowserService.describeError(
                new RuntimeException("Executable doesn't exist at /x/chrome\nmore"));
        assertTrue(msg.contains("內建 Chromium"));
        assertEquals("first line", WebBrowserService.describeError(new RuntimeException("first line\nsecond")));
    }
}
