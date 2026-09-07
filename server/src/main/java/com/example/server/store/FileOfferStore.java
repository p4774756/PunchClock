package com.example.server.store;

import com.example.PeerFileRules;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 同事互傳檔案的短暫記憶體暫存。本體不進 pendingActions，只活到 TTL。
 */
public final class FileOfferStore {

    public static final long TTL_MS = 10L * 60L * 1000L;
    public static final long FILE_ACTION_TTL_MS = 2L * 60L * 1000L;
    public static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;
    public static final int MAX_OFFERS = 32;

    private final ConcurrentHashMap<String, Offer> offers = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public FileOfferStore() {
        this(System::currentTimeMillis);
    }

    FileOfferStore(LongSupplier clock) {
        this.clock = clock != null ? clock : System::currentTimeMillis;
    }

    public PutResult put(String fromClientId, String toClientId, String rawFilename, byte[] bytes) {
        purgeExpired();
        String from = trimToEmpty(fromClientId);
        String to = trimToEmpty(toClientId);
        if (from.isEmpty() || to.isEmpty()) {
            return PutResult.fail("缺少收件人或發送者");
        }
        if (from.equals(to)) {
            return PutResult.fail("不能傳送檔案給自己");
        }
        String filename = PeerFileRules.sanitizeFilename(rawFilename);
        if (filename.isEmpty() || !PeerFileRules.isAllowedFilename(filename)) {
            return PutResult.fail("不支援的檔案類型，請改傳 " + PeerFileRules.allowedTypesHint());
        }
        if (bytes == null || bytes.length == 0) {
            return PutResult.fail("檔案不可為空");
        }
        if (bytes.length > PeerFileRules.MAX_BYTES) {
            return PutResult.fail("檔案不可超過 " + PeerFileRules.MAX_SIZE_LABEL);
        }
        if (offers.size() >= MAX_OFFERS || totalBytes() + bytes.length > MAX_TOTAL_BYTES) {
            return PutResult.fail("伺服器暫存已滿，請稍後再試");
        }

        String fileId = UUID.randomUUID().toString().replace("-", "");
        Offer offer = new Offer(
                fileId,
                from,
                to,
                filename,
                PeerFileRules.mimeFor(filename),
                bytes,
                clock.getAsLong()
        );
        offers.put(fileId, offer);
        return PutResult.ok(offer);
    }

    public GetResult getForRecipient(String fileId, String requesterClientId) {
        purgeExpired();
        String id = trimToEmpty(fileId);
        String requester = trimToEmpty(requesterClientId);
        if (id.isEmpty() || requester.isEmpty()) {
            return GetResult.notFound("找不到檔案");
        }
        Offer offer = offers.get(id);
        if (offer == null) {
            return GetResult.notFound("檔案不存在或已過期");
        }
        if (!requester.equals(offer.toClientId)) {
            return GetResult.forbidden("無權下載此檔案");
        }
        return GetResult.ok(offer);
    }

    public int size() {
        return offers.size();
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

    private long totalBytes() {
        long total = 0;
        for (Offer offer : offers.values()) {
            if (offer != null && offer.bytes != null) {
                total += offer.bytes.length;
            }
        }
        return total;
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
        public final byte[] bytes;
        public final long createdAtMs;

        Offer(String fileId, String fromClientId, String toClientId,
              String filename, String mime, byte[] bytes, long createdAtMs) {
            this.fileId = fileId;
            this.fromClientId = fromClientId;
            this.toClientId = toClientId;
            this.filename = filename;
            this.mime = mime;
            this.bytes = bytes;
            this.createdAtMs = createdAtMs;
        }

        public int size() {
            return bytes == null ? 0 : bytes.length;
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
            return new PutResult(true, "檔案已排入佇列，對方約 15 秒內收到通知", offer);
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
}
