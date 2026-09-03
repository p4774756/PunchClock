package com.example.service;

import com.example.AppVersion;
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
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 專責與 server 進行單向 HTTP POST 存活與多任務狀態上報
 */
public class HeartbeatService {

    /** 線上同事摘要（由心跳回應 peers[] 解析） */
    public static final class PeerInfo {
        public final String clientId;
        public final String status;
        public final String appVersion;
        public final int taskCount;
        public final int scheduledCount;
        public final String lastSeen;

        public PeerInfo(String clientId, String status, String appVersion,
                        int taskCount, int scheduledCount, String lastSeen) {
            this.clientId = clientId;
            this.status = status != null ? status : "UNKNOWN";
            this.appVersion = appVersion != null ? appVersion : "";
            this.taskCount = taskCount;
            this.scheduledCount = scheduledCount;
            this.lastSeen = lastSeen != null ? lastSeen : "";
        }
    }

    private volatile HttpClient httpClient;
    private final Gson gson = new Gson();
    private ScheduledExecutorService scheduler;

    private String serverUrl = "";
    private String clientId = "company-worker";
    private String heartbeatToken = "punchclock-dev-secret";
    private String currentStatus = "ONLINE";
    private String message = null;
    private boolean isServiceActive = false;
    private boolean trustAllSsl = false;

    private Supplier<List<CheckInTask>> tasksProvider;
    private Consumer<String> commandListener;
    private Consumer<List<PeerInfo>> peersListener;
    private final AtomicBoolean heartbeatInFlight = new AtomicBoolean(false);
    private final AtomicBoolean heartbeatPending = new AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicLong heartbeatSeq = new java.util.concurrent.atomic.AtomicLong(0);

    public void setCommandListener(Consumer<String> commandListener) {
        this.commandListener = commandListener;
    }

    public void setPeersListener(Consumer<List<PeerInfo>> peersListener) {
        this.peersListener = peersListener;
    }

    public void setTasksProvider(Supplier<List<CheckInTask>> tasksProvider) {
        this.tasksProvider = tasksProvider;
    }

    public HeartbeatService() {
        this.httpClient = buildHttpClient(false);
    }

    /**
     * 是否信任所有 SSL 憑證（僅建議本機除錯；預設 false）
     */
    public synchronized void setTrustAllSsl(boolean trustAllSsl) {
        if (this.trustAllSsl == trustAllSsl && httpClient != null) {
            return;
        }
        this.trustAllSsl = trustAllSsl;
        this.httpClient = buildHttpClient(trustAllSsl);
    }

    public boolean isTrustAllSsl() {
        return trustAllSsl;
    }

