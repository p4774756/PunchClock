package com.example.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.function.Consumer;

/**
 * 專責處理 Playwright 瀏覽器控制與打卡點擊邏輯
 */
public class AutomationService {

    /**
     * 執行自動打卡任務
     *
     * @param targetUrl  目標網址
     * @param buttonId   打卡按鈕的 HTML Element ID 或 Selector
     * @param logger     日誌輸出 Callback
     */
    public boolean executeCheckIn(String targetUrl, String buttonId, Consumer<String> logger) {
        log(logger, "⏰ 【觸發】啟動 Playwright 瀏覽器...");

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(false)
                    .setChannel("msedge");

            log(logger, "啟動 Microsoft Edge 瀏覽器...");
            Browser browser = playwright.chromium().launch(launchOptions);
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            log(logger, "網頁導向中：" + targetUrl);
            page.navigate(targetUrl);

            String selector = (buttonId.startsWith("#") || buttonId.startsWith(".") || buttonId.contains("["))
                    ? buttonId
                    : "#" + buttonId;

            log(logger, "準備尋找並點擊打卡按鈕 (Selector: " + selector + ")...");
            page.click(selector);
            log(logger, "✅ 已成功點擊打卡按鈕！");

            page.waitForTimeout(5000);
            browser.close();
            log(logger, "瀏覽器已關閉，打卡任務結束。");
            return true;
        } catch (Exception ex) {
            String errorMsg = "❌ 打卡過程中發生錯誤：" + ex.getMessage();
            log(logger, errorMsg);
            throw new RuntimeException(errorMsg, ex);
        }
    }

    private void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}

