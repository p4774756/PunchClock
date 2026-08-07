package com.example.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 專責與 ping-pong-server (Render 雲端服務) 進行心跳 (Heartbeat) 存活回報
 */
public class HeartbeatService {

    private final HttpClient httpClient;
    private ScheduledExecutorService heartbeatScheduler;

    private String serverUrl = "";
    private String clientId = "company-worker";
    private String currentStatus = "ONLINE";
    private String scheduledTime = null;
    private boolean isHeartbeatActive = false;

    public HeartbeatService() {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(8));

        // 在這裡為 HttpClient 注入繞過 SSL 的設定
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            
            // 將 SSLContext 設定進去
            builder.sslContext(sc);
            
            // 提示：Java 11 HttpClient 的 Hostname 驗證預設會跟隨 SSLContext，
            // 如果執行後仍有問題，可透過系統參數強制關閉：System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
            
        } catch (Exception e) {
            System.err.println("初始化 SSL 繞過失敗: " + e.getMessage());
        }

        this.httpClient = builder.build();
    }

    /**
     * 測試與伺服器的連線
     */
    public void testConnection(String serverUrl, Consumer<String> logger, Consumer<Boolean> onResult) {
        if (serverUrl == null || serverUrl.isBlank()) {
            if (logger != null) logger.accept("⚠️ 請先輸入有效的 ping-pong-server 網址！");
            if (onResult != null) onResult.accept(false);
            return;
        }

        String targetPingUrl = formatServerUrl(serverUrl) + "/ping";
        log(logger, "📡 [Server 連線測試] 嘗試發送 Ping 至：" + targetPingUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetPingUrl))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            log(logger, "✅ [Server 連線測試] 成功！伺服器回應正常 (200 OK)。");
                            if (onResult != null) onResult.accept(true);
                        } else {
                            log(logger, "⚠️ [Server 連線測試] 回應異常，HTTP 狀態碼：" + response.statusCode());
                            if (onResult != null) onResult.accept(false);
                        }
                    })
                    .exceptionally(ex -> {
                        log(logger, "❌ [Server 連線測試] 無法連線至伺服器：" + ex.getMessage());
                        if (onResult != null) onResult.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            log(logger, "❌ [Server 連線測試] 網址格式錯誤：" + ex.getMessage());
            if (onResult != null) onResult.accept(false);
        }
    }

    /**
     * 啟動定期心跳服務 (每 60 秒發送一次)
     */
    public void startHeartbeat(String serverUrl, Consumer<String> logger, Consumer<Boolean> statusCallback) {
        stopHeartbeat(); // 先安全清理舊排程

        if (serverUrl == null || serverUrl.isBlank()) {
            log(logger, "⚠️ [心跳服務] 伺服器網址為空，未開啟心跳回報。");
            return;
        }

        this.serverUrl = formatServerUrl(serverUrl);
        this.isHeartbeatActive = true;

        log(logger, "💚 [心跳服務] 已啟動！目標伺服器：" + this.serverUrl + " (週期：60 秒)");

        heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        heartbeatScheduler.scheduleAtFixedRate(() -> sendHeartbeat(logger, statusCallback), 0, 60, TimeUnit.SECONDS);
    }

    /**
     * 發送單次心跳
     */
    public void sendHeartbeat(Consumer<String> logger, Consumer<Boolean> statusCallback) {
        if (!isHeartbeatActive || serverUrl.isBlank()) return;

        String endpoint = serverUrl + "/api/heartbeat";
        String payload = String.format(
                "{\"clientId\":\"%s\",\"status\":\"%s\",\"scheduledTime\":%s}",
                escapeJson(clientId),
                escapeJson(currentStatus),
                scheduledTime == null ? "null" : "\"" + escapeJson(scheduledTime) + "\""
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            if (statusCallback != null) statusCallback.accept(true);
                        } else {
                            log(logger, "⚠️ [心跳回報] 失敗，HTTP 狀態碼：" + response.statusCode());
                            if (statusCallback != null) statusCallback.accept(false);
                        }
                    })
                    .exceptionally(ex -> {
                        log(logger, "❌ [心跳回報] 連線發生異常：" + ex.getMessage());
                        if (statusCallback != null) statusCallback.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            log(logger, "❌ [心跳回報] 請求發送錯誤：" + ex.getMessage());
            if (statusCallback != null) statusCallback.accept(false);
        }
    }

    /**
     * 更新打卡任務狀態
     */
    public void updateTaskStatus(String status, String scheduledTime) {
        this.currentStatus = status;
        this.scheduledTime = scheduledTime;
    }

    /**
     * 停止心跳服務
     */
    public void stopHeartbeat() {
        this.isHeartbeatActive = false;
        if (heartbeatScheduler != null && !heartbeatScheduler.isShutdown()) {
            heartbeatScheduler.shutdownNow();
        }
    }

    private String formatServerUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
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
