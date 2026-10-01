package com.example.service;

import com.example.PeerFileRules;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 即時直傳（雙方同時在線）：伺服器只在記憶體轉手分段，不寫磁碟。
 * <p>
 * 傳送端：登記 → 等對方接收 → 伺服器有空位就上傳下一段 → 等對方回報寫完。<br>
 * 接收端：接收 → 依位移逐段下載（長輪詢）→ 寫完通知伺服器。
 * 斷線時同一個位移可以重送／重取，伺服器在對方取走下一段前不會丟掉資料。
 */
final class RelayTransfer {

    static final Duration CONTROL_TIMEOUT = Duration.ofSeconds(30);
    static final Duration CHUNK_TIMEOUT = Duration.ofMinutes(5);
    static final Duration POLL_TIMEOUT = Duration.ofMillis(PeerFileRules.RELAY_LONG_POLL_MS).plusSeconds(30);
    static final int MAX_CONSECUTIVE_FAILURES = 6;
    static final long[] DEFAULT_RETRY_DELAYS_MS = {1_000L, 2_000L, 4_000L, 8_000L, 15_000L};

    enum SendStatus { DELIVERED, FAILED, UNSUPPORTED_SERVER, RECIPIENT_UNAVAILABLE }

    static final class SendResult {
        final SendStatus status;
        final String message;

        private SendResult(SendStatus status, String message) {
            this.status = status;
            this.message = message == null ? "" : message;
        }

        static SendResult of(SendStatus status, String message) {
            return new SendResult(status, message);
        }
    }

    static final class ReceiveResult {
        final boolean ok;
        final String message;

        private ReceiveResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message == null ? "" : message;
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
            return (status >= 500 || status == 408 || status == 429) && !"BUSY".equals(string("code"));
        }

        String string(String key) {
            JsonElement value = json.get(key);
            return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
        }

        long number(String key, long fallback) {
            JsonElement value = json.get(key);
            try {
                return value != null && value.isJsonPrimitive() ? value.getAsLong() : fallback;
            } catch (Exception ex) {
                return fallback;
            }
        }

        boolean flag(String key) {
            JsonElement value = json.get(key);
            try {
                return value != null && value.isJsonPrimitive() && value.getAsBoolean();
            } catch (Exception ex) {
                return false;
            }
        }

        String state() {
            return string("state");
        }

        boolean terminal() {
            String state = state();
            return status == 404 || status == 410
                    || "COMPLETED".equals(state) || "DECLINED".equals(state) || "CANCELLED".equals(state)
                    || "EXPIRED".equals(state) || "FAILED".equals(state);
        }

        String describe() {
            String message = string("message");
            return "狀態碼 " + status + (message.isEmpty() ? "" : "，" + message);
        }

