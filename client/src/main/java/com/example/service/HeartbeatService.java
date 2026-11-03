package com.example.service;

import com.example.AppVersion;
import com.example.MarqueeNotice;
import com.example.PeerFileRules;
import com.example.model.CheckInTask;
import com.google.gson.Gson;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 專責與 server 進行單向 HTTP POST 存活與多任務狀態上報。
 * 打卡結果存在任務的 lastResult 欄位，跟著一般心跳送；timeout 就等下次 15 秒心跳再帶一次。
 * 同事訊息見 {@link PeerMessageClient}，傳檔與直傳見 {@link PeerFileClient}，
 * 回應解析見 {@link HeartbeatResponseParser}。
 */
public class HeartbeatService implements HeartbeatEvents {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    /** 單檔可達 300 MB，較慢網路需要比一般心跳更長的逾時。 */
    static final Duration FILE_TRANSFER_TIMEOUT = Duration.ofMinutes(30);

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

    /** 心跳回應裡的桌面端更新通知。path 只接受伺服器上的固定下載路徑。 */
    public static final class UpdateNotice {
        public final String version;
        public final String sourceDigest;
        /** 伺服器上那份桌面端的建置（或 commit）時間，ISO-8601 UTC；沒帶為空字串。 */
        public final String releaseTime;
        public final String sha256;
        public final long size;
        public final String path;
        /** 比目前這份桌面端新的提交說明；舊伺服器沒帶時為空。 */
        public final List<String> changes;
        /** {@link #changes} 之外還有幾筆沒列出。 */
        public final int changesMore;

        public UpdateNotice(String version, String sourceDigest, String sha256, long size, String path) {
            this(version, sourceDigest, "", sha256, size, path);
        }

        public UpdateNotice(String version, String sourceDigest, String releaseTime, String sha256,
                            long size, String path) {
            this(version, sourceDigest, releaseTime, sha256, size, path, List.of(), 0);
        }

        public UpdateNotice(String version, String sourceDigest, String releaseTime, String sha256,
                            long size, String path, List<String> changes, int changesMore) {
            this.version = version == null ? "" : version;
            this.sourceDigest = sourceDigest == null ? "" : sourceDigest;
            this.releaseTime = releaseTime == null ? "" : releaseTime;
            this.sha256 = sha256 == null ? "" : sha256;
            this.size = size;
            this.path = path == null ? "" : path;
            this.changes = changes == null || changes.isEmpty() ? List.of() : List.copyOf(changes);
            this.changesMore = Math.max(0, changesMore);
        }
    }

    /** 即時直傳的結果。對方不在線或伺服器不支援時不會改走暫存。 */
    public enum RelayOutcome { DELIVERED, FAILED, CANCELLED, UNSUPPORTED_SERVER, RECIPIENT_UNAVAILABLE }

    private volatile HttpClient httpClient;
    private final Gson gson = new Gson();
    private final PeerFileClient peerFiles = new PeerFileClient(this);
    private final PeerMessageClient peerMessages = new PeerMessageClient(this);
    private final HeartbeatResponseParser responses = new HeartbeatResponseParser(this);
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
    private Consumer<UpdateNotice> updateListener;
    private Consumer<MarqueeNotice> marqueeListener;
    private final AtomicBoolean heartbeatInFlight = new AtomicBoolean(false);
    private final AtomicBoolean heartbeatPending = new AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicLong heartbeatSeq = new java.util.concurrent.atomic.AtomicLong(0);

    public void setCommandListener(Consumer<String> commandListener) {
        this.commandListener = commandListener;
    }

    public void setPeersListener(Consumer<List<PeerInfo>> peersListener) {
        this.peersListener = peersListener;
    }

    public void setUpdateListener(Consumer<UpdateNotice> updateListener) {
        this.updateListener = updateListener;
    }

    public void setMarqueeListener(Consumer<MarqueeNotice> marqueeListener) {
        this.marqueeListener = marqueeListener;
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

    static HttpClient buildHttpClient(boolean trustAllSsl) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(CONNECT_TIMEOUT);

        if (trustAllSsl) {
            SSLContext sc = trustAllSslContext();
            if (sc != null) {
                builder.sslContext(sc);
            }
        }

        return builder.build();
    }

    /** 不驗證憑證的 SSLContext（公司 SSL 攔截除錯用）；初始化失敗回 null。 */
    static SSLContext trustAllSslContext() {
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
            return sc;
        } catch (Exception e) {
            System.err.println("初始化 SSL 繞過失敗: " + e.getMessage());
            return null;
        }
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
        payload.put("appReleaseTime", AppVersion.RELEASE_TIME_ISO);
        payload.put("appSourceDigest", AppVersion.SOURCE_DIGEST);
        payload.put("tasks", tasksList);
        payload.put("heartbeatSeq", heartbeatSeq.incrementAndGet());
        payload.put("avatar", avatarEncoded == null ? "" : avatarEncoded);
        payload.put("capabilities", List.of(PeerFileRules.CAPABILITY_RELAY));

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
                                responses.parse(response.body(), logger);
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



