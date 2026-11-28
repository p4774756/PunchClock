package com.example.service;

import com.example.service.automation.CheckInBrowserFactory;
import com.example.service.automation.CheckInPageDriver;
import com.example.service.automation.CheckInSession;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.Map;
import java.util.function.Consumer;

public final class PlaywrightCheckInFactory implements CheckInBrowserFactory {

    private Playwright playwright;
    private Boolean playwrightSkipDownload;
    private Boolean lastPlaywrightSkipDownload;
    private Browser cachedBrowser;

    @Override
    public CheckInSession openSession(String browserChoice, Consumer<String> logger) {
        String choice = normalizeChoice(browserChoice);
        ensurePlaywright(choice, logger);
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

    static boolean skipsBundledBrowserDownload(String browserChoice) {
        return usesLocalChannelBrowser(normalizeChoice(browserChoice));
    }

    static Playwright.CreateOptions playwrightCreateOptions(String browserChoice) {
        if (!skipsBundledBrowserDownload(browserChoice)) {
            return null;
        }
        return new Playwright.CreateOptions()
                .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
    }

    private static String normalizeChoice(String browserChoice) {
        if (browserChoice == null) {
            return "msedge";
        }
        String choice = browserChoice.trim().toLowerCase();
        switch (choice) {
            case "firefox":
            case "webkit":
            case "chromium":
                return choice;
            case "chrome":
            case "msedge":
            case "edge":
                return choice;
            default:
                return "msedge";
        }
    }

    private static boolean usesLocalChannelBrowser(String choice) {
        switch (choice) {
            case "firefox":
            case "webkit":
            case "chromium":
                return false;
            default:
                return true;
        }
    }

    private synchronized void ensurePlaywright(String choice, Consumer<String> logger) {
        boolean skip = usesLocalChannelBrowser(choice);
        if (playwright != null && playwrightSkipDownload != null && playwrightSkipDownload != skip) {
            if (!skip) {
                log(logger, "[初始化] 正在下載內建瀏覽器，需連網");
            }
            closePlaywright();
        } else if (playwright == null && !skip && Boolean.TRUE.equals(lastPlaywrightSkipDownload)) {
            log(logger, "[初始化] 正在下載內建瀏覽器，需連網");
        }
        if (playwright == null) {
            PlaywrightNodeSupport.ensureNodeForPackagedApp(logger);
            log(logger, "[初始化] 首次初始化 Playwright 引擎...");
            Playwright.CreateOptions options = playwrightCreateOptions(choice);
            playwright = options == null ? Playwright.create() : Playwright.create(options);
            playwrightSkipDownload = skip;
            lastPlaywrightSkipDownload = skip;
        }
    }

    private Browser launchBrowser(String choice, Consumer<String> logger) {
        closeCachedBrowser();

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(false);

        switch (choice) {
            case "chrome":
                log(logger, "[執行] 啟動 本機 Google Chrome 瀏覽器...");
                launchOptions.setChannel("chrome");
                cachedBrowser = launchLocalChromium(choice, launchOptions, logger);
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
                cachedBrowser = launchLocalChromium(choice, launchOptions, logger);
                break;
        }

        return cachedBrowser;
    }

    private Browser launchLocalChromium(String choice, BrowserType.LaunchOptions launchOptions,
                                        Consumer<String> logger) {
        try {
            return playwright.chromium().launch(launchOptions);
        } catch (RuntimeException failure) {
            String label = "chrome".equals(choice) ? "Google Chrome" : "Microsoft Edge";
            log(logger, "[失敗] 找不到本機 " + label
                    + "，請確認已安裝，或改選 Chromium／Firefox／WebKit。");
            throw failure;
        }
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
            playwrightSkipDownload = null;
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