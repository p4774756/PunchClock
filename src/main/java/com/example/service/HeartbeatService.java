package com.example.service;

import com.example.model.CheckInTask;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 專責與 ping-pong-server 進行單向 HTTP POST 存活與多任務狀態上報
 */
public class HeartbeatService {

    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private ScheduledExecutorService scheduler;

    private String serverUrl = "";
    private String clientId = "company-worker";
    private String currentStatus = "ONLINE";
    private String message = null;
    private boolean isServiceActive = false;

    private Supplier<List<CheckInTask>> tasksProvider;
    private Consumer<String> commandListener;

    public void setCommandListener(Consumer<String> commandListener) {
        this.commandListener = commandListener;
    }

    public void setTasksProvider(Supplier<List<CheckInTask>> tasksProvider) {
        this.tasksProvider = tasksProvider;
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
        stopHeartbeat();

        if (serverUrl == null || serverUrl.isBlank()) {
            log(logger, "⚠️ [HTTP POST 服務] 伺服器網址為空，未開啟連線。");
            return;
        }

        this.serverUrl = formatServerUrl(serverUrl);
        this.isServiceActive = true;

        String endpoint = this.serverUrl + "/api/heartbeat";
        log(logger, "🔌 [HTTP POST 服務] 啟動單向心跳上報 (純 HTTP POST)：" + endpoint);

        scheduler = Executors.newScheduledThreadPool(1);

        sendPostHeartbeat(logger, statusCallback);

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
        List<CheckInTask> tasks = tasksProvider != null ? tasksProvider.get() : Collections.emptyList();

        // 使用 Gson 安全序列化 JSON
        List<Map<String, Object>> tasksList = new ArrayList<>();
        for (CheckInTask t : tasks) {
            Map<String, Object> taskMap = new LinkedHashMap<>();
            taskMap.put("id", t.getId());
            taskMap.put("name", t.getName());
            taskMap.put("targetUrl", t.getTargetUrl());
            taskMap.put("buttonId", t.getButtonId());
            taskMap.put("targetTime", t.getFormattedTargetTime());
            taskMap.put("actualTime", t.getFormattedActualTime());
            taskMap.put("useRandomOffset", t.isUseRandomOffset());
            taskMap.put("browserType", t.getBrowserType());
            taskMap.put("status", t.getStatus() != null ? t.getStatus().name() : "PENDING");
            taskMap.put("message", t.getResultMessage());
            tasksList.add(taskMap);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientId", clientId);
        payload.put("status", currentStatus);
        payload.put("message", message);
        payload.put("tasks", tasksList);

        String jsonBody = gson.toJson(payload);

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
                            parseServerCommand(response.body(), logger);
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

    /**
     * 使用 Gson 安全解析伺服器回應中的指令
     */
    private void parseServerCommand(String body, Consumer<String> logger) {
        if (body == null || body.isBlank() || commandListener == null) return;

        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("action")) return;

            String action = json.get("action").getAsString();
            if ("CANCEL_SCHEDULE".equals(action)) {
                log(logger, "🛑 [HTTP 心跳] 收到伺服器取消排程指令 (CANCEL_SCHEDULE)");
                commandListener.accept("CANCEL_SCHEDULE");
            } else if (action.startsWith("CANCEL_TASK:")) {
                log(logger, "🛑 [HTTP 心跳] 收到伺服器取消特定任務指令 (" + action + ")");
                commandListener.accept(action);
            }
        } catch (Exception ex) {
            // 回應非 JSON 或格式異常時靜默忽略（正常心跳回應可能無 action）
        }
    }

    public void sendHeartbeat(Consumer<String> logger, Consumer<Boolean> statusCallback) {
        sendPostHeartbeat(logger, statusCallback);
    }

    public void updateStatus(String status, String message) {
        this.currentStatus = status;
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

    private void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
