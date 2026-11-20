package com.example.service;

import com.example.service.automation.CheckInBrowserFactory;
import com.example.service.automation.CheckInPageDriver;
import com.example.service.automation.CheckInSession;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.function.Consumer;

public final class PlaywrightCheckInFactory implements CheckInBrowserFactory {

    private Playwright playwright;
    private Browser cachedBrowser;

    @Override
    public CheckInSession openSession(String browserChoice, Consumer<String> logger) {
        ensurePlaywright(logger);
        String choice = (browserChoice == null) ? "msedge" : browserChoice.trim().toLowerCase();
        Browser browser = launchBrowser(choice, logger);
        BrowserContext context = browser.newContext(
                new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
        return new PlaywrightCheckInSession(context);
    }

    @Override
    public synchronized void shutdown() {
        closeCachedBrowser();
        closePlaywright();
    }

    private void ensurePlaywright(Consumer<String> logger) {
        if (playwright == null) {
            log(logger, "[初始化] 首次初始化 Playwright 引擎...");
            playwright = Playwright.create();
        }
    }

    private Browser launchBrowser(String choice, Consumer<String> logger) {
        closeCachedBrowser();

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(false);

        switch (choice) {
            case "chrome":
                log(logger, "[執行] 啟動 本機 Google Chrome 瀏覽器...");
                launchOptions.setChannel("chrome");
                cachedBrowser = playwright.chromium().launch(launchOptions);
                break;
            case "firefox":
                log(logger, "[執行] 啟動 內建 Firefox 瀏覽器...");
                cachedBrowser = playwright.firefox().launch(launchOptions);
                break;
            case "webkit":
                log(logger, "[執行] 啟動 內建 WebKit (Safari核心) 瀏覽器...");
                cachedBrowser = playwright.webkit().launch(launchOptions);
                break;
            case "chromium":
                log(logger, "[執行] 啟動 內建 Chromium 瀏覽器...");
                cachedBrowser = playwright.chromium().launch(launchOptions);
                break;
            case "msedge":
            case "edge":
            default:
                log(logger, "[執行] 啟動 本機 Microsoft Edge 瀏覽器...");
                launchOptions.setChannel("msedge");
                cachedBrowser = playwright.chromium().launch(launchOptions);
                break;
        }

        return cachedBrowser;
    }

    private void closeCachedBrowser() {
        if (cachedBrowser != null) {
            try {
                if (cachedBrowser.isConnected()) {
                    cachedBrowser.close();
                }
            } catch (Exception ignored) {
            }
            cachedBrowser = null;
        }
    }

    private void closePlaywright() {
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception ignored) {
            }
            playwright = null;
        }
    }

    private static void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    private final class PlaywrightCheckInSession implements CheckInSession {

        private final BrowserContext context;

        private PlaywrightCheckInSession(BrowserContext context) {
            this.context = context;
        }

        @Override
        public CheckInPageDriver newPage() {
            return new PlaywrightPageDriver(context.newPage());
        }

        @Override
        public void close() {
            try {
                context.close();
            } catch (Exception ignored) {
            }
            PlaywrightCheckInFactory.this.closeCachedBrowser();
            PlaywrightCheckInFactory.this.closePlaywright();
        }
    }
}