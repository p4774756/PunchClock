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
     * @param webhookUrl Discord Webhook 網址 (選填)
     * @param logger     日誌輸出 Callback
     */
    public void executeCheckIn(String targetUrl, String buttonId, String webhookUrl, Consumer<String> logger) {
        DiscordWebhookService webhookService = new DiscordWebhookService();
        log(logger, "⏰ 【觸發】排程時間已到，啟動 Playwright 瀏覽器...");

        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(false);

            log(logger, "啟動 Chromium 瀏覽器...");
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

            // 發送 Discord 成功推播
            if (webhookUrl != null && !webhookUrl.isBlank()) {
                webhookService.sendEmbedNotification(
                        webhookUrl,
                        "🎉 自動打卡成功！",
                        "• **目標網址**：" + targetUrl + "\n• **按鈕 ID**：" + selector + "\n• **執行結果**：✅ 已成功開啟瀏覽器並點擊按鈕",
                        0x22C55E, // 綠色
                        logger
                );
            }

            page.waitForTimeout(5000);
            browser.close();
            log(logger, "瀏覽器已關閉，指定日期打卡任務結束。");
        } catch (Exception ex) {
            String errorMsg = "❌ 打卡過程中發生錯誤：" + ex.getMessage();
            log(logger, errorMsg);

            // 發送 Discord 失敗推播
            if (webhookUrl != null && !webhookUrl.isBlank()) {
                webhookService.sendEmbedNotification(
                        webhookUrl,
                        "❌ 自動打卡失敗！",
                        "• **目標網址**：" + targetUrl + "\n• **錯誤訊息**：" + ex.getMessage(),
                        0xEF4444, // 紅色
                        logger
                );
            }
        }
    }

    private void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}

