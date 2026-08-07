package com.example.service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 專責與 ping-pong-server 進行 WebSocket 雙向通訊與存活回報
 */
public class HeartbeatService {

    private final HttpClient httpClient;
    private ScheduledExecutorService scheduler;

    private WebSocket webSocket;
    private String serverUrl = "";
    private String clientId = "company-worker";
    private String currentStatus = "ONLINE";
    private String scheduledTime = null;
    private boolean isServiceActive = false;
    private boolean isConnected = false;

    private Runnable remoteTriggerHandler;

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

    public void setRemoteTriggerHandler(Runnable remoteTriggerHandler) {
        this.remoteTriggerHandler = remoteTriggerHandler;
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
     * 啟動 WebSocket 長連線與定期 Ping
     */
    public void startHeartbeat(String serverUrl, Consumer<String> logger, Consumer<Boolean> statusCallback) {
        stopHeartbeat(); // 安全清理舊連線

        if (serverUrl == null || serverUrl.isBlank()) {
            log(logger, "⚠️ [WebSocket 服務] 伺服器網址為空，未開啟連線。");
            return;
        }

        this.serverUrl = formatServerUrl(serverUrl);
        this.isServiceActive = true;

        String wsUrl = toWebSocketUri(this.serverUrl, clientId);
        log(logger, "🔌 [WebSocket 服務] 啟動長連線，連線至：" + wsUrl);

        scheduler = Executors.newScheduledThreadPool(2);

        // 嘗試建立 WebSocket 連線
        connectWebSocket(wsUrl, logger, statusCallback);

        // 每 15 秒檢查一次連線並發送 PING / 心跳
        scheduler.scheduleAtFixedRate(() -> {
            if (!isServiceActive) return;

            if (webSocket == null || !isConnected) {
                log(logger, "🔄 [WebSocket] 連線中斷，嘗試重新連線至：" + wsUrl + "...");
                connectWebSocket(wsUrl, logger, statusCallback);
            } else {
                sendPing(logger);
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    private void connectWebSocket(String wsUrl, Consumer<String> logger, Consumer<Boolean> statusCallback) {
        try {
            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        private final StringBuilder messageBuffer = new StringBuilder();

                        @Override
                        public void onOpen(WebSocket ws) {
                            webSocket = ws;
                            isConnected = true;
                            log(logger, "💚 [WebSocket] 已成功連線至伺服器！雙向控制已就緒。");
                            if (statusCallback != null) statusCallback.accept(true);
                            sendPing(logger);
                            ws.request(1);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            messageBuffer.append(data);
                            if (last) {
                                String fullMessage = messageBuffer.toString();
                                messageBuffer.setLength(0);
                                handleIncomingMessage(fullMessage, logger);
                            }
                            ws.request(1);
                            return null;
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                            isConnected = false;
                            webSocket = null;
                            log(logger, "🔴 [WebSocket] 連線關閉 (" + statusCode + ": " + reason + ")");
                            if (statusCallback != null) statusCallback.accept(false);
                            return null;
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            isConnected = false;
                            webSocket = null;
                            log(logger, "❌ [WebSocket 錯誤] " + error.getMessage());
                            if (statusCallback != null) statusCallback.accept(false);
                        }
                    });
        } catch (Exception ex) {
            isConnected = false;
            webSocket = null;
            log(logger, "❌ [WebSocket 連線異常] " + ex.getMessage());
            if (statusCallback != null) statusCallback.accept(false);
        }
    }

    public interface RemoteScheduleHandler {
        void onSchedule(String scheduledTime, String targetUrl, String buttonId);
    }

    private RemoteScheduleHandler remoteScheduleHandler;
    private Runnable remoteCancelHandler;

    public void setRemoteScheduleHandler(RemoteScheduleHandler handler) {
        this.remoteScheduleHandler = handler;
    }

    public void setRemoteCancelHandler(Runnable handler) {
        this.remoteCancelHandler = handler;
    }

    private void handleIncomingMessage(String message, Consumer<String> logger) {
        log(logger, "📩 [WebSocket 收到訊息] " + message);
        if (message.contains("\"type\":\"TRIGGER_CHECKIN\"")) {
            log(logger, "🚀 [遠端指令] 收到來自 Web 控制台的打卡指令！");
            if (remoteTriggerHandler != null) {
                remoteTriggerHandler.run();
            }
        } else if (message.contains("\"type\":\"CANCEL_SCHEDULE\"")) {
            log(logger, "🛑 [遠端指令] 收到來自 Web 控制台的取消排程指令！");
            if (remoteCancelHandler != null) {
                remoteCancelHandler.run();
            }
        } else if (message.contains("\"type\":\"START_SCHEDULE\"")) {
            log(logger, "🔔 [遠端指令] 收到來自 Web 控制台的設定排程指令！");
            String scheduledTime = extractJsonValue(message, "scheduledTime");
            String targetUrl = extractJsonValue(message, "targetUrl");
            String buttonId = extractJsonValue(message, "buttonId");
            if (remoteScheduleHandler != null) {
                remoteScheduleHandler.onSchedule(scheduledTime, targetUrl, buttonId);
            }
        }
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start == -1) return "";
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }

    public interface TaskDetailsProvider {
        String getTargetUrl();
        String getButtonId();
    }

    private TaskDetailsProvider taskDetailsProvider;

    public void setTaskDetailsProvider(TaskDetailsProvider taskDetailsProvider) {
        this.taskDetailsProvider = taskDetailsProvider;
    }

    private void sendPing(Consumer<String> logger) {
        if (webSocket != null && isConnected) {
            String targetUrl = (taskDetailsProvider != null) ? taskDetailsProvider.getTargetUrl() : "";
            String buttonId = (taskDetailsProvider != null) ? taskDetailsProvider.getButtonId() : "";

            String payload = String.format(
                    "{\"type\":\"PING\",\"clientId\":\"%s\",\"status\":\"%s\",\"scheduledTime\":%s,\"targetUrl\":\"%s\",\"buttonId\":\"%s\"}",
                    escapeJson(clientId),
                    escapeJson(currentStatus),
                    scheduledTime == null ? "null" : "\"" + escapeJson(scheduledTime) + "\"",
                    escapeJson(targetUrl),
                    escapeJson(buttonId)
            );
            webSocket.sendText(payload, true);
        }
    }

    /**
     * 回傳打卡執行結果至 WebSocket 伺服器
     */
    public void sendCheckinResult(boolean success, String message) {
        if (webSocket != null && isConnected) {
            String payload = String.format(
                    "{\"type\":\"CHECKIN_RESULT\",\"clientId\":\"%s\",\"success\":%b,\"message\":\"%s\"}",
                    escapeJson(clientId),
                    success,
                    escapeJson(message)
            );
            webSocket.sendText(payload, true);
        }
    }

    /**
     * 手動發送心跳 / 狀態更新
     */
    public void sendHeartbeat(Consumer<String> logger, Consumer<Boolean> statusCallback) {
        if (webSocket != null && isConnected) {
            sendPing(logger);
            if (statusCallback != null) statusCallback.accept(true);
        } else if (statusCallback != null) {
            statusCallback.accept(false);
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
     * 停止心跳與 WebSocket 服務
     */
    public void stopHeartbeat() {
        this.isServiceActive = false;
        this.isConnected = false;

        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "App closing");
            } catch (Exception ignored) {}
            webSocket = null;
        }

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    private String toWebSocketUri(String serverUrl, String clientId) {
        String trimmed = serverUrl.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String wsUrl;
        if (trimmed.startsWith("https://")) {
            wsUrl = "wss://" + trimmed.substring(8);
        } else if (trimmed.startsWith("http://")) {
            wsUrl = "ws://" + trimmed.substring(7);
        } else if (trimmed.startsWith("wss://") || trimmed.startsWith("ws://")) {
            wsUrl = trimmed;
        } else {
            wsUrl = "ws://" + trimmed;
        }
        return wsUrl + "/ws/client?clientId=" + clientId;
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

