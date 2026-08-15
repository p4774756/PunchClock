package com.example.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.function.Consumer;

/**
 * 專責處理 Playwright 瀏覽器控制與打卡點擊邏輯
 * Playwright instance 採 lazy-init 重用策略，避免重複啟動 Node.js 程序
 */
public class AutomationService {

    private static final int NAVIGATE_TIMEOUT_MS = 30_000;
    private static final int SELECTOR_TIMEOUT_MS = 15_000;
    private static final int CLICK_TIMEOUT_MS = 5_000;

    private Playwright playwright;
    private Browser cachedBrowser;
    private String cachedBrowserType;

    /**
     * 執行自動打卡任務
     *
     * @param targetUrl     目標網址
     * @param buttonId      打卡按鈕的 HTML Element ID 或 Selector
     * @param browserChoice 選擇使用的瀏覽器類型 (chrome, msedge, chromium, firefox, webkit)
     * @param logger        日誌輸出 Callback
     */
    public synchronized boolean executeCheckIn(String targetUrl, String buttonId, String browserChoice, Consumer<String> logger) {
        log(logger, "⏰ 【觸發】準備啟動自動化瀏覽器 (選擇: " + (browserChoice != null ? browserChoice : "msedge") + ")...");

        BrowserContext context = null;
        try {
            ensurePlaywright(logger);
            String choice = (browserChoice == null) ? "msedge" : browserChoice.trim().toLowerCase();
            Browser browser = getOrCreateBrowser(choice, logger);

            context = browser.newContext();
            Page page = context.newPage();

            log(logger, "網頁導向中：" + targetUrl);
            page.navigate(targetUrl, new Page.NavigateOptions().setTimeout(NAVIGATE_TIMEOUT_MS));

            String selector = (buttonId == null) ? "" : buttonId.trim();
            if (selector.isEmpty()) {
                log(logger, "❌ 未設定打卡按鈕 Selector");
                return false;
            }
            if (!(selector.startsWith("#") || selector.startsWith(".") || selector.contains("["))) {
                selector = "#" + selector;
            }

            log(logger, "等待打卡按鈕出現（最多 " + (SELECTOR_TIMEOUT_MS / 1000) + " 秒，Selector: " + selector + "）...");
            try {
                page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                        .setState(WaitForSelectorState.VISIBLE)
                        .setTimeout(SELECTOR_TIMEOUT_MS));
            } catch (TimeoutError timeoutError) {
                log(logger, "❌ 逾時：找不到可見的打卡按鈕（" + selector + "）");
                return false;
            }

            page.click(selector, new Page.ClickOptions().setTimeout(CLICK_TIMEOUT_MS));
            log(logger, "✅ 已成功點擊打卡按鈕！");

            page.waitForTimeout(5000);
            log(logger, "瀏覽器分頁已關閉，打卡任務結束。");
            return true;
        } catch (Exception ex) {
            closeCachedBrowser();
            String errorMsg = "❌ 打卡過程中發生錯誤：" + ex.getMessage();
            log(logger, errorMsg);
            throw new RuntimeException(errorMsg, ex);
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Lazy-init Playwright instance（重用，避免重複啟動 Node.js 程序）
     */
    private void ensurePlaywright(Consumer<String> logger) {
        if (playwright == null) {
            log(logger, "🔧 首次初始化 Playwright 引擎...");
            playwright = Playwright.create();
        }
    }

    /**
     * 取得或建立瀏覽器實例。若瀏覽器類型相同則重用已啟動的瀏覽器。
     */
    private Browser getOrCreateBrowser(String choice, Consumer<String> logger) {
        // 若瀏覽器類型不同或已關閉，需重新建立
        if (cachedBrowser != null && cachedBrowser.isConnected() && choice.equals(cachedBrowserType)) {
            log(logger, "♻️ 重用已啟動的瀏覽器...");
            return cachedBrowser;
        }

        closeCachedBrowser();

        BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(false);

        switch (choice) {
            case "chrome":
                log(logger, "🚀 啟動 本機 Google Chrome 瀏覽器...");
                launchOptions.setChannel("chrome");
                cachedBrowser = playwright.chromium().launch(launchOptions);
                break;
            case "firefox":
                log(logger, "🚀 啟動 內建 Firefox 瀏覽器...");
                cachedBrowser = playwright.firefox().launch(launchOptions);
                break;
            case "webkit":
                log(logger, "🚀 啟動 內建 WebKit (Safari核心) 瀏覽器...");
                cachedBrowser = playwright.webkit().launch(launchOptions);
                break;
            case "chromium":
                log(logger, "🚀 啟動 內建 Chromium 瀏覽器...");
                cachedBrowser = playwright.chromium().launch(launchOptions);
                break;
            case "msedge":
            case "edge":
            default:
                log(logger, "🚀 啟動 本機 Microsoft Edge 瀏覽器...");
                launchOptions.setChannel("msedge");
                cachedBrowser = playwright.chromium().launch(launchOptions);
                break;
        }

        cachedBrowserType = choice;
        return cachedBrowser;
    }

    private void closeCachedBrowser() {
        if (cachedBrowser != null) {
            try {
                if (cachedBrowser.isConnected()) {
                    cachedBrowser.close();
                }
            } catch (Exception ignored) {}
            cachedBrowser = null;
            cachedBrowserType = null;
        }
    }

    /**
     * 關閉所有 Playwright 資源（應用程式關閉時呼叫）
     */
    public synchronized void shutdown() {
        closeCachedBrowser();
        if (playwright != null) {
            try {
                playwright.close();
            } catch (Exception ignored) {}
            playwright = null;
        }
    }

    private void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
