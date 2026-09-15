package com.example.service;

import com.example.AppVersion;
import com.example.PeerFileRules;
import com.example.PeerFolderPacker;
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
 * 專責與 server 進行單向 HTTP POST 存活與多任務狀態上報。
 * 打卡結果存在任務的 lastResult 欄位，跟著一般心跳送；timeout 就等下次 15 秒心跳再帶一次。
 */
public class HeartbeatService {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    /** 單檔 100 MB 在較慢網路上需要比一般心跳更長的逾時。 */
    static final Duration FILE_TRANSFER_TIMEOUT = Duration.ofMinutes(10);

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

    /** 傳檔紀錄摘要（由心跳回應 files[] 解析） */
    public static final class PeerFileInfo {
        public final String fileId;
        public final String fromClientId;
        public final String toClientId;
        public final String filename;
        public final String mime;
        public final String kind;
        public final String status;
        public final long size;
        public final long createdAtMs;
        public final long expiresAtMs;
        public final int downloadCount;

        public PeerFileInfo(String fileId, String fromClientId, String toClientId,
                            String filename, String mime, String kind, String status,
                            long size, long createdAtMs, long expiresAtMs, int downloadCount) {
            this.fileId = fileId != null ? fileId : "";
            this.fromClientId = fromClientId != null ? fromClientId : "";
            this.toClientId = toClientId != null ? toClientId : "";
            this.filename = filename != null ? filename : "";
            this.mime = mime != null ? mime : "";
            this.kind = kind != null ? kind : PeerFileRules.KIND_FILE;
            this.status = status != null ? status : "waiting";
            this.size = size;
            this.createdAtMs = createdAtMs;
            this.expiresAtMs = expiresAtMs;
            this.downloadCount = downloadCount;
        }

