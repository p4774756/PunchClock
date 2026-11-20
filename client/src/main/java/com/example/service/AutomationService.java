package com.example.service;

import com.example.service.automation.CheckInBrowserFactory;
import com.example.service.automation.CheckInPageDriver;
import com.example.service.automation.CheckInSession;
import com.microsoft.playwright.TimeoutError;

import java.util.function.Consumer;

/**
 * 專責處理 Playwright 瀏覽器控制與打卡點擊邏輯。
 * 每次打卡結束後關閉瀏覽器與 Playwright，避免視窗殘留。
 */
public class AutomationService {

    static final int NAVIGATE_TIMEOUT_MS = 45_000;
    static final int NAVIGATE_MAX_ATTEMPTS = 3;
    static final int NAVIGATE_RETRY_DELAY_MS = 5_000;
    static final int SELECTOR_TIMEOUT_MS = 15_000;
    static final int CLICK_TIMEOUT_MS = 5_000;
    static final int HIGHLIGHT_DURATION_MS = 10_000;
    static final int LAYOUT_STABLE_MS = 700;
    static final int LAYOUT_STABLE_POLL_MS = 150;
    static final int LAYOUT_STABLE_MAX_MS = 8_000;
    static final int HIGHLIGHT_TRACK_INTERVAL_MS = 120;

    private final CheckInBrowserFactory browserFactory;

    public AutomationService() {
        this(new PlaywrightCheckInFactory());
    }

    AutomationService(CheckInBrowserFactory browserFactory) {
        this.browserFactory = browserFactory;
    }

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

        CheckInSession session = null;
        try {
            session = browserFactory.openSession(browserChoice, logger);
            CheckInPageDriver page = session.newPage();

            log(logger, "網頁導向中：" + targetUrl);
            navigateWithRetry(page, targetUrl, logger);

            String selector = (buttonId == null) ? "" : buttonId.trim();
            if (selector.isEmpty()) {
                log(logger, "[失敗] 未設定目標按鈕 Selector");
                return false;
            }
            if (!(selector.startsWith("#") || selector.startsWith(".") || selector.contains("["))) {
                selector = "#" + selector;
            }

            log(logger, "等待目標按鈕出現（最多 " + (SELECTOR_TIMEOUT_MS / 1000) + " 秒，Selector: " + selector + "）...");
            try {
                page.waitForSelectorVisible(selector, SELECTOR_TIMEOUT_MS);
            } catch (TimeoutError timeoutError) {
                log(logger, "[失敗] 逾時：找不到可見的目標按鈕（" + selector + "）");
                return false;
            }

            log(logger, "等待頁面版面穩定（避免廣告載入推挤偏移）...");
            page.waitForLayoutStable(selector, LAYOUT_STABLE_MS, LAYOUT_STABLE_POLL_MS, LAYOUT_STABLE_MAX_MS);

            log(logger, "標注目標按鈕（漫畫集中線＋紅框 " + (HIGHLIGHT_DURATION_MS / 1000) + " 秒後點擊，並持續追蹤位置）...");
            page.runHighlightSequence(selector, HIGHLIGHT_DURATION_MS, HIGHLIGHT_TRACK_INTERVAL_MS);
            page.removeHighlight(selector);

            page.click(selector, CLICK_TIMEOUT_MS);
            log(logger, "[成功] 已成功點擊目標按鈕！");

            page.waitForTimeout(5000);
            log(logger, "瀏覽器已關閉，排程任務結束。");
            return true;
        } catch (Exception ex) {
            String errorMsg = formatUserError(ex);
            log(logger, errorMsg);
            throw new RuntimeException(errorMsg, ex);
        } finally {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            } else {
                // openSession 在回傳 session 前失敗時，Playwright 與瀏覽器仍留在 factory。
                // 舊 finally 不論 context 有沒有建立，都會在這次打卡結束當下關掉，不等應用程式之後再呼叫。
                try {
                    shutdown();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * 導向目標網址；遇到網路層錯誤（net::ERR_*）或載入逾時時，短暫等待後重試。
     */
    void navigateWithRetry(CheckInPageDriver page, String targetUrl, Consumer<String> logger) {
        for (int attempt = 1; ; attempt++) {
            try {
                page.navigate(targetUrl, NAVIGATE_TIMEOUT_MS);
                return;
            } catch (RuntimeException ex) {
                if (attempt >= NAVIGATE_MAX_ATTEMPTS || !isRetryableNavigateError(ex)) {
                    throw ex;
                }
                log(logger, "[警告] 網頁導向失敗（第 " + attempt + "/" + NAVIGATE_MAX_ATTEMPTS + " 次），"
                        + (NAVIGATE_RETRY_DELAY_MS / 1000) + " 秒後重試：" + summarize(ex.getMessage()));
                page.waitForTimeout(NAVIGATE_RETRY_DELAY_MS);
            }
        }
    }

    static boolean isRetryableNavigateError(Throwable ex) {
        if (ex instanceof TimeoutError) {
            return true;
        }
        String raw = ex.getMessage();
        return raw != null && raw.contains("net::ERR_");
    }

    static String summarize(String raw) {
        if (raw == null) {
            return "";
        }
        int idx = raw.indexOf("Call log:");
        if (idx >= 0) {
            raw = raw.substring(0, idx);
        }
        raw = raw.trim().replace('\n', ' ');
        return raw.length() > 100 ? raw.substring(0, 100) + "..." : raw;
    }

    /**
     * 關閉所有 Playwright 資源（應用程式關閉時呼叫）
     */
    public synchronized void shutdown() {
        browserFactory.shutdown();
    }

    static String formatUserError(Exception ex) {
        String raw = ex.getMessage();
        if (ex instanceof TimeoutError || (raw != null && raw.contains("Timeout") && raw.contains("exceeded"))) {
            return "[失敗] 網頁載入逾時（" + (NAVIGATE_TIMEOUT_MS / 1000)
                    + " 秒）。廣告多的網站可改選 Edge / Chrome（本機）。";
        }
        if (raw != null && raw.contains("net::ERR_")) {
            int start = raw.indexOf("net::ERR_");
            int end = start;
            while (end < raw.length() && (Character.isLetterOrDigit(raw.charAt(end))
                    || raw.charAt(end) == '_' || raw.charAt(end) == ':')) {
                end++;
            }
            return "[失敗] 無法連線到打卡網站（" + raw.substring(start, end)
                    + "），已重試 " + NAVIGATE_MAX_ATTEMPTS + " 次。請檢查網路／VPN／Proxy 後再試。";
        }
        if (raw != null && raw.contains("Call log:")) {
            raw = raw.substring(0, raw.indexOf("Call log:")).trim();
        }
        if (raw != null && raw.length() > 100) {
            raw = raw.substring(0, 100) + "...";
        }
        return "[失敗] 執行過程中發生錯誤：" + (raw != null ? raw : ex.getClass().getSimpleName());
    }

    private void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
