package com.example.service;

import com.example.ClientUpdatePolicy;
import com.example.PeerFileRules;
import com.example.PeerFolderPacker;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** 同事傳檔、直傳，以及下載桌面端更新檔。走與心跳相同的 HttpClient。 */
final class PeerFileClient {

    private final HeartbeatEndpoint endpoint;

    PeerFileClient(HeartbeatEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * 傳送檔案或資料夾給同事（資料夾會先壓成 ZIP；經伺服器暫存，對方心跳收到通知後再下載）。
     */
    void sendPeerFile(String toClientId, Path file, Consumer<String> logger, Consumer<Boolean> callback) {
        sendPeerFile(toClientId, file, logger, null, null, callback);
    }

    void sendPeerFile(String toClientId, Path file, Consumer<String> logger,
                             TransferIo.Progress progress, Consumer<Boolean> callback) {
        sendPeerFile(toClientId, file, logger, progress, null, callback);
    }

    /**
     * @param progress     位元組進度（準備複製／實際上傳）
     * @param statusUpdate 階段文字（壓縮／準備／上傳）；可為 null
     */
    void sendPeerFile(String toClientId, Path file, Consumer<String> logger,
                             TransferIo.Progress progress, Consumer<String> statusUpdate,
                             Consumer<Boolean> callback) {
        sendPeerFile(toClientId, file, logger, progress, statusUpdate, null, callback);
    }

    /**
     * @param cancel 可為 null；取消後 callback 收到 false，並寫一行 [取消] 日誌
     */
    void sendPeerFile(String toClientId, Path file, Consumer<String> logger,
                             TransferIo.Progress progress, Consumer<String> statusUpdate,
                             TransferCancel cancel, Consumer<Boolean> callback) {
        sendPeerFile(toClientId, file == null ? null : Collections.singletonList(file), logger, progress,
                statusUpdate, cancel, callback);
    }

    /** 多個檔案／資料夾會先打包成同一個 ZIP 再上傳；只有一個項目時行為與單檔相同。 */
    void sendPeerFile(String toClientId, List<Path> files, Consumer<String> logger,
                             TransferIo.Progress progress, Consumer<String> statusUpdate,
                             TransferCancel cancel, Consumer<Boolean> callback) {
        if (!endpoint.active() || endpoint.url().isBlank()) {
            endpoint.log(logger, "[警告] [檔案] 雲端未連線，無法傳送檔案");
            if (callback != null) callback.accept(false);
            return;
        }
        if (toClientId == null || toClientId.isBlank()) {
            endpoint.log(logger, "[警告] [檔案] 請選擇收件同事");
            if (callback != null) callback.accept(false);
            return;
        }
        if (files == null || files.isEmpty() || files.stream().anyMatch(java.util.Objects::isNull)) {
            endpoint.log(logger, "[警告] [檔案] 找不到要傳送的檔案");
            if (callback != null) callback.accept(false);
            return;
        }

        final String targetId = toClientId.trim();
        final TransferCancel token = cancel != null ? cancel : new TransferCancel();
        final List<Path> sources = new ArrayList<>(files);
        Thread worker = new Thread(() -> sendPeerFileOnWorker(targetId, sources, logger, progress, statusUpdate,
                token, callback), "peer-file-upload");
        worker.setDaemon(true);
        worker.start();
    }

    private void sendPeerFileOnWorker(String toClientId, List<Path> files, Consumer<String> logger,
                                      TransferIo.Progress progress, Consumer<String> statusUpdate,
                                      TransferCancel cancel, Consumer<Boolean> callback) {
        String filename;
        Path contentPath;
        long contentSize;
        String kind;
        PeerFolderPacker.PackResult packed = null;
        Path multipartTemp = null;
        String displayName = HeartbeatService.displayNameOf(files);
        try {
            Outgoing outgoing = prepareOutgoing(files, logger, progress, statusUpdate, cancel);
            if (outgoing == null) {
                if (callback != null) callback.accept(false);
                return;
            }
            filename = outgoing.filename;
            contentPath = outgoing.path;
            contentSize = outgoing.size;
            kind = outgoing.kind;
            packed = outgoing.packed;

            notifyStatus(statusUpdate, "正在上傳「" + filename + "」到伺服器…（"
                    + PeerFileRules.formatSize(contentSize) + "，分段續傳）");
            TransferIo.Progress chunkedProgress = TransferIo.throttle(progress);
            chunkedProgress.onProgress(0L, contentSize);
            ChunkedUploader.Result chunked = new ChunkedUploader(
                    () -> endpoint.client(), endpoint.url(), endpoint.token(), endpoint.workerId(), logger, statusUpdate)
                    .upload(toClientId, contentPath, contentSize, filename, kind, chunkedProgress, cancel);
            if (!chunked.unsupported) {
                if (packed != null) {
                    packed.deleteQuietly();
                    packed = null;
                }
                if (chunked.ok) {
                    notifyStatus(statusUpdate, "上傳完成");
                    endpoint.log(logger, "[成功] [檔案] 已送出「" + filename + "」給【" + toClientId
                            + "】（" + PeerFileRules.formatSize(contentSize)
                            + "，保留 " + PeerFileRules.OFFER_TTL_LABEL + "）");
                } else {
                    endpoint.log(logger, "[失敗] [檔案] 送出「" + filename + "」失敗：" + chunked.message);
                }
                if (callback != null) callback.accept(chunked.ok);
                return;
            }
            endpoint.log(logger, "[提示] [檔案] 伺服器尚未支援分段上傳（請更新雲端），改用整檔上傳");

            String mime = PeerFileRules.isFolderKind(kind) ? "application/zip" : PeerFileRules.mimeFor(filename);
            String boundary = "PunchClockFile" + UUID.randomUUID().toString().replace("-", "");
            multipartTemp = Files.createTempFile("punchclock-upload-", ".multipart");
            notifyStatus(statusUpdate, "正在準備上傳「" + filename + "」（"
                    + PeerFileRules.formatSize(contentSize) + "）…");
            TransferIo.Progress prepareProgress = TransferIo.throttle(progress);
            writeMultipart(multipartTemp, boundary, orderedFields(
                    "fromClientId", endpoint.workerId(),
                    "toClientId", PeerFileRules.normalizeClientId(toClientId),
                    "filename", filename,
                    "kind", kind
            ), filename, mime, contentPath, prepareProgress, cancel);
            cancel.throwIfCancelled();

            long uploadBytes = Files.size(multipartTemp);
            notifyStatus(statusUpdate, "正在上傳「" + filename + "」到伺服器…");
            TransferIo.Progress uploadProgress = TransferIo.throttle(progress);
            if (uploadProgress != null) {
                uploadProgress.onProgress(0L, uploadBytes);
            }
            String target = endpoint.url() + "/api/peer/file";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Authorization", "Bearer " + endpoint.token())
                    .timeout(HeartbeatService.FILE_TRANSFER_TIMEOUT)
                    .POST(TransferIo.ofFile(multipartTemp, uploadProgress, cancel))
                    .build();

            final String sentName = filename;
            final long sentSize = contentSize;
            final Path cleanupMultipart = multipartTemp;
            final PeerFolderPacker.PackResult cleanupPacked = packed;
            multipartTemp = null;
            packed = null;

            CompletableFuture<HttpResponse<String>> upload =
                    endpoint.client().sendAsync(request, HttpResponse.BodyHandlers.ofString());
            cancel.cancelOnCancel(upload);
            upload
                    .whenComplete((response, error) -> {
                        deleteQuietly(cleanupMultipart);
                        if (cleanupPacked != null) {
                            cleanupPacked.deleteQuietly();
                        }
                    })
                    .thenAccept(response -> {
                        boolean ok = response.statusCode() == 200;
                        if (ok) {
                            if (progress != null) {
                                progress.onProgress(uploadBytes, uploadBytes);
                            }
                            notifyStatus(statusUpdate, "上傳完成");
                            endpoint.log(logger, "[成功] [檔案] 已送出「" + sentName + "」給【" + toClientId
                                    + "】（" + PeerFileRules.formatSize(sentSize)
                                    + "，保留 " + PeerFileRules.OFFER_TTL_LABEL + "）");
                        } else {
                            String serverMessage = extractJsonMessage(response.body());
                            endpoint.log(logger, "[失敗] [檔案] 送出「" + sentName + "」失敗，狀態碼："
                                    + response.statusCode()
                                    + (serverMessage.isEmpty() ? "" : "，" + serverMessage));
                        }
                        if (callback != null) callback.accept(ok);
                    })
                    .exceptionally(ex -> {
                        if (cancel.isCancelled()) {
                            endpoint.log(logger, "[取消] [檔案] 已取消傳送「" + sentName + "」");
                        } else {
                            endpoint.log(logger, "[失敗] [檔案] 送出「" + sentName + "」異常：" + HeartbeatService.describeTransferFailure(ex));
                        }
                        if (callback != null) callback.accept(false);
                        return null;
                    });
        } catch (OutOfMemoryError ex) {
            deleteQuietly(multipartTemp);
            if (packed != null) {
                packed.deleteQuietly();
            }
            endpoint.log(logger, "[失敗] [檔案] 本機記憶體不足，無法處理這麼大的檔案");
            if (callback != null) callback.accept(false);
        } catch (Exception ex) {
            deleteQuietly(multipartTemp);
            if (packed != null) {
                packed.deleteQuietly();
            }
            if (cancel.isCancelled()) {
                endpoint.log(logger, "[取消] [檔案] 已取消傳送「" + displayName + "」");
            } else {
                endpoint.log(logger, "[失敗] [檔案] 送出異常：" + HeartbeatService.describeTransferFailure(ex));
            }
            if (callback != null) callback.accept(false);
        }
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
     * @param callback 收到 {@link HeartbeatService.RelayOutcome#UNSUPPORTED_SERVER}／{@link HeartbeatService.RelayOutcome#RECIPIENT_UNAVAILABLE}
     *                 時可改用 {@link #sendPeerFile} 伺服器暫存
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

    /**
     * 下載同事傳來的檔案（收件人或發送者在過期前皆可）。
     */
    void downloadPeerFile(String fileId, Path destination,
                                 Consumer<String> logger, Consumer<Boolean> callback) {
        downloadPeerFile(fileId, destination, logger, null, callback);
    }

    void downloadPeerFile(String fileId, Path destination,
                                 Consumer<String> logger, TransferIo.Progress progress,
                                 Consumer<Boolean> callback) {
        downloadPeerFile(fileId, destination, logger, progress, null, callback);
    }

    /**
     * @param cancel 可為 null；取消後刪除 .part、callback 收到 false，並寫一行 [取消] 日誌
     */
    void downloadPeerFile(String fileId, Path destination,
                                 Consumer<String> logger, TransferIo.Progress progress,
                                 TransferCancel cancel, Consumer<Boolean> callback) {
        if (!endpoint.active() || endpoint.url().isBlank()) {
            endpoint.log(logger, "[警告] [檔案] 雲端未連線，無法下載");
            if (callback != null) callback.accept(false);
            return;
        }
        if (fileId == null || fileId.isBlank() || destination == null) {
            endpoint.log(logger, "[警告] [檔案] 缺少檔案編號或儲存路徑");
            if (callback != null) callback.accept(false);
            return;
        }

        Path dest = PeerFileRules.resolveSavePath(destination, destination.getFileName() != null
                ? destination.getFileName().toString() : "download");
        String target = endpoint.url() + "/api/peer/file/" + urlEncode(fileId.trim())
                + "?clientId=" + urlEncode(endpoint.workerId());
        final TransferCancel token = cancel != null ? cancel : new TransferCancel();
        final String savedName = dest.getFileName() != null ? dest.getFileName().toString() : "檔案";
        try {
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path part = dest.resolveSibling(dest.getFileName().toString() + ".part");
            deleteQuietly(part);
            TransferIo.Progress downloadProgress = TransferIo.throttle(progress);
            CompletableFuture<Integer> download =
                    downloadToFile(URI.create(target), part, logger, downloadProgress, token);
            token.cancelOnCancel(download);
            download
                    .thenAccept(statusCode -> {
                        try {
                            if (token.isCancelled()) {
                                deleteQuietly(part);
                                endpoint.log(logger, "[取消] [檔案] 已取消下載「" + savedName + "」");
                                if (callback != null) callback.accept(false);
                                return;
                            }
                            if (statusCode != 200) {
                                String serverMessage = "";
                                try {
                                    if (Files.isRegularFile(part) && Files.size(part) < 64 * 1024) {
                                        serverMessage = extractJsonMessage(
                                                Files.readString(part, StandardCharsets.UTF_8));
                                    }
                                } catch (Exception ignored) {
                                    // ignore
                                }
                                deleteQuietly(part);
                                endpoint.log(logger, "[失敗] [檔案] 下載失敗，狀態碼：" + statusCode
                                        + (serverMessage.isEmpty() ? "" : "，" + serverMessage));
                                if (callback != null) callback.accept(false);
                                return;
                            }
                            Files.move(part, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            long size = Files.size(dest);
                            endpoint.log(logger, "[成功] [檔案] 已儲存：" + dest.toAbsolutePath()
                                    + "（" + PeerFileRules.formatSize(size) + "）");
                            if (callback != null) callback.accept(true);
                        } catch (Exception ex) {
                            deleteQuietly(part);
                            endpoint.log(logger, "[失敗] [檔案] 寫入本機失敗：" + ex.getMessage());
                            if (callback != null) callback.accept(false);
                        }
                    })
                    .exceptionally(ex -> {
                        deleteQuietly(part);
                        if (token.isCancelled()) {
                            endpoint.log(logger, "[取消] [檔案] 已取消下載「" + savedName + "」");
                        } else {
                            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                            endpoint.log(logger, "[失敗] [檔案] 下載異常：" + cause.getMessage());
                        }
                        if (callback != null) callback.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            endpoint.log(logger, "[失敗] [檔案] 下載異常：" + ex.getMessage());
            if (callback != null) callback.accept(false);
        }
    }

    /**
     * 手動清除伺服器上的暫存檔（發送者或收件人）。
     */
    void deletePeerFile(String fileId, Consumer<String> logger, Consumer<Boolean> callback) {
        if (!endpoint.active() || endpoint.url().isBlank()) {
            endpoint.log(logger, "[警告] [檔案] 雲端未連線，無法清除");
            if (callback != null) callback.accept(false);
            return;
        }
        if (fileId == null || fileId.isBlank()) {
            endpoint.log(logger, "[警告] [檔案] 缺少檔案編號");
            if (callback != null) callback.accept(false);
            return;
        }
        String target = endpoint.url() + "/api/peer/file/" + urlEncode(fileId.trim())
                + "?clientId=" + urlEncode(endpoint.workerId());
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .header("Authorization", "Bearer " + endpoint.token())
                    .timeout(HeartbeatService.REQUEST_TIMEOUT)
                    .DELETE();
            String encodedClient = urlEncode(endpoint.workerId());
            if (isAsciiHeaderValue(encodedClient)) {
                builder.header("X-PunchClock-Client", encodedClient);
            }
            endpoint.client().sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        boolean ok = response.statusCode() == 200;
                        String serverMessage = extractJsonMessage(response.body());
                        if (ok) {
                            endpoint.log(logger, "[成功] [檔案] " + (serverMessage.isEmpty() ? "已清除暫存檔" : serverMessage));
                        } else {
                            endpoint.log(logger, "[失敗] [檔案] 清除失敗，狀態碼：" + response.statusCode()
                                    + (serverMessage.isEmpty() ? "" : "，" + serverMessage));
                        }
                        if (callback != null) callback.accept(ok);
                    })
                    .exceptionally(ex -> {
                        endpoint.log(logger, "[失敗] [檔案] 清除異常：" + ex.getMessage());
                        if (callback != null) callback.accept(false);
                        return null;
                    });
        } catch (Exception ex) {
            endpoint.log(logger, "[失敗] [檔案] 清除異常：" + ex.getMessage());
            if (callback != null) callback.accept(false);
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

    private java.util.concurrent.CompletableFuture<Integer> downloadToFile(
            URI uri, Path dest, Consumer<String> logger, TransferIo.Progress progress, TransferCancel cancel) {
        if (cancel.isCancelled()) {
            return CompletableFuture.failedFuture(new TransferIo.CancelledException());
        }
        CompletableFuture<HttpResponse<java.io.InputStream>> request =
                endpoint.client().sendAsync(buildDownloadRequest(uri), HttpResponse.BodyHandlers.ofInputStream());
        cancel.cancelOnCancel(request);
        return request
                .thenCompose(response -> {
                    int code = response.statusCode();
                    if (code >= 300 && code < 400) {
                        String location = response.headers().firstValue("Location").orElse("").trim();
                        try {
                            response.body().close();
                        } catch (Exception ignored) {
                            // ignore
                        }
                        if (location.isEmpty()) {
                            return java.util.concurrent.CompletableFuture.completedFuture(code);
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
                            return java.util.concurrent.CompletableFuture.completedFuture(code);
                        }
                        if (!sameOrigin(uri, next)) {
                            endpoint.log(logger, "[警告] [檔案] 下載轉址跨網域，已中止：" + next);
                            return java.util.concurrent.CompletableFuture.completedFuture(code);
                        }
                        deleteQuietly(dest);
                        return downloadToFile(next, dest, logger, progress, cancel);
                    }
                    try (java.io.InputStream in = response.body();
                         java.io.OutputStream out = Files.newOutputStream(dest)) {
                        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                        TransferIo.copy(in, out, total, progress, cancel);
                    } catch (Exception ex) {
                        deleteQuietly(dest);
                        return java.util.concurrent.CompletableFuture.failedFuture(ex);
                    }
                    if (cancel.isCancelled()) {
                        deleteQuietly(dest);
                        return CompletableFuture.failedFuture(new TransferIo.CancelledException());
                    }
                    return java.util.concurrent.CompletableFuture.completedFuture(code);
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

    private static Map<String, String> orderedFields(String... keyValues) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            fields.put(keyValues[i], keyValues[i + 1]);
        }
        return fields;
    }

    private static void writeMultipart(Path dest, String boundary, Map<String, String> fields,
                                       String filename, String mime, Path fileContent,
                                       TransferIo.Progress progress, TransferCancel cancel)
            throws java.io.IOException {
        byte[] crlf = "\r\n".getBytes(StandardCharsets.UTF_8);
        long contentSize = Files.size(fileContent);
        try (java.io.OutputStream out = Files.newOutputStream(dest);
             java.io.InputStream in = Files.newInputStream(fileContent)) {
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
            TransferIo.copy(in, out, contentSize, progress, cancel);
            out.write(crlf);
            out.write(("--" + boundary + "--").getBytes(StandardCharsets.UTF_8));
            out.write(crlf);
        }
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
}
