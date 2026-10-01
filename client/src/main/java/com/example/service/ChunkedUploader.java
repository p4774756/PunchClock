package com.example.service;

import com.example.PeerFileRules;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 分段上傳（可續傳）：每段一個短請求；斷線、逾時或 5xx 時先問伺服器收到多少，再從那裡接著傳。
 * 伺服器沒有分段 API（舊版）時回 {@link Result#unsupported}，由呼叫端退回整檔 multipart。
 */
final class ChunkedUploader {

    static final Duration CONTROL_TIMEOUT = Duration.ofSeconds(30);
    static final Duration CHUNK_TIMEOUT = Duration.ofMinutes(5);
    static final int MAX_CONSECUTIVE_FAILURES = 6;
    /** 伺服器重啟會遺失進度（Render 免費方案磁碟也會清空），只能從頭來；限制次數避免無限重傳。 */
    static final int MAX_SESSION_RESTARTS = 2;
    static final long[] DEFAULT_RETRY_DELAYS_MS = {2_000L, 4_000L, 8_000L, 15_000L, 30_000L};

    static final class Result {
        final boolean ok;
        final boolean unsupported;
        final String message;

        private Result(boolean ok, boolean unsupported, String message) {
            this.ok = ok;
            this.unsupported = unsupported;
            this.message = message == null ? "" : message;
        }

        static Result ok(String message) {
            return new Result(true, false, message);
        }

        static Result failed(String message) {
            return new Result(false, false, message);
        }

        static Result unsupported() {
            return new Result(false, true, "");
        }
    }

    private static final class Reply {
        final int status;
        final JsonObject json;

        Reply(int status, JsonObject json) {
            this.status = status;
            this.json = json != null ? json : new JsonObject();
        }

        boolean isTransient() {
            return status >= 500 || status == 408 || status == 429;
        }

        long received(long fallback) {
            JsonElement value = json.get("received");
            try {
                return value != null && value.isJsonPrimitive() ? value.getAsLong() : fallback;
            } catch (Exception ex) {
                return fallback;
            }
        }

        String string(String key) {
            JsonElement value = json.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
        }

        String describe() {
            String message = string("message");
            return "狀態碼 " + status + (message.isEmpty() ? "" : "，" + message);
        }
    }

    private static final class Session {
        final String beginJson;
        String uploadId = "";
        long offset;
        int restarts;
        int failures;

        Session(String beginJson) {
            this.beginJson = beginJson;
        }
    }

    private final Supplier<HttpClient> http;
    private final String serverUrl;
    private final String token;
    private final String clientId;
    private final int chunkBytes;
    private final long[] retryDelaysMs;
    private final Consumer<String> logger;
    private final Consumer<String> statusUpdate;
    private final Gson gson = new Gson();

    ChunkedUploader(Supplier<HttpClient> http, String serverUrl, String token, String clientId,
                    Consumer<String> logger, Consumer<String> statusUpdate) {
        this(http, serverUrl, token, clientId, PeerFileRules.UPLOAD_CHUNK_BYTES, DEFAULT_RETRY_DELAYS_MS,
                logger, statusUpdate);
    }

    ChunkedUploader(Supplier<HttpClient> http, String serverUrl, String token, String clientId,
                    int chunkBytes, long[] retryDelaysMs, Consumer<String> logger, Consumer<String> statusUpdate) {
        this.http = http;
        this.serverUrl = serverUrl;
        this.token = token;
        this.clientId = clientId;
        this.chunkBytes = Math.max(1, Math.min(chunkBytes, PeerFileRules.MAX_UPLOAD_CHUNK_BYTES));
        this.retryDelaysMs = retryDelaysMs != null && retryDelaysMs.length > 0
                ? retryDelaysMs : DEFAULT_RETRY_DELAYS_MS;
        this.logger = logger;
        this.statusUpdate = statusUpdate;
    }

    /**
     * @throws TransferIo.CancelledException 使用者取消（已盡力通知伺服器丟棄暫存）
     */
    Result upload(String toClientId, Path content, long size, String filename, String kind,
                  TransferIo.Progress progress, TransferCancel cancel) throws IOException {
        Map<String, Object> beginBody = new LinkedHashMap<>();
        beginBody.put("fromClientId", clientId);
        beginBody.put("toClientId", PeerFileRules.normalizeClientId(toClientId));
        beginBody.put("filename", filename);
        beginBody.put("kind", kind);
        beginBody.put("size", size);
        Session session = new Session(gson.toJson(beginBody));
        Reply begun = sendWithRetry(() -> beginRequest(session.beginJson), cancel);
        if (begun.status == 404 || begun.status == 405) {
            return Result.unsupported();
        }
        if (begun.status != 200) {
            return Result.failed("無法開始上傳（" + begun.describe() + "）");
        }
        session.uploadId = begun.string("uploadId");
        session.offset = begun.received(0L);
        if (session.uploadId.isEmpty()) {
            return Result.unsupported();
        }

        try {
            while (true) {
                Result failure = sendChunks(session, content, size, progress, cancel);
                if (failure != null) {
                    return failure;
                }
                Reply done = sendWithRetry(() -> controlRequest(session.uploadId, "/complete", "POST"), cancel);
                if (done.status == 200) {
                    if (progress != null) {
                        progress.onProgress(size, size);
                    }
                    return Result.ok(done.string("message"));
                }
                if (done.status == 409) {
                    session.offset = done.received(session.offset);
                    if (++session.failures >= MAX_CONSECUTIVE_FAILURES) {
                        return Result.failed("伺服器回報檔案不完整（" + done.describe() + "）");
                    }
                    continue;
                }
                if (done.status == 404) {
                    Result restart = restartSession(session, cancel);
                    if (restart != null) {
                        return restart;
                    }
                    continue;
                }
                return Result.failed("完成上傳失敗（" + done.describe() + "）");
            }
        } catch (TransferIo.CancelledException ex) {
            abortQuietly(session.uploadId);
            throw ex;
        }
    }

    /** @return null 表示這輪分段都送完；否則為失敗結果 */
    private Result sendChunks(Session session, Path content, long size,
                              TransferIo.Progress progress, TransferCancel cancel) throws IOException {
        while (session.offset < size) {
            cancel.throwIfCancelled();
            long base = session.offset;
            long length = Math.min(chunkBytes, size - base);
            TransferIo.Progress chunkProgress = progress == null
                    ? null : (transferred, total) -> progress.onProgress(base + transferred, size);
            String error;
            try {
                Reply reply = send(chunkRequest(session.uploadId, base, content, length, chunkProgress, cancel),
                        cancel);
                if (reply.status == 200) {
                    session.offset = reply.received(base + length);
                    session.failures = 0;
                    continue;
                }
                if (reply.status == 409) {
                    session.offset = reply.received(base);
                    if (++session.failures >= MAX_CONSECUTIVE_FAILURES) {
                        return Result.failed("伺服器進度一直對不上（" + reply.describe() + "）");
                    }
                    continue;
                }
                if (reply.status == 404) {
                    Result restart = restartSession(session, cancel);
                    if (restart != null) {
                        return restart;
                    }
                    continue;
                }
                if (!reply.isTransient()) {
                    return Result.failed("分段上傳被拒（" + reply.describe() + "）");
                }
                error = reply.describe();
            } catch (TransferIo.CancelledException ex) {
                throw ex;
            } catch (IOException ex) {
                error = HeartbeatService.describeTransferFailure(ex);
            }

            session.failures++;
            if (session.failures >= MAX_CONSECUTIVE_FAILURES) {
                return Result.failed("連續 " + session.failures + " 次中斷，已放棄（" + error + "）");
            }
            long delayMs = retryDelay(session.failures);
            log("[重試] [檔案] 上傳在 " + PeerFileRules.formatSize(base) + " / "
                    + PeerFileRules.formatSize(size) + " 中斷（" + error + "），"
                    + (delayMs / 1000L) + " 秒後續傳（第 " + session.failures + " 次）");
            notifyStatus("連線中斷，" + (delayMs / 1000L) + " 秒後續傳（已傳 "
                    + PeerFileRules.formatSize(base) + " / " + PeerFileRules.formatSize(size) + "）…");
            sleepCancellable(delayMs, cancel);
            try {
                Reply status = send(controlRequest(session.uploadId, "", "GET"), cancel);
                if (status.status == 200) {
                    session.offset = status.received(session.offset);
                } else if (status.status == 404) {
                    Result restart = restartSession(session, cancel);
                    if (restart != null) {
                        return restart;
                    }
                }
            } catch (TransferIo.CancelledException ex) {
                throw ex;
            } catch (IOException ignored) {
                // 查不到進度就照原位移重送；位移不對時伺服器會回 409 告知正確位置
            }
            notifyStatus("正在續傳…");
        }
        return null;
    }

    /** 伺服器遺失上傳工作（多半是重啟）：重新登記並從頭傳。 */
    private Result restartSession(Session session, TransferCancel cancel) throws IOException {
        if (session.restarts >= MAX_SESSION_RESTARTS) {
            return Result.failed("伺服器遺失上傳進度（可能剛重啟），請稍後重新傳送");
        }
        session.restarts++;
        log("[重試] [檔案] 伺服器遺失上傳進度（可能剛重啟），從頭重新上傳（第 " + session.restarts + " 次）");
        notifyStatus("伺服器遺失進度，從頭重新上傳…");
        Reply begun = sendWithRetry(() -> beginRequest(session.beginJson), cancel);
        if (begun.status != 200 || begun.string("uploadId").isEmpty()) {
            return Result.failed("無法重新開始上傳（" + begun.describe() + "）");
        }
        session.uploadId = begun.string("uploadId");
        session.offset = begun.received(0L);
        session.failures = 0;
        return null;
    }

    private Reply sendWithRetry(Supplier<HttpRequest> request, TransferCancel cancel) throws IOException {
        int failures = 0;
        while (true) {
            String error;
            try {
                Reply reply = send(request.get(), cancel);
                if (!reply.isTransient()) {
                    return reply;
                }
                error = reply.describe();
            } catch (TransferIo.CancelledException ex) {
                throw ex;
            } catch (IOException ex) {
                error = HeartbeatService.describeTransferFailure(ex);
            }
            failures++;
            if (failures >= MAX_CONSECUTIVE_FAILURES) {
                throw new IOException("連續 " + failures + " 次連線失敗：" + error);
            }
            long delayMs = retryDelay(failures);
            log("[重試] [檔案] 連線失敗（" + error + "），" + (delayMs / 1000L) + " 秒後重試（第 " + failures + " 次）");
            sleepCancellable(delayMs, cancel);
        }
    }

    private Reply send(HttpRequest request, TransferCancel cancel) throws IOException {
        cancel.throwIfCancelled();
        CompletableFuture<HttpResponse<String>> future =
                http.get().sendAsync(request, HttpResponse.BodyHandlers.ofString());
        cancel.cancelOnCancel(future);
        try {
            HttpResponse<String> response = future.get();
            return new Reply(response.statusCode(), parseJson(response.body()));
        } catch (CancellationException ex) {
            throw new TransferIo.CancelledException();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TransferIo.CancelledException();
        } catch (ExecutionException ex) {
            if (cancel.isCancelled()) {
                throw new TransferIo.CancelledException();
            }
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException(cause.getMessage(), cause);
        }
    }

    private HttpRequest beginRequest(String json) {
        return withAuth(HttpRequest.newBuilder()
                .uri(URI.create(serverUrl + "/api/peer/upload"))
                .header("Content-Type", "application/json")
                .timeout(CONTROL_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(json)))
                .build();
    }

    private HttpRequest chunkRequest(String uploadId, long offset, Path content, long length,
                                     TransferIo.Progress progress, TransferCancel cancel) {
        return withAuth(HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl(uploadId, "") + "&offset=" + offset))
                .header("Content-Type", "application/octet-stream")
                .timeout(CHUNK_TIMEOUT)
                .PUT(TransferIo.ofFileRange(content, offset, length, progress, cancel)))
                .build();
    }

    private HttpRequest controlRequest(String uploadId, String suffix, String method) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl(uploadId, suffix)))
                .timeout(CONTROL_TIMEOUT);
        if ("POST".equals(method)) {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            builder.GET();
        }
        return withAuth(builder).build();
    }

    private String uploadUrl(String uploadId, String suffix) {
        return serverUrl + "/api/peer/upload/" + urlEncode(uploadId) + suffix + "?clientId=" + urlEncode(clientId);
    }

    private HttpRequest.Builder withAuth(HttpRequest.Builder builder) {
        builder.header("Authorization", "Bearer " + token);
        String encodedClient = urlEncode(clientId);
        if (encodedClient.chars().allMatch(c -> c >= 0x20 && c <= 0x7e)) {
            builder.header("X-PunchClock-Client", encodedClient);
        }
        return builder;
    }

    private void abortQuietly(String uploadId) {
        if (uploadId == null || uploadId.isEmpty()) {
            return;
        }
        try {
            http.get().sendAsync(controlRequest(uploadId, "", "DELETE"), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // 伺服器閒置逾時也會清掉
        }
    }

    private long retryDelay(int failures) {
        return retryDelaysMs[Math.min(Math.max(failures, 1), retryDelaysMs.length) - 1];
    }

    private static void sleepCancellable(long delayMs, TransferCancel cancel) throws TransferIo.CancelledException {
        long deadline = System.currentTimeMillis() + delayMs;
        try {
            while (true) {
                cancel.throwIfCancelled();
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return;
                }
                Thread.sleep(Math.min(200L, remaining));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TransferIo.CancelledException();
        }
    }

    private void log(String text) {
        if (logger != null) {
            logger.accept(text);
        }
    }

    private void notifyStatus(String text) {
        if (statusUpdate != null) {
            statusUpdate.accept(text);
        }
    }

    private static JsonObject parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