    /** 供測試與心跳回應解析共用。格式不符時回 null。 */
    public static UpdateNotice parseUpdateNotice(String body) {
        return HeartbeatResponseParser.parseUpdateNotice(body);
    }

    public static String displayNameOf(List<Path> files) {
        if (files == null || files.isEmpty()) {
            return "檔案";
        }
        if (files.size() > 1) {
            return files.size() + " 個項目";
        }
        Path only = files.get(0);
        return only != null && only.getFileName() != null ? only.getFileName().toString() : "檔案";
    }


    static String describeTransferFailure(Throwable ex) {
        Throwable t = ex;
        while (t != null && t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t == null || t.getMessage() == null || t.getMessage().isBlank()
                ? String.valueOf(ex)
                : t.getMessage().trim();
        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("header parser received no bytes") || lower.contains("received no bytes")) {
            return "連線在伺服器回應前被關閉（常見：雲端記憶體不足崩潰、服務重啟或上傳中斷）。請看 Render log。"
                    + " 原始錯誤：" + msg;
        }
        return msg;
    }




    public void sendPeerMessage(String toClientId, String text, Consumer<String> logger, Consumer<Boolean> callback) {
        peerMessages.sendPeerMessage(toClientId, text, logger, callback);
    }

    public void sendPeerMessage(String toClientId, String text, String imageEncoded,
                                Consumer<String> logger, Consumer<Boolean> callback) {
        peerMessages.sendPeerMessage(toClientId, text, imageEncoded, logger, callback);
    }

    public void sendPeerPoke(String toClientId, Consumer<String> logger, Consumer<Boolean> callback) {
        peerMessages.sendPeerPoke(toClientId, logger, callback);
    }

    public void sendPeerFileRelay(String toClientId, Path file, Consumer<String> logger,
                                  TransferIo.Progress progress, Consumer<String> statusUpdate,
                                  TransferCancel cancel, Consumer<RelayOutcome> callback) {
        peerFiles.sendPeerFileRelay(toClientId, file, logger, progress, statusUpdate, cancel, callback);
    }

    public void sendPeerFileRelay(String toClientId, List<Path> files, Consumer<String> logger,
                                  TransferIo.Progress progress, Consumer<String> statusUpdate,
                                  TransferCancel cancel, Consumer<RelayOutcome> callback) {
        peerFiles.sendPeerFileRelay(toClientId, files, logger, progress, statusUpdate, cancel, callback);
    }

    public void receivePeerRelay(String relayId, Path destination, Consumer<String> logger,
                                 TransferIo.Progress progress, Consumer<String> statusUpdate,
                                 TransferCancel cancel, Consumer<Boolean> callback) {
        peerFiles.receivePeerRelay(relayId, destination, logger, progress, statusUpdate, cancel, callback);
    }

    public void declinePeerRelay(String relayId, Consumer<String> logger) {
        peerFiles.declinePeerRelay(relayId, logger);
    }

    public Path downloadClientUpdate(String path) throws IOException, InterruptedException {
        return peerFiles.downloadClientUpdate(path);
    }

    public Path downloadClientUpdate(String path, long expectedSize, TransferIo.Progress progress,
                                     TransferCancel cancel) throws IOException, InterruptedException {
        return peerFiles.downloadClientUpdate(path, expectedSize, progress, cancel);
    }

    /** @deprecated 僅供測試反射呼叫；請使用 {@link HeartbeatResponseParser} */
    @SuppressWarnings("unused")
    private void parseServerCommand(String body, Consumer<String> logger) {
        responses.parse(body, logger);
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


    @Override
    public HttpClient client() {
        return httpClient;
    }

    @Override
    public String url() {
        return serverUrl;
    }

    @Override
    public String workerId() {
        return clientId;
    }

    @Override
    public String token() {
        return heartbeatToken;
    }

    @Override
    public boolean active() {
        return isServiceActive;
    }

    @Override
    public String avatar() {
        return avatarEncoded;
    }

    @Override
    public Consumer<String> commandListener() {
        return commandListener;
    }

    @Override
    public Consumer<List<PeerInfo>> peersListener() {
        return peersListener;
    }

    @Override
    public Consumer<UpdateNotice> updateListener() {
        return updateListener;
    }

    @Override
    public Consumer<MarqueeNotice> marqueeListener() {
        return marqueeListener;
    }

    @Override
    public void log(Consumer<String> logger, String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }
}
