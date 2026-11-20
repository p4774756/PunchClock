package com.example.service;

import com.example.service.automation.CheckInBrowserFactory;
import com.example.service.automation.CheckInPageDriver;
import com.example.service.automation.CheckInSession;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.function.Consumer;

/** 測試用 headless Chromium，行為與正式 Playwright 流程相同僅改為無視窗。 */
final class HeadlessPlaywrightCheckInFactory implements CheckInBrowserFactory {

    private Playwright playwright;
    private Browser browser;

    @Override
    public CheckInSession openSession(String browserChoice, Consumer<String> logger) {
        if (playwright == null) {
            playwright = Playwright.create();
        }
        if (browser == null || !browser.isConnected()) {
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        }
        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
        return new Session(context);
    }

    @Override
    public void shutdown() {
        if (browser != null) {
            try {
                browser.close();
            } catch (Exception ignored) {
            }
            browser = null;
        }
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception ignored) {
            }
            playwright = null;
        }
    }

    private final class Session implements CheckInSession {

        private final BrowserContext context;

        private Session(BrowserContext context) {
            this.context = context;
        }

        @Override
        public CheckInPageDriver newPage() {
            return new ShortHighlightPageDriver(context.newPage());
        }

        @Override
        public void close() {
            try {
                context.close();
            } catch (Exception ignored) {
            }
            HeadlessPlaywrightCheckInFactory.this.shutdown();
        }
    }

    private static final class ShortHighlightPageDriver implements CheckInPageDriver {

        private final PlaywrightPageDriver delegate;

        private ShortHighlightPageDriver(com.microsoft.playwright.Page page) {
            this.delegate = new PlaywrightPageDriver(page);
        }

        @Override
        public void navigate(String targetUrl, int timeoutMs) {
            delegate.navigate(targetUrl, timeoutMs);
        }

        @Override
        public void waitForSelectorVisible(String selector, int timeoutMs) {
            delegate.waitForSelectorVisible(selector, timeoutMs);
        }

        @Override
        public void waitForLayoutStable(String selector, int stableMs, int pollMs, int maxMs) {
            delegate.waitForLayoutStable(selector, stableMs, pollMs, maxMs);
        }

        @Override
        public void runHighlightSequence(String selector, int durationMs, int trackIntervalMs) {
            delegate.runHighlightSequence(selector, 300, trackIntervalMs);
        }

        @Override
        public void removeHighlight(String selector) {
            delegate.removeHighlight(selector);
        }

        @Override
        public void click(String selector, int timeoutMs) {
            delegate.click(selector, timeoutMs);
        }

        @Override
        public void waitForTimeout(int timeoutMs) {
            delegate.waitForTimeout(timeoutMs);
        }
    }
}