        String endMessage() {
            String message = string("message");
            if (!message.isEmpty()) {
                return message;
            }
            return status == 404 ? "伺服器找不到這筆直傳（可能剛重啟）" : "直傳已結束（" + state() + "）";
        }
    }

    private final Supplier<HttpClient> http;
    private final String serverUrl;
    private final String token;
    private final String clientId;
    private final int chunkBytes;
    private final long[] retryDelaysMs;
    private final long pollWaitMs;
    private final Consumer<String> logger;
    private final Consumer<String> statusUpdate;
    private final Gson gson = new Gson();

    RelayTransfer(Supplier<HttpClient> http, String serverUrl, String token, String clientId,
                  Consumer<String> logger, Consumer<String> statusUpdate) {
        this(http, serverUrl, token, clientId, PeerFileRules.RELAY_CHUNK_BYTES, DEFAULT_RETRY_DELAYS_MS,
                PeerFileRules.RELAY_LONG_POLL_MS, logger, statusUpdate);
    }

    RelayTransfer(Supplier<HttpClient> http, String serverUrl, String token, String clientId,
                  int chunkBytes, long[] retryDelaysMs, long pollWaitMs,
                  Consumer<String> logger, Consumer<String> statusUpdate) {
        this.http = http;
        this.serverUrl = serverUrl;
        this.token = token;
        this.clientId = clientId;
        this.chunkBytes = Math.max(1, Math.min(chunkBytes, PeerFileRules.RELAY_CHUNK_BYTES));
        this.retryDelaysMs = retryDelaysMs != null && retryDelaysMs.length > 0
                ? retryDelaysMs : DEFAULT_RETRY_DELAYS_MS;
        this.pollWaitMs = Math.max(0L, Math.min(pollWaitMs, PeerFileRules.RELAY_LONG_POLL_MS));
        this.logger = logger;
        this.statusUpdate = statusUpdate;
    }

    /**
     * @throws TransferIo.CancelledException 使用者取消（已盡力通知伺服器中止）
     */
    SendResult send(String toClientId, Path content, long size, String filename, String kind,
                    TransferIo.Progress progress, TransferCancel cancel) throws IOException {
        String target = PeerFileRules.normalizeClientId(toClientId);
        Map<String, Object> beginBody = new LinkedHashMap<>();
        beginBody.put("fromClientId", clientId);
        beginBody.put("toClientId", target);
        beginBody.put("filename", filename);
        beginBody.put("kind", kind);
        beginBody.put("size", size);
        String beginJson = gson.toJson(beginBody);
        Reply begun = sendWithRetry(() -> post("/api/peer/relay", "", beginJson), cancel);
        if (begun.status == 404 || begun.status == 405) {
            return SendResult.of(SendStatus.UNSUPPORTED_SERVER, "伺服器尚未支援直傳");
        }
        if (begun.status == 409 && begun.string("code").startsWith("RECIPIENT_")) {
            return SendResult.of(SendStatus.RECIPIENT_UNAVAILABLE, begun.string("message"));
        }
        String relayId = begun.string("relayId");
        if (begun.status != 200 || relayId.isEmpty()) {
            return SendResult.of(SendStatus.FAILED, "無法開始直傳（" + begun.describe() + "）");
        }

        notifyStatus("已通知【" + target + "】，等待對方接收…（約 15 秒內跳出提示，需在 "
                + PeerFileRules.RELAY_ACCEPT_TIMEOUT_LABEL + " 內按下接收）");
        Reply st = begun;
        boolean announced = false;
        int failures = 0;
        try {
            while (true) {
                cancel.throwIfCancelled();
                if (st.terminal()) {
                    if ("COMPLETED".equals(st.state())) {
                        if (progress != null) {
                            progress.onProgress(size, size);
                        }
                        return SendResult.of(SendStatus.DELIVERED, st.string("message"));
                    }
                    return SendResult.of(SendStatus.FAILED, st.endMessage());
                }
                if ("ACCEPTED".equals(st.state())) {
                    if (!announced) {
                        announced = true;
                        log("[檔案] 【" + target + "】已接收，開始直傳「" + filename + "」");
                    }
                    long pushed = st.number("pushed", 0L);
                    if (st.flag("canPush") && pushed < size) {
                        long offset = pushed;
                        long length = Math.min(chunkBytes, size - offset);
                        String chunk = ChunkedUploader.chunkLabel(offset, size, chunkBytes);
                        notifyStatus("直傳「" + filename + "」" + chunk);
                        TransferIo.Progress chunkProgress = progress == null
                                ? null : (transferred, total) -> progress.onProgress(offset + transferred, size);
                        String error;
                        try {
                            Reply reply = send(pushRequest(relayId, offset, content, length, chunkProgress, cancel),
                                    cancel);
                            if (reply.status == 200) {
                                st = reply;
                                failures = 0;
                                continue;
                            }
                            if (reply.terminal()) {
                                st = reply;
                                continue;
                            }
                            if (reply.status == 409) {
                                if (++failures >= MAX_CONSECUTIVE_FAILURES) {
                                    cancelQuietly(relayId);
                                    return SendResult.of(SendStatus.FAILED, "伺服器進度一直對不上（" + reply.describe() + "）");
                                }
                                st = reply.flag("canPush")
                                        ? reply
                                        : awaitChange(relayId, reply.number("version", 0L), cancel);
                                continue;
                            }
                            if (!reply.isTransient()) {
                                cancelQuietly(relayId);
                                return SendResult.of(SendStatus.FAILED, "直傳被拒（" + reply.describe() + "）");
                            }
                            error = reply.describe();
                        } catch (TransferIo.CancelledException ex) {
                            throw ex;
                        } catch (IOException ex) {
                            error = HeartbeatService.describeTransferFailure(ex);
                        }
                        if (++failures >= MAX_CONSECUTIVE_FAILURES) {
                            cancelQuietly(relayId);
                            return SendResult.of(SendStatus.FAILED, "連續 " + failures + " 次中斷，已放棄（" + error + "）");
                        }
                        long delayMs = retryDelay(failures);
                        log("[重試] [檔案] 直傳" + chunk + "中斷（" + error + "），"
                                + (delayMs / 1000L) + " 秒後重送（第 " + failures + " 次重試）");
                        notifyStatus(chunk + "中斷，" + (delayMs / 1000L) + " 秒後重送…");
                        sleepCancellable(delayMs, cancel);
                        st = awaitChange(relayId, Long.MAX_VALUE, cancel);
                        continue;
                    }
                    if (pushed >= size) {
                        notifyStatus("已全部送出，等待對方寫入完成…");
                    }
                }
                st = awaitChange(relayId, st.number("version", 0L), cancel);
            }
        } catch (IOException ex) {
            cancelQuietly(relayId);
            throw ex;
        }
    }

    /**
     * @throws TransferIo.CancelledException 使用者取消（已刪除 .part 並通知伺服器中止）
     */
    ReceiveResult receive(String relayId, Path dest, TransferIo.Progress progress, TransferCancel cancel)
            throws IOException {
        Reply accepted = sendWithRetry(() -> post("/api/peer/relay/" + urlEncode(relayId) + "/accept", "", null),
                cancel);
        if (accepted.status != 200) {
            return new ReceiveResult(false, accepted.terminal()
                    ? accepted.endMessage()
                    : "無法接收（" + accepted.describe() + "）");
        }
        long size = accepted.number("size", -1L);
        if (size <= 0) {
            cancelQuietly(relayId);
            return new ReceiveResult(false, "伺服器回報的檔案大小無效");
        }
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path part = dest.resolveSibling(dest.getFileName().toString() + ".part");
        boolean done = false;
        try {
            try (OutputStream out = Files.newOutputStream(part)) {
                ReceiveResult failure = receiveChunks(relayId, size, out, progress, cancel);
                if (failure != null) {
                    cancelQuietly(relayId);
                    return failure;
                }
            }
            Reply completed = sendWithRetry(
                    () -> post("/api/peer/relay/" + urlEncode(relayId) + "/complete", "", null), cancel);
            if (completed.status != 200) {
                cancelQuietly(relayId);
                return new ReceiveResult(false, "無法完成直傳（" + completed.describe() + "）");
            }
            Files.move(part, dest, StandardCopyOption.REPLACE_EXISTING);
            done = true;
            return new ReceiveResult(true, "");
        } catch (IOException ex) {
            if (!done) {
                cancelQuietly(relayId);
            }
            throw ex;
        } finally {
            if (!done) {
                deleteQuietly(part);
            }
        }
    }

    /** @return null 表示全部收齊；否則為失敗結果 */
    private ReceiveResult receiveChunks(String relayId, long size, OutputStream out,
                                        TransferIo.Progress progress, TransferCancel cancel) throws IOException {
        long offset = 0L;
        int failures = 0;
        if (progress != null) {
            progress.onProgress(0L, size);
        }
        while (offset < size) {
            cancel.throwIfCancelled();
            String chunk = ChunkedUploader.chunkLabel(offset, size, chunkBytes);
            String error;
            try {
                final long base = offset;
                TransferIo.Progress chunkProgress = progress == null
                        ? null : (transferred, total) -> progress.onProgress(base + transferred, size);
                HttpResponse<InputStream> response = sendStream(takeRequest(relayId, offset), cancel);
                int code = response.statusCode();
                if (code == 200) {
                    long expected = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    String echoed = response.headers().firstValue("X-Relay-Offset").orElse("");
                    if (!echoed.isEmpty() && !echoed.trim().equals(String.valueOf(offset))) {
                        closeQuietly(response.body());
                        return new ReceiveResult(false, "伺服器回傳的位移不符（預期 " + offset + "，收到 " + echoed + "）");
                    }
                    byte[] data;
                    try (InputStream in = response.body()) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream(
                                (int) Math.max(0L, Math.min(expected, chunkBytes * 2L)));
                        TransferIo.copy(in, buffer, expected, chunkProgress, cancel);
                        data = buffer.toByteArray();
                    }
                    if (data.length == 0 || (expected >= 0 && data.length != expected)) {
                        throw new IOException("分段不完整（" + data.length + " / " + expected + " bytes）");
                    }
                    if (offset + data.length > size) {
                        return new ReceiveResult(false, "收到的資料超過宣告大小");
                    }
                    out.write(data);
                    offset += data.length;
                    failures = 0;
                    notifyStatus("正在接收直傳 " + chunk);
                    continue;
                }
                if (code == 204) {
                    failures = 0;
                    notifyStatus("等待對方送出" + chunk + "…");
                    continue;
                }
                Reply reply;
                try (InputStream in = response.body()) {
                    reply = new Reply(code, parseJson(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
                }
                if (reply.terminal()) {
                    return new ReceiveResult(false, reply.endMessage());
                }
                if (!reply.isTransient()) {
                    return new ReceiveResult(false, "直傳中斷（" + reply.describe() + "）");
                }
                error = reply.describe();
            } catch (TransferIo.CancelledException ex) {
                throw ex;
            } catch (IOException ex) {
                error = HeartbeatService.describeTransferFailure(ex);
            }
            if (++failures >= MAX_CONSECUTIVE_FAILURES) {
                return new ReceiveResult(false, "連續 " + failures + " 次中斷，已放棄（" + error + "）");
            }
            long delayMs = retryDelay(failures);
            log("[重試] [檔案] 接收直傳" + chunk + "中斷（" + error + "），"
                    + (delayMs / 1000L) + " 秒後重取（第 " + failures + " 次重試）");
            notifyStatus(chunk + "中斷，" + (delayMs / 1000L) + " 秒後重取…");
            sleepCancellable(delayMs, cancel);
        }
        return null;
    }

    /** 收件人拒收（或任一方中止）；不等結果。 */
    void cancelQuietly(String relayId) {
        if (relayId == null || relayId.isEmpty()) {
            return;
        }
        try {
            HttpRequest request = withAuth(HttpRequest.newBuilder()
                    .uri(URI.create(relayUrl("/api/peer/relay/" + urlEncode(relayId), "")))
                    .timeout(CONTROL_TIMEOUT)
                    .DELETE())
                    .build();
            http.get().sendAsync(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // 伺服器閒置逾時也會中止
        }
    }

    /** 長輪詢：版本有變化或逾時就回來；暫時性錯誤會重試。 */
    private Reply awaitChange(String relayId, long sinceVersion, TransferCancel cancel) throws IOException {
        long since = sinceVersion == Long.MAX_VALUE ? 0L : sinceVersion;
        long wait = sinceVersion == Long.MAX_VALUE ? 0L : pollWaitMs;
        int failures = 0;
        while (true) {
            String error;
            try {
                Reply reply = send(withAuth(HttpRequest.newBuilder()
                        .uri(URI.create(relayUrl("/api/peer/relay/" + urlEncode(relayId),
                                "&since=" + since + "&wait=" + wait)))
                        .timeout(POLL_TIMEOUT)
                        .GET())
                        .build(), cancel);
                if (reply.status == 200 || reply.terminal()) {
                    return reply;
                }
                if (!reply.isTransient()) {
                    return new Reply(410, reply.json.has("message") ? reply.json : messageJson(reply.describe()));
                }
                error = reply.describe();
            } catch (TransferIo.CancelledException ex) {
                throw ex;
            } catch (IOException ex) {
                error = HeartbeatService.describeTransferFailure(ex);
            }
            if (++failures >= MAX_CONSECUTIVE_FAILURES) {
                return new Reply(410, messageJson("連續 " + failures + " 次無法查詢直傳狀態（" + error + "）"));
            }
            long delayMs = retryDelay(failures);
            log("[重試] [檔案] 查詢直傳狀態失敗（" + error + "），" + (delayMs / 1000L) + " 秒後重試");
            sleepCancellable(delayMs, cancel);
        }
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
            if (++failures >= MAX_CONSECUTIVE_FAILURES) {
                throw new IOException("連續 " + failures + " 次連線失敗：" + error);
            }
            long delayMs = retryDelay(failures);
            log("[重試] [檔案] 連線失敗（" + error + "），" + (delayMs / 1000L) + " 秒後重試（第 " + failures + " 次）");
            sleepCancellable(delayMs, cancel);
        }
    }

    private Reply send(HttpRequest request, TransferCancel cancel) throws IOException {
        HttpResponse<String> response = await(http.get().sendAsync(request, HttpResponse.BodyHandlers.ofString()),
                cancel);
        return new Reply(response.statusCode(), parseJson(response.body()));
    }

    private HttpResponse<InputStream> sendStream(HttpRequest request, TransferCancel cancel) throws IOException {
        return await(http.get().sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()), cancel);
    }

    private static <T> HttpResponse<T> await(CompletableFuture<HttpResponse<T>> future, TransferCancel cancel)
            throws IOException {
        cancel.throwIfCancelled();
        cancel.cancelOnCancel(future);
        try {
            return future.get();
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

    private HttpRequest post(String path, String query, String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(relayUrl(path, query)))
                .timeout(CONTROL_TIMEOUT);
        if (json != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }
        return withAuth(builder).build();
    }

    private HttpRequest pushRequest(String relayId, long offset, Path content, long length,
                                    TransferIo.Progress progress, TransferCancel cancel) {
        return withAuth(HttpRequest.newBuilder()
                .uri(URI.create(relayUrl("/api/peer/relay/" + urlEncode(relayId) + "/chunk", "&offset=" + offset)))
                .header("Content-Type", "application/octet-stream")
                .timeout(CHUNK_TIMEOUT)
                .PUT(TransferIo.ofFileRange(content, offset, length, progress, cancel)))
                .build();
    }

    private HttpRequest takeRequest(String relayId, long offset) {
        return withAuth(HttpRequest.newBuilder()
                .uri(URI.create(relayUrl("/api/peer/relay/" + urlEncode(relayId) + "/chunk",
                        "&offset=" + offset + "&wait=" + pollWaitMs)))
                .timeout(CHUNK_TIMEOUT)
                .GET())
                .build();
    }

    private String relayUrl(String path, String extraQuery) {
        return serverUrl + path + "?clientId=" + urlEncode(clientId) + (extraQuery == null ? "" : extraQuery);
    }

    private HttpRequest.Builder withAuth(HttpRequest.Builder builder) {
        builder.header("Authorization", "Bearer " + token);
        String encodedClient = urlEncode(clientId);
        if (encodedClient.chars().allMatch(c -> c >= 0x20 && c <= 0x7e)) {
            builder.header("X-PunchClock-Client", encodedClient);
        }
        return builder;
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

    private static JsonObject messageJson(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("message", message);
        return json;
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

    private static void closeQuietly(InputStream in) {
        try {
            if (in != null) {
                in.close();
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            if (path != null) {
                Files.deleteIfExists(path);
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
