package com.example.service;

import com.example.AppVersion;
import com.example.PeerFileRules;
import com.example.model.CheckInTask;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private volatile String avatarEncoded = "";

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

    public void setAvatarEncoded(String encoded) {
        this.avatarEncoded = encoded != null ? encoded.trim() : "";
    }

    public String getAvatarEncoded() {
        return avatarEncoded;
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
        payload.put("avatar", avatarEncoded == null ? "" : avatarEncoded);

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
            // MSG|fromId|base64text
            // MSG|fromId|base64text|epochMs
            // MSG|fromId|base64text|epochMs|avatar
            String[] parts = action.split("\\|", 5);
            if (parts.length >= 3) {
                String fromId = parts[1];
                String text = decodePeerPayload(parts[2]);
                String sentAtMs = parts.length >= 4 ? parts[3].trim() : "";
                String avatar = parts.length >= 5 ? parts[4].trim() : "";
                log(logger, "[訊息] [戳] 收到來自【" + fromId + "】的訊息");
                commandListener.accept("MSG|" + fromId + "|" + sentAtMs + "|" + avatar + "|" + text);
            }
        } else if (action.startsWith("POKE|")) {
            // POKE|fromId 或 POKE|fromId|epochMs 或 POKE|fromId|epochMs|avatar
            String[] parts = action.split("\\|", 4);
            String fromId = parts.length > 1 && !parts[1].isBlank() ? parts[1] : "未知";
            String sentAtMs = parts.length >= 3 ? parts[2].trim() : "";
            String avatar = parts.length >= 4 ? parts[3].trim() : "";
            log(logger, "[通知] [戳] 【" + fromId + "】戳了你");
            commandListener.accept("POKE|" + fromId + "|" + sentAtMs + "|" + avatar);
        } else if (action.startsWith("FILE|")) {
            // FILE|fromId|fileId|base64name|size|mime|epochMs
            String[] parts = action.split("\\|", 7);
            if (parts.length >= 6) {
                String fromId = parts[1];
                String fileId = parts[2];
                String filename = PeerFileRules.decodeName(parts[3]);
                String size = parts[4];
                String mime = parts[5];
                String sentAtMs = parts.length >= 7 ? parts[6].trim() : "";
                log(logger, "[檔案] 收到來自【" + fromId + "】的檔案：" + filename);
                commandListener.accept(
                        "FILE|" + fromId + "|" + fileId + "|" + size + "|" + mime + "|" + sentAtMs + "|" + filename);
            }
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

        String trimmed = text.trim();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fromClientId", clientId);
        payload.put("toClientId", toClientId.trim());
        payload.put("text", trimmed);
        if (avatarEncoded != null && !avatarEncoded.isBlank()) {
            payload.put("avatar", avatarEncoded);
        }
        postPeerApi("/api/peer/message", payload, logger, callback,
                "訊息給【" + toClientId.trim() + "】：" + trimmed);
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
        if (avatarEncoded != null && !avatarEncoded.isBlank()) {
            payload.put("avatar", avatarEncoded);
        }
        postPeerApi("/api/peer/poke", payload, logger, callback,
                "戳一下給【" + toClientId.trim() + "】");
    }

    /**
     * 傳送檔案給同事（經伺服器暫存，對方心跳收到通知後再下載）。
     */
    public void sendPeerFile(String toClientId, Path file, Consumer<String> logger, Consumer<Boolean> callback) {
        if (!isServiceActive || serverUrl.isBlank()) {
            log(logger, "[警告] [檔案] 雲端未連線，無法傳送檔案");
            if (callback != null) callback.accept(false);
            return;
        }
        if (toClientId == null || toClientId.isBlank()) {
            log(logger, "[警告] [檔案] 請選擇收件同事");
            if (callback != null) callback.accept(false);
            return;
        }
        if (file == null || !Files.isRegularFile(file)) {
            log(logger, "[警告] [檔案] 找不到要傳送的檔案");
            if (callback != null) callback.accept(false);
            return;
        }

        String filename = PeerFileRules.sanitizeFilename(
                file.getFileName() != null ? file.getFileName().toString() : "");
        if (filename.isEmpty() || !PeerFileRules.isAllowedFilename(filename)) {
            log(logger, "[警告] [檔案] 不支援的檔案類型，請改傳 " + PeerFileRules.allowedTypesHint());
            if (callback != null) callback.accept(false);
            return;
        }

        byte[] bytes;
        try {
            long size = Files.size(file);
            if (!PeerFileRules.isAllowedSize(size)) {
                log(logger, size <= 0
                        ? "[警告] [檔案] 檔案不可為空"
                        : "[警告] [檔案] 檔案不可超過 " + PeerFileRules.MAX_SIZE_LABEL);
                if (callback != null) callback.accept(false);
                return;
            }
            bytes = Files.readAllBytes(file);
        } catch (Exception ex) {
            log(logger, "[失敗] [檔案] 讀取檔案失敗：" + ex.getMessage());
            if (callback != null) callback.accept(false);
            return;
        }

        String mime = PeerFileRules.mimeFor(filename);
        String boundary = "PunchClockFile" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipart(boundary, Map.of(
                "fromClientId", clientId,
                "toClientId", toClientId.trim(),
                "filename", filename
        ), filename, mime, bytes);

        String endpoint = serverUrl + "/api/peer/file";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Bearer " + heartbeatToken)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        boolean ok = response.statusCode() == 200;
                        if (ok) {
                            log(logger, "[成功] [檔案] 已送出「" + filename + "」給【" + toClientId.trim()
                                    + "】（" + PeerFileRules.formatSize(bytes.length) + "）");
                        } else {
                            String serverMessage = extractJsonMessage(response.body());
                            log(logger, "[失敗] [檔案] 送出「" + filename + "」失敗，狀態碼："
                                    + response.statusCode()
                                    + (serverMessage.isEmpty() ? "" : "，" + serverMessage));
                        }
                        if (callback != null) callback.accept(ok);
                    })
                    .exceptionally(ex -> {
                        log(logger, "[失敗] [檔案] 送出「" + filename + "」異常：" + ex.getMessage());
                        if (callback != null) callback.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            log(logger, "[失敗] [檔案] 送出「" + filename + "」異常：" + ex.getMessage());
            if (callback != null) callback.accept(false);
        }
    }

    /**
     * 下載同事傳來的檔案（需為收件人）。
     */
    public void downloadPeerFile(String fileId, Path destination,
                                 Consumer<String> logger, Consumer<Boolean> callback) {
        if (!isServiceActive || serverUrl.isBlank()) {
            log(logger, "[警告] [檔案] 雲端未連線，無法下載");
            if (callback != null) callback.accept(false);
            return;
        }
        if (fileId == null || fileId.isBlank() || destination == null) {
            log(logger, "[警告] [檔案] 缺少檔案編號或儲存路徑");
            if (callback != null) callback.accept(false);
            return;
        }

        String endpoint = serverUrl + "/api/peer/file/" + urlEncode(fileId.trim())
                + "?clientId=" + urlEncode(clientId);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + heartbeatToken)
                    .header("X-PunchClock-Client", clientId)
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            String serverMessage = extractJsonMessage(
                                    new String(response.body(), StandardCharsets.UTF_8));
                            log(logger, "[失敗] [檔案] 下載失敗，狀態碼：" + response.statusCode()
                                    + (serverMessage.isEmpty() ? "" : "，" + serverMessage));
                            if (callback != null) callback.accept(false);
                            return;
                        }
                        try {
                            Path parent = destination.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                            Files.write(destination, response.body());
                            log(logger, "[成功] [檔案] 已儲存：" + destination.toAbsolutePath()
                                    + "（" + PeerFileRules.formatSize(response.body().length) + "）");
                            if (callback != null) callback.accept(true);
                        } catch (Exception ex) {
                            log(logger, "[失敗] [檔案] 寫入本機失敗：" + ex.getMessage());
                            if (callback != null) callback.accept(false);
                        }
                    })
                    .exceptionally(ex -> {
                        log(logger, "[失敗] [檔案] 下載異常：" + ex.getMessage());
                        if (callback != null) callback.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            log(logger, "[失敗] [檔案] 下載異常：" + ex.getMessage());
            if (callback != null) callback.accept(false);
        }
    }

    private static byte[] buildMultipart(String boundary, Map<String, String> fields,
                                         String filename, String mime, byte[] fileBytes) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
            for (Map.Entry<String, String> field : fields.entrySet()) {
                out.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
                out.write(crlf);
                out.write(("Content-Disposition: form-data; name=\"" + field.getKey() + "\"")
                        .getBytes(StandardCharsets.UTF_8));
                out.write(crlf);
                out.write(crlf);
                out.write(field.getValue().getBytes(StandardCharsets.UTF_8));
                out.write(crlf);
            }
            out.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
            out.write(crlf);
            String asciiName = filename.replace("\"", "");
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + asciiName + "\"")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(crlf);
            out.write(("Content-Type: " + (mime == null || mime.isBlank() ? "application/octet-stream" : mime))
                    .getBytes(StandardCharsets.UTF_8));
            out.write(crlf);
            out.write(crlf);
            out.write(fileBytes);
            out.write(crlf);
            out.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
            out.write(crlf);
            return out.toByteArray();
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("無法組裝上傳內容", ex);
        }
    }

    private static String extractJsonMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("message") && json.get("message").isJsonPrimitive()) {
                String message = json.get("message").getAsString();
                return message != null ? message.trim() : "";
            }
        } catch (Exception ignored) {
            // 非 JSON 錯誤頁時忽略
        }
        return "";
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
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
                            log(logger, "[成功] [戳] 已送出" + actionLabel);
                        } else {
                            log(logger, "[失敗] [戳] 送出" + actionLabel + "失敗，狀態碼：" + response.statusCode());
                        }
                        if (callback != null) callback.accept(ok);
                    })
                    .exceptionally(ex -> {
                        log(logger, "[失敗] [戳] 送出" + actionLabel + "異常：" + ex.getMessage());
                        if (callback != null) callback.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            log(logger, "[失敗] [戳] 送出" + actionLabel + "異常：" + ex.getMessage());
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
