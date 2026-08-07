package com.example.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * 專責處理 Discord Webhook 單向卡片推播服務
 */
public class DiscordWebhookService {

    private final HttpClient httpClient;

    public DiscordWebhookService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 發送單向 Discord Rich Embed 卡片推播
     *
     * @param webhookUrl Webhook 網址
     * @param title      卡片標題
     * @param message    卡片主要內容 / 說明
     * @param colorHex   卡片左側邊條顏色 (例如 0x22C55E 綠色, 0xEF4444 紅色, 0x3B82F6 藍色)
     * @param logger     日誌 Callback
     */
    public void sendEmbedNotification(String webhookUrl,
                                      String title,
                                      String message,
                                      int colorHex,
                                      Consumer<String> logger) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return;
        }

        String jsonPayload = String.format(
                "{" +
                        "\"embeds\": [{" +
                        "\"title\": \"%s\"," +
                        "\"description\": \"%s\"," +
                        "\"color\": %d," +
                        "\"footer\": {\"text\": \"clickClick 自動打卡助手\"}" +
                        "}]" +
                "}",
                escapeJson(title),
                escapeJson(message),
                colorHex
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl.trim()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            log(logger, "🔔 [Discord Webhook] 成功推播訊息至 Discord 頻道。");
                        } else {
                            log(logger, "⚠️ [Discord Webhook] 推播失敗，HTTP 狀態碼：" + response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        log(logger, "❌ [Discord Webhook] 發送發生例外：" + ex.getMessage());
                        return null;
                    });
        } catch (Exception ex) {
            log(logger, "❌ [Discord Webhook] URL 格式無效或建構失敗：" + ex.getMessage());
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
