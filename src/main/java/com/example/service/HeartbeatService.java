package com.example.service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 專責與 ping-pong-server 進行單向 HTTP POST 存活與狀態上報
 * （完全無 WebSocket，無任何被控或反向連線風險）
 */
public class HeartbeatService {

    private final HttpClient httpClient;
    private ScheduledExecutorService scheduler;

    private String serverUrl = "";
    private String clientId = "company-worker";
    private String currentStatus = "ONLINE";
    private String scheduledTime = null;
    private String message = null;
    private boolean isServiceActive = false;
    private Consumer<String> commandListener;

    public void setCommandListener(Consumer<String> commandListener) {
        this.commandListener = commandListener;
    }

    public HeartbeatService() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(8));

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
            builder.sslContext(sc);
        } catch (Exception e) {
            System.err.println("初始化 SSL 繞過失敗: " + e.getMessage());
        }

        this.httpClient = builder.build();
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String newClientId) {
        if (newClientId != null && !newClientId.trim().isEmpty()) {
            this.clientId = newClientId.trim();
        }
    }

    public interface TaskDetailsProvider {
        String getTargetUrl();
        String getButtonId();
    }

    private TaskDetailsProvider taskDetailsProvider;

    public void setTaskDetailsProvider(TaskDetailsProvider taskDetailsProvider) {
        this.taskDetailsProvider = taskDetailsProvider;
    }

    /**
     * 測試與伺服器的 HTTP Ping 連線
     */
    public void testConnection(String serverUrl, Consumer<String> logger, Consumer<Boolean> onResult) {
        if (serverUrl == null || serverUrl.isBlank()) {
            if (logger != null) logger.accept("⚠️ 請先輸入有效的 ping-pong-server 網址！");
            if (onResult != null) onResult.accept(false);
            return;
        }

        String targetPingUrl = formatServerUrl(serverUrl) + "/ping";
        log(logger, "📡 [Server 連線測試] 發送 HTTP GET Ping 至：" + targetPingUrl);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetPingUrl))
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            log(logger, "✅ [Server 連線測試] 成功！伺服器回應 200 OK。");
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
     * 啟動定期單向 HTTP POST 心跳
     */
    public void startHeartbeat(String serverUrl, Consumer<String> logger, Consumer<Boolean> statusCallback) {
        stopHeartbeat(); // 清理舊任務

        if (serverUrl == null || serverUrl.isBlank()) {
            log(logger, "⚠️ [HTTP POST 服務] 伺服器網址為空，未開啟連線。");
            return;
        }

        this.serverUrl = formatServerUrl(serverUrl);
        this.isServiceActive = true;

        String endpoint = this.serverUrl + "/api/heartbeat";
        log(logger, "🔌 [HTTP POST 服務] 啟動單向心跳上報 (純 HTTP POST)：" + endpoint);

        scheduler = Executors.newScheduledThreadPool(1);

        // 立即執行一次上報
        sendPostHeartbeat(logger, statusCallback);

        // 每 15 秒定期單向 HTTP POST 上報
        scheduler.scheduleAtFixedRate(() -> {
            if (!isServiceActive) return;
            sendPostHeartbeat(logger, statusCallback);
        }, 15, 15, TimeUnit.SECONDS);
    }

    /**
     * 發送單向 HTTP POST 心跳請求
     */
    public void sendPostHeartbeat(Consumer<String> logger, Consumer<Boolean> statusCallback) {
        if (!isServiceActive || serverUrl.isBlank()) return;

        String endpoint = serverUrl + "/api/heartbeat";
        String jsonBody = String.format(
                "{\"clientId\":\"%s\",\"status\":\"%s\",\"scheduledTime\":%s,\"message\":%s}",
                escapeJson(clientId),
                escapeJson(currentStatus),
                scheduledTime == null ? "null" : "\"" + escapeJson(scheduledTime) + "\"",
                message == null ? "null" : "\"" + escapeJson(message) + "\""
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            if (statusCallback != null) statusCallback.accept(true);
                            String body = response.body();
                            if (body != null && commandListener != null) {
                                if (body.contains("\"action\":\"CANCEL_SCHEDULE\"")) {
                                    log(logger, "🛑 [HTTP 心跳] 收到伺服器取消排程指令 (CANCEL_SCHEDULE)");
                                    commandListener.accept("CANCEL_SCHEDULE");
                                }
                            }
                        } else {
                            log(logger, "⚠️ [HTTP POST 心跳] 伺服器回應異常，狀態碼：" + response.statusCode());
                            if (statusCallback != null) statusCallback.accept(false);
                        }
                    })
                    .exceptionally(ex -> {
                        log(logger, "❌ [HTTP POST 心跳失敗] " + ex.getMessage());
                        if (statusCallback != null) statusCallback.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            log(logger, "❌ [HTTP POST 發送異常] " + ex.getMessage());
            if (statusCallback != null) statusCallback.accept(false);
        }
    }

    public void sendHeartbeat(Consumer<String> logger, Consumer<Boolean> statusCallback) {
        sendPostHeartbeat(logger, statusCallback);
    }

    /**
     * 回傳打卡狀態與結果至伺服器
     */
    public void sendCheckinResult(boolean success, String message) {
        sendPostHeartbeat(null, null);
    }

    public void updateTaskStatus(String status, String scheduledTime) {
        updateTaskStatus(status, scheduledTime, null);
    }

    public void updateTaskStatus(String status, String scheduledTime, String message) {
        this.currentStatus = status;
        this.scheduledTime = scheduledTime;
        this.message = message;
    }

    public void stopHeartbeat() {
        this.isServiceActive = false;
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
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
