package com.example.service;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * 專責處理 Selenium 瀏覽器控制與打卡點擊邏輯
 */
public class AutomationService {

    /**
     * 執行自動打卡任務
     *
     * @param targetUrl 目標網址
     * @param buttonId  打卡按鈕的 HTML Element ID
     * @param logger    日誌輸出 Callback
     */
    public void executeCheckIn(String targetUrl, String buttonId, Consumer<String> logger) {
        log(logger, "⏰ 【觸發】排程時間已到，啟動 Selenium 瀏覽器...");

        try {
            WebDriverManager.edgedriver().setup();
        } catch (Exception e) {
            log(logger, "❌ WebDriver 初始化失敗：" + e.getMessage());
            return;
        }

        EdgeOptions options = new EdgeOptions();
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--allow-running-insecure-content");

        WebDriver driver = null;
        try {
            driver = new EdgeDriver(options);
            driver.manage().window().maximize();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            log(logger, "網頁導向中：" + targetUrl);
            driver.get(targetUrl);

            WebElement checkInButton = driver.findElement(By.id(buttonId));
            log(logger, "成功找到打卡按鈕 (ID: " + buttonId + ")，準備點擊...");
            checkInButton.click();
            log(logger, "✅ 已成功點擊打卡按鈕！");

            Thread.sleep(5000);
        } catch (Exception ex) {
            log(logger, "❌ 打卡過程中發生錯誤：" + ex.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
                log(logger, "瀏覽器已關閉，指定日期打卡任務結束。");
            } else {
                log(logger, "⚠️ 瀏覽器未成功啟動，任務結束。");
            }
        }
    }

    private void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