    private static HttpClient buildHttpClient(boolean trustAllSsl) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(8));

        if (trustAllSsl) {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
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
        }

        return builder.build();
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String newClientId) {
        if (newClientId != null && !newClientId.trim().isEmpty()) {
            this.clientId = newClientId.trim();
        }
    }

    public void setHeartbeatToken(String token) {
        if (token != null && !token.trim().isEmpty()) {
            this.heartbeatToken = token.trim();
        }
    }

    /**
     * 啟動定期單向 HTTP POST 心跳
     */
    public void startHeartbeat(String serverUrl, Consumer<String> logger, Consumer<Boolean> statusCallback) {
        stopHeartbeat();

        if (serverUrl == null || serverUrl.isBlank()) {
            log(logger, "[警告] [HTTP POST 服務] 伺服器網址為空，未開啟連線。");
            return;
        }

        this.serverUrl = formatServerUrl(serverUrl);
        this.isServiceActive = true;

        String endpoint = this.serverUrl + "/api/heartbeat";
        log(logger, "[連線] [HTTP POST 服務] 啟動單向心跳上報 (純 HTTP POST)：" + endpoint
                + (trustAllSsl ? " [SSL 信任全部憑證]" : ""));

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

        if (!heartbeatInFlight.compareAndSet(false, true)) {
            heartbeatPending.set(true);
            return;
        }

        String endpoint = serverUrl + "/api/heartbeat";
        List<CheckInTask> tasks = tasksProvider != null ? tasksProvider.get() : Collections.emptyList();

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
        payload.put("appVersion", AppVersion.VERSION);
        payload.put("tasks", tasksList);
        payload.put("heartbeatSeq", heartbeatSeq.incrementAndGet());

        String jsonBody = gson.toJson(payload);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + heartbeatToken)
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((response, ex) -> {
                        try {
                            if (ex != null) {
                                log(logger, "[失敗] [HTTP POST 心跳失敗] " + ex.getMessage());
                                if (statusCallback != null) statusCallback.accept(false);
                            } else if (response.statusCode() == 200) {
                                if (statusCallback != null) statusCallback.accept(true);
                                parseHeartbeatResponse(response.body(), logger);
                            } else {
                                log(logger, "[警告] [HTTP POST 心跳] 伺服器回應異常，狀態碼：" + response.statusCode());
                                if (statusCallback != null) statusCallback.accept(false);
                            }
                        } finally {
                            finishHeartbeatSend(logger, statusCallback);
                        }
                    });
        } catch (Exception ex) {
            log(logger, "[失敗] [HTTP POST 發送異常] " + ex.getMessage());
            if (statusCallback != null) statusCallback.accept(false);
            finishHeartbeatSend(logger, statusCallback);
        }
    }

    private void finishHeartbeatSend(Consumer<String> logger, Consumer<Boolean> statusCallback) {
        heartbeatInFlight.set(false);
        if (heartbeatPending.getAndSet(false)) {
            sendPostHeartbeat(logger, statusCallback);
        }
    }

    /**
     * 使用 Gson 安全解析伺服器回應中的指令與同事列表
     * 協定：優先讀取 actions[]，並相容舊版單一 action 欄位；peers[] 為其他連線裝置
     */
    private void parseHeartbeatResponse(String body, Consumer<String> logger) {
        if (body == null || body.isBlank()) return;

        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            parseServerActions(json, logger);
            parseServerPeers(json);
        } catch (Exception ex) {
            // 回應非 JSON 或格式異常時靜默忽略
        }
    }

    private void parseServerActions(JsonObject json, Consumer<String> logger) {
        if (commandListener == null) return;

        java.util.LinkedHashSet<String> actions = new java.util.LinkedHashSet<>();

        if (json.has("actions") && json.get("actions").isJsonArray()) {
            for (com.google.gson.JsonElement el : json.getAsJsonArray("actions")) {
                if (el != null && el.isJsonPrimitive()) {
                    String a = el.getAsString();
                    if (a != null && !a.isBlank() && !"NONE".equalsIgnoreCase(a)) {
                        actions.add(a.trim());
                    }
                }
            }
        } else if (json.has("action") && json.get("action").isJsonPrimitive()) {
            String action = json.get("action").getAsString();
            if (action != null && !action.isBlank() && !"NONE".equalsIgnoreCase(action)) {
                actions.add(action.trim());
            }
        }

        for (String action : actions) {
            dispatchServerAction(action, logger);
        }
    }

    private void dispatchServerAction(String action, Consumer<String> logger) {
        if ("CANCEL_SCHEDULE".equals(action)) {
            log(logger, "[取消] [HTTP 心跳] 收到伺服器取消排程指令 (CANCEL_SCHEDULE)");
            commandListener.accept("CANCEL_SCHEDULE");
        } else if (action.startsWith("CANCEL_TASK:")) {
            log(logger, "[取消] [HTTP 心跳] 收到伺服器取消特定任務指令 (" + action + ")");
            commandListener.accept(action);
        } else if (action.startsWith("MSG|")) {
            // MSG|fromId|base64text 或 MSG|fromId|base64text|epochMs
            String[] parts = action.split("\\|", 4);
            if (parts.length >= 3) {
                String fromId = parts[1];
                String text = decodePeerPayload(parts[2]);
                String sentAtMs = parts.length >= 4 ? parts[3].trim() : "";
                log(logger, "[訊息] [戳] 收到來自【" + fromId + "】的訊息");
                commandListener.accept("MSG|" + fromId + "|" + sentAtMs + "|" + text);
            }
        } else if (action.startsWith("POKE|")) {
            // POKE|fromId 或 POKE|fromId|epochMs
            String[] parts = action.split("\\|", 3);
            String fromId = parts.length > 1 && !parts[1].isBlank() ? parts[1] : "未知";
            String sentAtMs = parts.length >= 3 ? parts[2].trim() : "";
            log(logger, "[通知] [戳] 【" + fromId + "】戳了你");
            commandListener.accept("POKE|" + fromId + "|" + sentAtMs);
        } else {
            log(logger, "[警告] [HTTP 心跳] 收到未支援的遠端指令: " + action);
        }
    }

    private void parseServerPeers(JsonObject json) {
        if (peersListener == null || !json.has("peers") || !json.get("peers").isJsonArray()) {
            return;
        }
        List<PeerInfo> peers = new ArrayList<>();
        for (com.google.gson.JsonElement el : json.getAsJsonArray("peers")) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject p = el.getAsJsonObject();
            String id = p.has("clientId") ? p.get("clientId").getAsString() : "";
            if (id.isBlank()) continue;
            String status = p.has("status") ? p.get("status").getAsString() : "UNKNOWN";
            String appVersion = p.has("appVersion") ? p.get("appVersion").getAsString() : "";
            int taskCount = p.has("taskCount") ? p.get("taskCount").getAsInt() : 0;
            int scheduledCount = p.has("scheduledCount") ? p.get("scheduledCount").getAsInt() : 0;
            String lastSeen = p.has("lastSeen") ? p.get("lastSeen").getAsString() : "";
            peers.add(new PeerInfo(id, status, appVersion, taskCount, scheduledCount, lastSeen));
        }
        peersListener.accept(peers);
    }

    private static String decodePeerPayload(String base64url) {
        if (base64url == null || base64url.isBlank()) return "";
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(base64url);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return base64url;
        }
    }

    /**
     * 傳送訊息給同事（經伺服器中繼）
     */
    public void sendPeerMessage(String toClientId, String text, Consumer<String> logger, Consumer<Boolean> callback) {
        if (!isServiceActive || serverUrl.isBlank()) {
            log(logger, "[警告] [戳] 雲端未連線，無法傳送訊息");
            if (callback != null) callback.accept(false);
            return;
        }
        if (toClientId == null || toClientId.isBlank()) {
            log(logger, "[警告] [戳] 請選擇收件同事");
            if (callback != null) callback.accept(false);
            return;
        }
        if (text == null || text.trim().isEmpty()) {
            log(logger, "[警告] [戳] 訊息不可為空");
            if (callback != null) callback.accept(false);
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromClientId", clientId);
        payload.put("toClientId", toClientId.trim());
        payload.put("text", text.trim());
        postPeerApi("/api/peer/message", payload, logger, callback, "傳送訊息");
    }

    /**
     * 戳一下同事（經伺服器中繼）
     */
    public void sendPeerPoke(String toClientId, Consumer<String> logger, Consumer<Boolean> callback) {
        if (!isServiceActive || serverUrl.isBlank()) {
            log(logger, "[警告] [戳] 雲端未連線，無法戳同事");
            if (callback != null) callback.accept(false);
            return;
        }
        if (toClientId == null || toClientId.isBlank()) {
            log(logger, "[警告] [戳] 請選擇同事");
            if (callback != null) callback.accept(false);
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromClientId", clientId);
        payload.put("toClientId", toClientId.trim());
        postPeerApi("/api/peer/poke", payload, logger, callback, "戳一下");
    }

    private void postPeerApi(String path, Map<String, Object> payload,
                             Consumer<String> logger, Consumer<Boolean> callback, String actionLabel) {
        String endpoint = serverUrl + path;
        String jsonBody = gson.toJson(payload);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + heartbeatToken)
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        boolean ok = response.statusCode() == 200;
                        if (ok) {
                            log(logger, "[成功] [戳] " + actionLabel + "已送出");
                        } else {
                            log(logger, "[失敗] [戳] " + actionLabel + "失敗，狀態碼：" + response.statusCode());
                        }
                        if (callback != null) callback.accept(ok);
                    })
                    .exceptionally(ex -> {
                        log(logger, "[失敗] [戳] " + actionLabel + "異常：" + ex.getMessage());
                        if (callback != null) callback.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            log(logger, "[失敗] [戳] " + actionLabel + "異常：" + ex.getMessage());
            if (callback != null) callback.accept(false);
        }
    }

    /** @deprecated 僅供測試反射呼叫；請使用 parseHeartbeatResponse */
    @SuppressWarnings("unused")
    private void parseServerCommand(String body, Consumer<String> logger) {
        parseHeartbeatResponse(body, logger);
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
        heartbeatInFlight.set(false);
        heartbeatPending.set(false);
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
