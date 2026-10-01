package com.example.server.store;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 開會展示用照片暫存（記憶體，重啟後清空）。
 * 每張照片以 Base64 方式存放，方便前端直接顯示。
 */
public final class PhotoStore {

    private static final long MAX_PHOTO_BYTES = 10 * 1024 * 1024L;
    private static final int MAX_PHOTOS = 50;

    private final ConcurrentHashMap<String, Photo> photos = new ConcurrentHashMap<>();

    public static final class Photo {
        public final String id;
        public final String filename;
        public final String mimeType;
        public final String base64Data;
        public final long size;
        public final long createdAtMs;

        Photo(String id, String filename, String mimeType, String base64Data, long size) {
            this.id = id;
            this.filename = filename;
            this.mimeType = mimeType;
            this.base64Data = base64Data;
            this.size = size;
            this.createdAtMs = System.currentTimeMillis();
        }
    }

    public static final class UploadResult {
        public final boolean ok;
        public final String message;
        public final Photo photo;

        private UploadResult(boolean ok, String message, Photo photo) {
            this.ok = ok;
            this.message = message;
            this.photo = photo;
        }

        static UploadResult success(Photo photo) {
            return new UploadResult(true, "上傳成功", photo);
        }

        static UploadResult fail(String message) {
            return new UploadResult(false, message, null);
        }
    }

    public UploadResult upload(String filename, String mimeType, byte[] data) {
        if (filename == null || filename.isBlank()) {
            return UploadResult.fail("檔名不可為空");
        }
        if (data == null || data.length == 0) {
            return UploadResult.fail("檔案內容不可為空");
        }
        if (data.length > MAX_PHOTO_BYTES) {
            return UploadResult.fail("單張照片不可超過 10 MB");
        }
        if (photos.size() >= MAX_PHOTOS) {
            return UploadResult.fail("已達上限 " + MAX_PHOTOS + " 張，請先清除部分照片");
        }
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = guessMimeType(filename);
        }
        if (!mimeType.startsWith("image/")) {
            return UploadResult.fail("僅支援圖片格式");
        }
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String base64 = Base64.getEncoder().encodeToString(data);
        Photo photo = new Photo(id, sanitizeFilename(filename), mimeType, base64, data.length);
        photos.put(id, photo);
        return UploadResult.success(photo);
    }

    public boolean delete(String photoId) {
        return photos.remove(photoId) != null;
    }

    public int deleteAll() {
        int count = photos.size();
        photos.clear();
        return count;
    }

    public Photo get(String photoId) {
        return photos.get(photoId);
    }

    public List<Map<String, Object>> snapshot() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Photo p : photos.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", p.id);
            item.put("filename", p.filename);
            item.put("mimeType", p.mimeType);
            item.put("dataUrl", "data:" + p.mimeType + ";base64," + p.base64Data);
            item.put("size", p.size);
            item.put("createdAtMs", p.createdAtMs);
            result.add(item);
        }
        return result;
    }

    public int count() {
        return photos.size();
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "photo";
        }
        String safe = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.length() > 100 ? safe.substring(0, 100) : safe;
    }

    private static String guessMimeType(String filename) {
        if (filename == null) {
            return "image/jpeg";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "image/jpeg";
    }
}