        public boolean isFolder() {
            return PeerFileRules.isFolderKind(kind);
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
    private Consumer<List<PeerFileInfo>> filesListener;
    private final AtomicBoolean heartbeatInFlight = new AtomicBoolean(false);
    private final AtomicBoolean heartbeatPending = new AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicLong heartbeatSeq = new java.util.concurrent.atomic.AtomicLong(0);

    public void setCommandListener(Consumer<String> commandListener) {
        this.commandListener = commandListener;
    }

    public void setPeersListener(Consumer<List<PeerInfo>> peersListener) {
        this.peersListener = peersListener;
    }

    public void setFilesListener(Consumer<List<PeerFileInfo>> filesListener) {
        this.filesListener = filesListener;
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

    /** 套用 JVM Proxy 屬性後重建客戶端，讓後續心跳走新的 ProxySelector。 */
    public synchronized void refreshHttpClient() {
        this.httpClient = buildHttpClient(trustAllSsl);
    }

    public boolean isTrustAllSsl() {
        return trustAllSsl;
    }

    private static HttpClient buildHttpClient(boolean trustAllSsl) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT);

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
        String normalized = PeerFileRules.normalizeClientId(newClientId);
        if (!normalized.isEmpty()) {
            this.clientId = normalized;
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
            tasksList.add(toTaskPayload(t));
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
                    .timeout(REQUEST_TIMEOUT)
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

    static Map<String, Object> toTaskPayload(CheckInTask t) {
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
        if (t.getLastResultStatus() != null) {
            taskMap.put("lastResultStatus", t.getLastResultStatus().name());
            taskMap.put("lastResultMessage", t.getLastResultMessage());
        }
        return taskMap;
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
            parseServerFiles(json);
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
            // FILE|fromId|fileId|base64name|size|mime|epochMs（fromId 可含 |）
            String[] parts = splitPeerFileAction(action);
            if (parts.length == 6) {
                String fromId = parts[0];
                String fileId = parts[1];
                String filename = PeerFileRules.decodeName(parts[2]);
                String size = parts[3];
                String mime = parts[4];
                String sentAtMs = parts[5];
                log(logger, "[檔案] 收到來自【" + fromId + "】的檔案：" + filename);
                commandListener.accept(
                        "FILE|" + PeerFileRules.encodeName(fromId) + "|" + fileId + "|" + size
                                + "|" + mime + "|" + sentAtMs + "|" + filename);
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

    private void parseServerFiles(JsonObject json) {
        if (filesListener == null || !json.has("files") || !json.get("files").isJsonArray()) {
            return;
        }
        List<PeerFileInfo> files = new ArrayList<>();
        for (com.google.gson.JsonElement el : json.getAsJsonArray("files")) {
            if (el == null || !el.isJsonObject()) continue;
            JsonObject f = el.getAsJsonObject();
            String fileId = jsonString(f, "fileId");
            if (fileId.isBlank()) continue;
            files.add(new PeerFileInfo(
                    fileId,
                    jsonString(f, "fromClientId"),
                    jsonString(f, "toClientId"),
                    jsonString(f, "filename"),
                    jsonString(f, "mime"),
                    jsonString(f, "kind"),
                    jsonString(f, "status"),
                    jsonLong(f, "size"),
                    jsonLong(f, "createdAtMs"),
                    jsonLong(f, "expiresAtMs"),
                    (int) jsonLong(f, "downloadCount")
            ));
        }
        filesListener.accept(files);
    }

    private static String jsonString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return "";
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ex) {
            return String.valueOf(obj.get(key));
        }
    }

    private static long jsonLong(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return 0L;
        }
        try {
            return obj.get(key).getAsLong();
        } catch (Exception ex) {
            return 0L;
        }
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
     * 傳送檔案或資料夾給同事（資料夾會先壓成 ZIP；經伺服器暫存，對方心跳收到通知後再下載）。
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
        if (file == null) {
            log(logger, "[警告] [檔案] 找不到要傳送的檔案");
            if (callback != null) callback.accept(false);
            return;
        }

        String filename;
        byte[] bytes;
        String kind = PeerFileRules.KIND_FILE;
        try {
            if (Files.isDirectory(file)) {
                PeerFolderPacker.PackResult packed = PeerFolderPacker.pack(file);
                if (!packed.ok) {
                    log(logger, "[警告] [檔案] " + packed.message);
                    if (callback != null) callback.accept(false);
                    return;
                }
                filename = packed.filename;
                bytes = packed.bytes;
                kind = PeerFileRules.KIND_FOLDER;
                log(logger, "[檔案] 正在傳送資料夾「" + file.getFileName() + "」（壓縮 "
                        + PeerFileRules.formatSize(bytes.length) + "）");
            } else if (Files.isRegularFile(file)) {
                filename = PeerFileRules.sanitizeFilename(
                        file.getFileName() != null ? file.getFileName().toString() : "");
                if (filename.isEmpty()) {
                    log(logger, "[警告] [檔案] 檔名無效");
                    if (callback != null) callback.accept(false);
                    return;
                }
                long size = Files.size(file);
                if (!PeerFileRules.isAllowedSize(size)) {
                    log(logger, size <= 0
                            ? "[警告] [檔案] 檔案不可為空"
                            : "[警告] [檔案] 檔案不可超過 " + PeerFileRules.MAX_SIZE_LABEL);
                    if (callback != null) callback.accept(false);
                    return;
                }
                bytes = Files.readAllBytes(file);
            } else {
                log(logger, "[警告] [檔案] 找不到要傳送的檔案");
                if (callback != null) callback.accept(false);
                return;
            }
        } catch (Exception ex) {
            log(logger, "[失敗] [檔案] 讀取檔案失敗：" + ex.getMessage());
            if (callback != null) callback.accept(false);
            return;
        }

        String mime = PeerFileRules.isFolderKind(kind) ? "application/zip" : PeerFileRules.mimeFor(filename);
        String boundary = "PunchClockFile" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipart(boundary, orderedFields(
                "fromClientId", clientId,
                "toClientId", PeerFileRules.normalizeClientId(toClientId),
                "filename", filename,
                "kind", kind
        ), filename, mime, bytes);

        String endpoint = serverUrl + "/api/peer/file";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Bearer " + heartbeatToken)
                    .timeout(FILE_TRANSFER_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        boolean ok = response.statusCode() == 200;
                        if (ok) {
                            log(logger, "[成功] [檔案] 已送出「" + filename + "」給【" + toClientId.trim()
                                    + "】（" + PeerFileRules.formatSize(bytes.length)
                                    + "，保留 " + PeerFileRules.OFFER_TTL_LABEL + "）");
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
     * 下載同事傳來的檔案（收件人或發送者在過期前皆可）。
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

        Path dest = PeerFileRules.resolveSavePath(destination, destination.getFileName() != null
                ? destination.getFileName().toString() : "download");
        String endpoint = serverUrl + "/api/peer/file/" + urlEncode(fileId.trim())
                + "?clientId=" + urlEncode(clientId);
        try {
            sendGetPreservingAuth(URI.create(endpoint), logger)
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
                            Path parent = dest.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                            Files.write(dest, response.body());
                            log(logger, "[成功] [檔案] 已儲存：" + dest.toAbsolutePath()
                                    + "（" + PeerFileRules.formatSize(response.body().length) + "）");
                            if (callback != null) callback.accept(true);
                        } catch (Exception ex) {
                            log(logger, "[失敗] [檔案] 寫入本機失敗：" + ex.getMessage());
                            if (callback != null) callback.accept(false);
                        }
                    })
                    .exceptionally(ex -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        log(logger, "[失敗] [檔案] 下載異常：" + cause.getMessage());
                        if (callback != null) callback.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            log(logger, "[失敗] [檔案] 下載異常：" + ex.getMessage());
            if (callback != null) callback.accept(false);
        }
    }

    /**
     * 手動清除伺服器上的暫存檔（發送者或收件人）。
     */
    public void deletePeerFile(String fileId, Consumer<String> logger, Consumer<Boolean> callback) {
        if (!isServiceActive || serverUrl.isBlank()) {
            log(logger, "[警告] [檔案] 雲端未連線，無法清除");
            if (callback != null) callback.accept(false);
            return;
        }
        if (fileId == null || fileId.isBlank()) {
            log(logger, "[警告] [檔案] 缺少檔案編號");
            if (callback != null) callback.accept(false);
            return;
        }
        String endpoint = serverUrl + "/api/peer/file/" + urlEncode(fileId.trim())
                + "?clientId=" + urlEncode(clientId);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + heartbeatToken)
                    .timeout(REQUEST_TIMEOUT)
                    .DELETE();
            String encodedClient = urlEncode(clientId);
            if (isAsciiHeaderValue(encodedClient)) {
                builder.header("X-PunchClock-Client", encodedClient);
            }
            httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        boolean ok = response.statusCode() == 200;
                        String serverMessage = extractJsonMessage(response.body());
                        if (ok) {
                            log(logger, "[成功] [檔案] " + (serverMessage.isEmpty() ? "已清除暫存檔" : serverMessage));
                        } else {
                            log(logger, "[失敗] [檔案] 清除失敗，狀態碼：" + response.statusCode()
                                    + (serverMessage.isEmpty() ? "" : "，" + serverMessage));
                        }
                        if (callback != null) callback.accept(ok);
                    })
                    .exceptionally(ex -> {
                        log(logger, "[失敗] [檔案] 清除異常：" + ex.getMessage());
                        if (callback != null) callback.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            log(logger, "[失敗] [檔案] 清除異常：" + ex.getMessage());
            if (callback != null) callback.accept(false);
        }
    }

