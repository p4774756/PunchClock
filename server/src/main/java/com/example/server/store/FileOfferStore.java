package com.example.server.store;

import com.example.PeerFileRules;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 同事互傳檔案的記憶體暫存。過期前可重複下載，也可手動清除。
 */
public final class FileOfferStore {

    public static final long TTL_MS = PeerFileRules.OFFER_TTL_MS;
    public static final long FILE_ACTION_TTL_MS = PeerFileRules.OFFER_TTL_MS;
    /** 全體暫存約為單檔上限的 2 倍，避免小記憶體雲端把 100MB 檔再疊多份而 OOM。 */
    public static final long MAX_TOTAL_BYTES = PeerFileRules.MAX_BYTES * 2;
    public static final int MAX_OFFERS = 64;

    private final ConcurrentHashMap<String, Offer> offers = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public FileOfferStore() {
        this(System::currentTimeMillis);
    }

    FileOfferStore(LongSupplier clock) {
        this.clock = clock != null ? clock : System::currentTimeMillis;
    }

    public PutResult put(String fromClientId, String toClientId, String rawFilename, byte[] bytes) {
        return put(fromClientId, toClientId, rawFilename, bytes, PeerFileRules.KIND_FILE);
    }

    public PutResult put(String fromClientId, String toClientId, String rawFilename, byte[] bytes, String kind) {
        purgeExpired();
        String from = PeerFileRules.normalizeClientId(fromClientId);
        String to = PeerFileRules.normalizeClientId(toClientId);
        if (from.isEmpty() || to.isEmpty()) {
            return PutResult.fail("缺少收件人或發送者");
        }
        if (from.equals(to)) {
            return PutResult.fail("不能傳送檔案給自己");
        }
        String filename = PeerFileRules.sanitizeFilename(rawFilename);
        if (filename.isEmpty() || !PeerFileRules.isAllowedFilename(filename)) {
            return PutResult.fail("檔名無效");
        }
        if (bytes == null || bytes.length == 0) {
            return PutResult.fail("檔案不可為空");
        }
        if (bytes.length > PeerFileRules.MAX_BYTES) {
            return PutResult.fail("檔案不可超過 " + PeerFileRules.MAX_SIZE_LABEL);
        }
        if (offers.size() >= MAX_OFFERS || totalBytes() + bytes.length > MAX_TOTAL_BYTES) {
            return PutResult.fail("伺服器暫存已滿，請先清除舊檔或稍後再試");
        }

        String fileId = UUID.randomUUID().toString().replace("-", "");
        String normalizedKind = PeerFileRules.normalizeKind(kind);
        String mime = PeerFileRules.isFolderKind(normalizedKind)
                ? "application/zip"
                : PeerFileRules.mimeFor(filename);
        Offer offer = new Offer(
                fileId,
                from,
                to,
                filename,
                mime,
                normalizedKind,
                bytes,
                clock.getAsLong()
        );
        offers.put(fileId, offer);
        return PutResult.ok(offer);
    }

    public GetResult getForRecipient(String fileId, String requesterClientId) {
        return getForDownload(fileId, requesterClientId, false);
    }

    public GetResult getForDownload(String fileId, String requesterClientId, boolean admin) {
        purgeExpired();
        String id = trimToEmpty(fileId);
        if (id.isEmpty()) {
            return GetResult.notFound("找不到檔案");
        }
        Offer offer = offers.get(id);
        if (offer == null) {
            return GetResult.notFound("檔案不存在或已過期");
        }
        if (admin) {
            return GetResult.ok(offer);
        }
        String requester = PeerFileRules.normalizeClientId(requesterClientId);
        if (requester.isEmpty()) {
            return GetResult.notFound("找不到檔案");
        }
        if (requester.equals(offer.toClientId)) {
            offer.markDownloaded(clock.getAsLong());
            return GetResult.ok(offer);
        }
        if (requester.equals(offer.fromClientId)) {
            return GetResult.ok(offer);
        }
        return GetResult.forbidden("無權下載此檔案");
    }

    public DeleteResult delete(String fileId, String requesterClientId, boolean admin) {
        purgeExpired();
        String id = trimToEmpty(fileId);
        if (id.isEmpty()) {
            return DeleteResult.fail("找不到檔案");
        }
        Offer offer = offers.get(id);
        if (offer == null) {
            return DeleteResult.fail("檔案不存在或已過期");
        }
        if (!admin) {
            String requester = PeerFileRules.normalizeClientId(requesterClientId);
            if (requester.isEmpty()
                    || (!requester.equals(offer.fromClientId) && !requester.equals(offer.toClientId))) {
                return DeleteResult.forbidden("無權清除此檔案");
            }
        }
        offers.remove(id);
        return DeleteResult.ok(offer.filename);
    }

    public int deleteAll() {
        purgeExpired();
        int removed = offers.size();
        offers.clear();
        return removed;
    }

    public int size() {
        purgeExpired();
        return offers.size();
    }

