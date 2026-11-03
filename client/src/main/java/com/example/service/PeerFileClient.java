package com.example.service;

import com.example.ClientUpdatePolicy;
import com.example.PeerFileRules;
import com.example.PeerFolderPacker;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** 同事直傳，以及下載桌面端更新檔。走與心跳相同的 HttpClient。 */
final class PeerFileClient {

    private final HeartbeatEndpoint endpoint;

    PeerFileClient(HeartbeatEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    /** 要送出的內容：一般檔案直接用原檔；資料夾先壓成暫存 ZIP（用完要 {@link #cleanup()}）。 */
    private static final class Outgoing {
        final String filename;
        final Path path;
        final long size;
        final String kind;
        final PeerFolderPacker.PackResult packed;

        Outgoing(String filename, Path path, long size, String kind, PeerFolderPacker.PackResult packed) {
            this.filename = filename;
            this.path = path;
            this.size = size;
            this.kind = kind;
            this.packed = packed;
        }

        void cleanup() {
            if (packed != null) {
                packed.deleteQuietly();
            }
        }
    }

    /** @return null 表示不能傳送（原因已寫入日誌） */
    private Outgoing prepareOutgoing(List<Path> files, Consumer<String> logger, TransferIo.Progress progress,
                                     Consumer<String> statusUpdate, TransferCancel cancel)
            throws java.io.IOException {
        if (files.size() > 1) {
            cancel.throwIfCancelled();
            notifyStatus(statusUpdate, "正在壓縮 " + files.size() + " 個項目…");
            if (progress != null) {
                progress.onProgress(0L, 0L);
            }
            PeerFolderPacker.PackResult packed = PeerFolderPacker.packMany(files, cancel::isCancelled);
            if (packed.cancelled) {
                throw new TransferIo.CancelledException();
            }
            if (!packed.ok) {
                endpoint.log(logger, "[警告] [檔案] " + packed.message);
                return null;
            }
            endpoint.log(logger, "[檔案] 正在傳送 " + files.size() + " 個項目（打包成「" + packed.filename + "」，壓縮 "
                    + PeerFileRules.formatSize(packed.size) + "）");
            return new Outgoing(packed.filename, packed.path, packed.size, PeerFileRules.KIND_FOLDER, packed);
        }
        return prepareSingleOutgoing(files.get(0), logger, progress, statusUpdate, cancel);
    }

    private Outgoing prepareSingleOutgoing(Path file, Consumer<String> logger, TransferIo.Progress progress,
                                           Consumer<String> statusUpdate, TransferCancel cancel)
            throws java.io.IOException {
        cancel.throwIfCancelled();
        String displayName = file.getFileName() != null ? file.getFileName().toString() : "檔案";
        if (Files.isDirectory(file)) {
            notifyStatus(statusUpdate, "正在壓縮資料夾「" + displayName + "」…");
            if (progress != null) {
                progress.onProgress(0L, 0L);
            }
            PeerFolderPacker.PackResult packed = PeerFolderPacker.pack(file, cancel::isCancelled);
            if (packed.cancelled) {
                throw new TransferIo.CancelledException();
            }
            if (!packed.ok) {
                endpoint.log(logger, "[警告] [檔案] " + packed.message);
                return null;
            }
            endpoint.log(logger, "[檔案] 正在傳送資料夾「" + file.getFileName() + "」（壓縮 "
                    + PeerFileRules.formatSize(packed.size) + "）");
            return new Outgoing(packed.filename, packed.path, packed.size, PeerFileRules.KIND_FOLDER, packed);
        }
        if (Files.isRegularFile(file)) {
            String filename = PeerFileRules.sanitizeFilename(displayName);
            if (filename.isEmpty()) {
                endpoint.log(logger, "[警告] [檔案] 檔名無效");
                return null;
            }
            long size = Files.size(file);
            if (!PeerFileRules.isAllowedSize(size)) {
                endpoint.log(logger, size <= 0
                        ? "[警告] [檔案] 檔案不可為空"
                        : "[警告] [檔案] 檔案不可超過 " + PeerFileRules.MAX_SIZE_LABEL);
                return null;
            }
            return new Outgoing(filename, file, size, PeerFileRules.KIND_FILE, null);
        }
        endpoint.log(logger, "[警告] [檔案] 找不到要傳送的檔案");
        return null;
    }

    /**
     * 即時直傳給在線同事：伺服器只在記憶體轉手，不留檔。對方需在
     * {@link PeerFileRules#RELAY_ACCEPT_TIMEOUT_LABEL} 內按下接收，且雙方都要保持在線直到傳完。
     *
     * @param callback 直傳結果。對方不在線或伺服器不支援時，呼叫端應告知使用者，不會改走伺服器暫存
     */
    void sendPeerFileRelay(String toClientId, Path file, Consumer<String> logger,
                                  TransferIo.Progress progress, Consumer<String> statusUpdate,
                                  TransferCancel cancel, Consumer<HeartbeatService.RelayOutcome> callback) {
        sendPeerFileRelay(toClientId, file == null ? null : Collections.singletonList(file), logger, progress,
                statusUpdate, cancel, callback);
    }

    /** 多個檔案／資料夾會先打包成同一個 ZIP 再直傳。 */
    void sendPeerFileRelay(String toClientId, List<Path> files, Consumer<String> logger,
                                  TransferIo.Progress progress, Consumer<String> statusUpdate,
                                  TransferCancel cancel, Consumer<HeartbeatService.RelayOutcome> callback) {
        Consumer<HeartbeatService.RelayOutcome> done = outcome -> {
            if (callback != null) callback.accept(outcome);
        };
        if (!endpoint.active() || endpoint.url().isBlank()) {
            endpoint.log(logger, "[警告] [檔案] 雲端未連線，無法直傳");
            done.accept(HeartbeatService.RelayOutcome.FAILED);
            return;
        }
        if (toClientId == null || toClientId.isBlank() || files == null || files.isEmpty()
                || files.stream().anyMatch(java.util.Objects::isNull)) {
            endpoint.log(logger, "[警告] [檔案] 缺少收件同事或檔案");
            done.accept(HeartbeatService.RelayOutcome.FAILED);
            return;
        }
        final String targetId = toClientId.trim();
        final TransferCancel token = cancel != null ? cancel : new TransferCancel();
        final List<Path> sources = new ArrayList<>(files);
        Thread worker = new Thread(() -> {
            String displayName = HeartbeatService.displayNameOf(sources);
            Outgoing outgoing = null;
            try {
                outgoing = prepareOutgoing(sources, logger, progress, statusUpdate, token);
                if (outgoing == null) {
                    done.accept(HeartbeatService.RelayOutcome.FAILED);
                    return;
                }
                RelayTransfer.SendResult result = new RelayTransfer(
                        () -> endpoint.client(), endpoint.url(), endpoint.token(), endpoint.workerId(), logger, statusUpdate)
                        .send(targetId, outgoing.path, outgoing.size, outgoing.filename, outgoing.kind,
                                TransferIo.throttle(progress), token);
                switch (result.status) {
                    case DELIVERED:
                        notifyStatus(statusUpdate, "對方已收到");
                        endpoint.log(logger, "[成功] [檔案] 已直傳「" + outgoing.filename + "」給【" + targetId
                                + "】（" + PeerFileRules.formatSize(outgoing.size) + "，伺服器未留檔）");
                        done.accept(HeartbeatService.RelayOutcome.DELIVERED);
                        break;
                    case UNSUPPORTED_SERVER:
                        endpoint.log(logger, "[提示] [檔案] 伺服器尚未支援直傳（請更新雲端）");
                        done.accept(HeartbeatService.RelayOutcome.UNSUPPORTED_SERVER);
                        break;
                    case RECIPIENT_UNAVAILABLE:
                        endpoint.log(logger, "[提示] [檔案] 無法直傳給【" + targetId + "】：" + result.message);
                        done.accept(HeartbeatService.RelayOutcome.RECIPIENT_UNAVAILABLE);
                        break;
                    default:
                        endpoint.log(logger, "[失敗] [檔案] 直傳「" + outgoing.filename + "」失敗：" + result.message);
                        done.accept(HeartbeatService.RelayOutcome.FAILED);
                        break;
                }
            } catch (TransferIo.CancelledException ex) {
                endpoint.log(logger, "[取消] [檔案] 已取消直傳「" + displayName + "」");
                done.accept(HeartbeatService.RelayOutcome.CANCELLED);
            } catch (OutOfMemoryError ex) {
                endpoint.log(logger, "[失敗] [檔案] 本機記憶體不足，無法處理這麼大的檔案");
                done.accept(HeartbeatService.RelayOutcome.FAILED);
            } catch (Exception ex) {
                endpoint.log(logger, "[失敗] [檔案] 直傳異常：" + HeartbeatService.describeTransferFailure(ex));
                done.accept(HeartbeatService.RelayOutcome.FAILED);
            } finally {
                if (outgoing != null) {
                    outgoing.cleanup();
                }
            }
        }, "peer-relay-send");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 接收同事的即時直傳，寫到 destination（先寫 .part，收齊才改名）。
     */
    void receivePeerRelay(String relayId, Path destination, Consumer<String> logger,
                                 TransferIo.Progress progress, Consumer<String> statusUpdate,
                                 TransferCancel cancel, Consumer<Boolean> callback) {
        if (!endpoint.active() || endpoint.url().isBlank()) {
            endpoint.log(logger, "[警告] [檔案] 雲端未連線，無法接收直傳");
            if (callback != null) callback.accept(false);
            return;
        }
        if (relayId == null || relayId.isBlank() || destination == null) {
            endpoint.log(logger, "[警告] [檔案] 缺少直傳編號或儲存路徑");
            if (callback != null) callback.accept(false);
            return;
        }
        Path dest = PeerFileRules.resolveSavePath(destination, destination.getFileName() != null
                ? destination.getFileName().toString() : "download");
        final TransferCancel token = cancel != null ? cancel : new TransferCancel();
        final String savedName = dest.getFileName() != null ? dest.getFileName().toString() : "檔案";
        Thread worker = new Thread(() -> {
            boolean ok = false;
            try {
                RelayTransfer.ReceiveResult result = new RelayTransfer(
                        () -> endpoint.client(), endpoint.url(), endpoint.token(), endpoint.workerId(), logger, statusUpdate)
                        .receive(relayId.trim(), dest, TransferIo.throttle(progress), token);
                if (result.ok) {
                    endpoint.log(logger, "[成功] [檔案] 已直傳儲存：" + dest.toAbsolutePath()
                            + "（" + PeerFileRules.formatSize(Files.size(dest)) + "）");
                    ok = true;
                } else {
                    endpoint.log(logger, "[失敗] [檔案] 接收直傳「" + savedName + "」失敗：" + result.message);
                }
            } catch (TransferIo.CancelledException ex) {
                endpoint.log(logger, "[取消] [檔案] 已取消接收「" + savedName + "」");
            } catch (Exception ex) {
                endpoint.log(logger, "[失敗] [檔案] 接收直傳異常：" + HeartbeatService.describeTransferFailure(ex));
            }
            if (callback != null) callback.accept(ok);
        }, "peer-relay-receive");
        worker.setDaemon(true);
        worker.start();
    }

    /** 拒收同事的直傳（傳送端會立刻收到「對方拒絕接收」）。 */
    void declinePeerRelay(String relayId, Consumer<String> logger) {
        if (!endpoint.active() || endpoint.url().isBlank() || relayId == null || relayId.isBlank()) {
            return;
        }
        new RelayTransfer(() -> endpoint.client(), endpoint.url(), endpoint.token(), endpoint.workerId(), logger, null)
                .cancelQuietly(relayId.trim());
    }

    private static void notifyStatus(Consumer<String> statusUpdate, String text) {
        if (statusUpdate != null && text != null) {
            statusUpdate.accept(text);
        }
    }

    private HttpRequest buildDownloadRequest(URI uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .header("Authorization", "Bearer " + endpoint.token())
                .timeout(HeartbeatService.FILE_TRANSFER_TIMEOUT)
                .GET();
        // JDK 17+（Mac 常見）不接受非 ASCII header；Worker ID 只放 URL-safe 編碼。
        String encodedClient = urlEncode(endpoint.workerId());
        if (isAsciiHeaderValue(encodedClient)) {
            builder.header("X-PunchClock-Client", encodedClient);
        }
        return builder.build();
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

    /**
     * 下載伺服器附帶的桌面端 jar 到 {@code ~/.punchclock/updates/}。
     * 使用與心跳相同的 HttpClient（含信任所有 SSL）。
     */
    Path downloadClientUpdate(String path) throws IOException, InterruptedException {
        return downloadClientUpdate(path, 0L, null, null);
    }

    /**
     * @param expectedSize 心跳 {@code update.size}；回應沒有 Content-Length 時用來算百分比，未知傳 0
     * @param progress     已下載／總長度；可為 null
     * @param cancel       使用者取消時以 {@link TransferIo.CancelledException} 結束；可為 null
     */
    Path downloadClientUpdate(String path, long expectedSize, TransferIo.Progress progress,
                                     TransferCancel cancel) throws IOException, InterruptedException {
        if (!endpoint.active() || endpoint.url().isBlank()) {
            throw new IOException("雲端未連線");
        }
        if (!ClientUpdatePolicy.JAR_PATH.equals(path)) {
            throw new IOException("更新路徑無效");
        }
        Path dir = ClientUpdateInstaller.updateDir();
        Files.createDirectories(dir);
        Path part = dir.resolve("punchclock-client-next.jar.part");
        Path dest = dir.resolve("punchclock-client-next.jar");
        deleteQuietly(part);
        URI uri = URI.create(endpoint.url() + path);
        if (cancel != null) {
            cancel.throwIfCancelled();
        }
        java.util.concurrent.CompletableFuture<HttpResponse<java.io.InputStream>> request = endpoint.client().sendAsync(
                buildDownloadRequest(uri), HttpResponse.BodyHandlers.ofInputStream());
        if (cancel != null) {
            cancel.cancelOnCancel(request);
        }
        HttpResponse<java.io.InputStream> response;
        try {
            response = request.get();
        } catch (java.util.concurrent.CancellationException ex) {
            throw new TransferIo.CancelledException();
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw cause instanceof IOException ? (IOException) cause : new IOException(cause);
        }
        try (java.io.InputStream in = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("下載失敗，狀態碼：" + response.statusCode());
            }
            long total = response.headers().firstValueAsLong("Content-Length").orElse(expectedSize);
            if (total <= 0) {
                total = expectedSize;
            }
            if (cancel != null) {
                // 正在等資料時 read 不會自己醒來，取消時直接關掉串流
                cancel.onCancel(() -> {
                    try {
                        in.close();
                    } catch (Exception ignored) {
                        // best-effort
                    }
                });
            }
            try (java.io.OutputStream out = Files.newOutputStream(part)) {
                TransferIo.copy(in, out, total, progress, cancel);
            } catch (IOException ex) {
                if (cancel != null && cancel.isCancelled() && !(ex instanceof TransferIo.CancelledException)) {
                    throw new TransferIo.CancelledException();
                }
                throw ex;
            }
        } catch (IOException ex) {
            deleteQuietly(part);
            throw ex;
        } catch (RuntimeException ex) {
            deleteQuietly(part);
            throw ex;
        }
        Files.move(part, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