    private HttpRequest buildDownloadRequest(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + heartbeatToken)
                .timeout(FILE_TRANSFER_TIMEOUT)
                .GET();
        // JDK 17+（Mac 常見）不接受非 ASCII header；Worker ID 只放 URL-safe 編碼。
        String encodedClient = urlEncode(clientId);
        if (isAsciiHeaderValue(encodedClient)) {
            builder.header("X-PunchClock-Client", encodedClient);
        }
        return builder.build();
    }

    private java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> sendGetPreservingAuth(
            URI uri, Consumer<String> logger) {
        return httpClient.sendAsync(buildDownloadRequest(uri), HttpResponse.BodyHandlers.ofByteArray())
                .thenCompose(response -> {
                    int code = response.statusCode();
                    if (code < 300 || code >= 400) {
                        return java.util.concurrent.CompletableFuture.completedFuture(response);
                    }
                    String location = response.headers().firstValue("Location").orElse("").trim();
                    if (location.isEmpty()) {
                        return java.util.concurrent.CompletableFuture.completedFuture(response);
                    }
                    URI next;
                    try {
                        next = uri.resolve(location);
                        if ((next.getRawQuery() == null || next.getRawQuery().isEmpty())
                                && uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
                            String rebuilt = next.getScheme() + "://" + next.getRawAuthority()
                                    + next.getRawPath() + "?" + uri.getRawQuery();
                            if (next.getRawFragment() != null) {
                                rebuilt += "#" + next.getRawFragment();
                            }
                            next = URI.create(rebuilt);
                        }
                    } catch (Exception ex) {
                        return java.util.concurrent.CompletableFuture.completedFuture(response);
                    }
                    if (!sameOrigin(uri, next)) {
                        log(logger, "[警告] [檔案] 下載轉址跨網域，已中止：" + next);
                        return java.util.concurrent.CompletableFuture.completedFuture(response);
                    }
                    return httpClient.sendAsync(buildDownloadRequest(next), HttpResponse.BodyHandlers.ofByteArray());
                });
    }

    private static boolean sameOrigin(URI from, URI to) {
        if (from == null || to == null || to.getHost() == null) {
            return false;
        }
        String fromHost = from.getHost() == null ? "" : from.getHost();
        if (!fromHost.equalsIgnoreCase(to.getHost())) {
            return false;
        }
        return effectivePort(from) == effectivePort(to)
                && java.util.Objects.equals(from.getScheme(), to.getScheme());
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean isAsciiHeaderValue(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c > 0x7e) {
                return false;
            }
        }
        return true;
    }

    static String[] splitPeerFileAction(String action) {
        if (action == null || !action.startsWith("FILE|")) {
            return new String[0];
        }
        String[] parts = action.split("\\|", -1);
        if (parts.length < 7) {
            return new String[0];
        }
        String sentAtMs = parts[parts.length - 1].trim();
        String mime = parts[parts.length - 2];
        String size = parts[parts.length - 3];
        String encodedName = parts[parts.length - 4];
        String fileId = parts[parts.length - 5];
        String fromId = String.join("|", java.util.Arrays.copyOfRange(parts, 1, parts.length - 5));
        return new String[]{fromId, fileId, encodedName, size, mime, sentAtMs};
    }

    private static Map<String, String> orderedFields(String... keyValues) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            fields.put(keyValues[i], keyValues[i + 1]);
        }
        return fields;
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
                out.write("Content-Type: text/plain; charset=UTF-8".getBytes(StandardCharsets.UTF_8));
                out.write(crlf);
                out.write(crlf);
                out.write(field.getValue().getBytes(StandardCharsets.UTF_8));
                out.write(crlf);
            }
            out.write(("--" + boundary).getBytes(StandardCharsets.UTF_8));
            out.write(crlf);
            String asciiName = asciiMultipartFilename(filename);
            String encodedName = urlEncode(filename == null ? "" : filename).replace("+", "%20");
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + asciiName
                    + "\"; filename*=UTF-8''" + encodedName).getBytes(StandardCharsets.UTF_8));
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

    private static String asciiMultipartFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "download";
        }
        StringBuilder sb = new StringBuilder(filename.length());
        for (int i = 0; i < filename.length(); i++) {
            char c = filename.charAt(i);
            if (c >= 0x20 && c < 0x7f && c != '"' && c != '\\') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String ascii = sb.toString().trim();
        return ascii.isEmpty() ? "download" : ascii;
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
                    .timeout(REQUEST_TIMEOUT)
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