    public long totalBytes() {
        long total = 0;
        for (Offer offer : offers.values()) {
            if (offer != null && offer.bytes != null) {
                total += offer.bytes.length;
            }
        }
        return total;
    }

    public List<Map<String, Object>> publicSnapshot() {
        purgeExpired();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Offer offer : offers.values()) {
            if (offer != null) {
                list.add(toPublicMap(offer));
            }
        }
        list.sort((a, b) -> Long.compare(
                numberOrZero(b.get("createdAtMs")),
                numberOrZero(a.get("createdAtMs"))));
        return list;
    }

    public List<Map<String, Object>> snapshotForClient(String clientId) {
        String id = PeerFileRules.normalizeClientId(clientId);
        if (id.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> item : publicSnapshot()) {
            if (id.equals(item.get("fromClientId")) || id.equals(item.get("toClientId"))) {
                list.add(item);
            }
        }
        return list;
    }

    void purgeExpired() {
        long now = clock.getAsLong();
        Iterator<Map.Entry<String, Offer>> it = offers.entrySet().iterator();
        while (it.hasNext()) {
            Offer offer = it.next().getValue();
            if (offer == null || now - offer.createdAtMs > TTL_MS) {
                it.remove();
            }
        }
    }

    private Map<String, Object> toPublicMap(Offer offer) {
        long now = clock.getAsLong();
        long expiresAtMs = offer.createdAtMs + TTL_MS;
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fileId", offer.fileId);
        map.put("fromClientId", offer.fromClientId);
        map.put("toClientId", offer.toClientId);
        map.put("filename", offer.filename);
        map.put("mime", offer.mime);
        map.put("kind", offer.kind);
        map.put("size", offer.size());
        map.put("createdAtMs", offer.createdAtMs);
        map.put("expiresAtMs", expiresAtMs);
        map.put("ttlMs", TTL_MS);
        map.put("remainingMs", Math.max(0L, expiresAtMs - now));
        map.put("downloadCount", offer.downloadCount());
        map.put("lastDownloadedAtMs", offer.lastDownloadedAtMs());
        map.put("status", offer.downloadCount() > 0 ? "downloaded" : "waiting");
        return map;
    }

    private static long numberOrZero(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Offer {
        public final String fileId;
        public final String fromClientId;
        public final String toClientId;
        public final String filename;
        public final String mime;
        public final String kind;
        public final byte[] bytes;
        public final long createdAtMs;
        private int downloadCount;
        private long lastDownloadedAtMs;

        Offer(String fileId, String fromClientId, String toClientId,
              String filename, String mime, String kind, byte[] bytes, long createdAtMs) {
            this.fileId = fileId;
            this.fromClientId = fromClientId;
            this.toClientId = toClientId;
            this.filename = filename;
            this.mime = mime;
            this.kind = kind == null || kind.isBlank() ? PeerFileRules.KIND_FILE : kind;
            this.bytes = bytes;
            this.createdAtMs = createdAtMs;
        }

        public int size() {
            return bytes == null ? 0 : bytes.length;
        }

        public synchronized void markDownloaded(long nowMs) {
            downloadCount++;
            lastDownloadedAtMs = nowMs;
        }

        public synchronized int downloadCount() {
            return downloadCount;
        }

        public synchronized long lastDownloadedAtMs() {
            return lastDownloadedAtMs;
        }
    }

    public static final class PutResult {
        public final boolean ok;
        public final String message;
        public final Offer offer;

        private PutResult(boolean ok, String message, Offer offer) {
            this.ok = ok;
            this.message = message;
            this.offer = offer;
        }

        public static PutResult ok(Offer offer) {
            return new PutResult(true,
                    "檔案已排入佇列，對方約 15 秒內收到通知，暫存保留 " + PeerFileRules.OFFER_TTL_LABEL,
                    offer);
        }

        public static PutResult fail(String message) {
            return new PutResult(false, message, null);
        }
    }

    public static final class GetResult {
        public enum Status { OK, NOT_FOUND, FORBIDDEN }

        public final Status status;
        public final String message;
        public final Offer offer;

        private GetResult(Status status, String message, Offer offer) {
            this.status = status;
            this.message = message;
            this.offer = offer;
        }

        public static GetResult ok(Offer offer) {
            return new GetResult(Status.OK, "ok", offer);
        }

        public static GetResult notFound(String message) {
            return new GetResult(Status.NOT_FOUND, message, null);
        }

        public static GetResult forbidden(String message) {
            return new GetResult(Status.FORBIDDEN, message, null);
        }
    }

    public static final class DeleteResult {
        public enum Status { OK, NOT_FOUND, FORBIDDEN }

        public final Status status;
        public final String message;

        private DeleteResult(Status status, String message) {
            this.status = status;
            this.message = message;
        }

        public static DeleteResult ok(String filename) {
            return new DeleteResult(Status.OK, "已清除「" + filename + "」");
        }

        public static DeleteResult fail(String message) {
            return new DeleteResult(Status.NOT_FOUND, message);
        }

        public static DeleteResult forbidden(String message) {
            return new DeleteResult(Status.FORBIDDEN, message);
        }
    }
}
