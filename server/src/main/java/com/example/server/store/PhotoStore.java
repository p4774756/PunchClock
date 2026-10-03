package com.example.server.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 開會展示用照片暫存。
 * <p>
 * 支援兩種模式：
 * <ul>
 *   <li>記憶體模式（預設）：照片以 Base64 存於記憶體，重啟後清空</li>
 *   <li>磁碟模式：照片存檔於指定目錄，元資料存於 index.json，重啟後保留</li>
 * </ul>
 * 若環境變數 {@code PHOTO_STORAGE_DIR} 存在（例如 /var/data/photos），則自動啟用磁碟模式。
 */
public final class PhotoStore {

    private static final Logger LOG = Logger.getLogger(PhotoStore.class.getName());
    private static final long MAX_PHOTO_BYTES = 10 * 1024 * 1024L;
    private static final int MAX_PHOTOS = 50;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type INDEX_TYPE = new TypeToken<List<PhotoMeta>>() {}.getType();

    private final ConcurrentHashMap<String, Photo> photos = new ConcurrentHashMap<>();
    private final Path storageDir;

    public PhotoStore() {
        this(resolveStorageDir());
    }

    public PhotoStore(Path storageDir) {
        if (storageDir != null) {
            this.storageDir = storageDir.toAbsolutePath().normalize();
            try {
                Files.createDirectories(this.storageDir);
                loadFromDisk();
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "無法初始化照片儲存目錄：" + this.storageDir, ex);
            }
        } else {
            this.storageDir = null;
        }
    }

    private static Path resolveStorageDir() {
        String env = System.getenv("PHOTO_STORAGE_DIR");
        if (env != null && !env.isBlank()) {
            return Paths.get(env.trim());
        }
        Path persistent = Paths.get("/var/data/photos");
        if (Files.isDirectory(persistent.getParent())) {
            return persistent;
        }
        return null;
    }

    public boolean isPersistent() {
        return storageDir != null;
    }

    public static final class Photo {
        public final String id;
        public final String filename;
        public final String mimeType;
        public final long size;
        public final long createdAtMs;
        private final Path filePath;
        private String cachedBase64;

        Photo(String id, String filename, String mimeType, long size, long createdAtMs, Path filePath) {
            this.id = id;
            this.filename = filename;
            this.mimeType = mimeType;
            this.size = size;
            this.createdAtMs = createdAtMs;
            this.filePath = filePath;
        }

        Photo(String id, String filename, String mimeType, String base64Data, long size, long createdAtMs) {
            this.id = id;
            this.filename = filename;
            this.mimeType = mimeType;
            this.size = size;
            this.createdAtMs = createdAtMs;
            this.filePath = null;
            this.cachedBase64 = base64Data;
        }

        public String base64Data() {
            if (cachedBase64 != null) {
                return cachedBase64;
            }
            if (filePath != null && Files.isRegularFile(filePath)) {
                try {
                    byte[] bytes = Files.readAllBytes(filePath);
                    cachedBase64 = Base64.getEncoder().encodeToString(bytes);
                    return cachedBase64;
                } catch (IOException ex) {
                    LOG.log(Level.WARNING, "無法讀取照片檔案：" + filePath, ex);
                }
            }
            return "";
        }

        void deleteFile() {
            if (filePath != null) {
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException ignored) {
                }
            }
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
        String safeName = sanitizeFilename(filename);
        long now = System.currentTimeMillis();
        Photo photo;

        if (storageDir != null) {
            String ext = extensionOf(safeName);
            Path filePath = storageDir.resolve(id + ext);
            try {
                Files.write(filePath, data);
            } catch (IOException ex) {
                LOG.log(Level.WARNING, "無法寫入照片檔案：" + filePath, ex);
                return UploadResult.fail("無法儲存照片：" + ex.getMessage());
            }
            photo = new Photo(id, safeName, mimeType, data.length, now, filePath);
            photos.put(id, photo);
            saveIndex();
        } else {
            String base64 = Base64.getEncoder().encodeToString(data);
            photo = new Photo(id, safeName, mimeType, base64, data.length, now);
            photos.put(id, photo);
        }

        return UploadResult.success(photo);
    }

    public boolean delete(String photoId) {
        Photo removed = photos.remove(photoId);
        if (removed != null) {
            removed.deleteFile();
            if (storageDir != null) {
                saveIndex();
            }
            return true;
        }
        return false;
    }

    public int deleteAll() {
        int count = photos.size();
        for (Photo p : photos.values()) {
            p.deleteFile();
        }
        photos.clear();
        if (storageDir != null) {
            saveIndex();
        }
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
            item.put("dataUrl", "data:" + p.mimeType + ";base64," + p.base64Data());
            item.put("size", p.size);
            item.put("createdAtMs", p.createdAtMs);
            result.add(item);
        }
        return result;
    }

    public int count() {
        return photos.size();
    }

    private void loadFromDisk() {
        if (storageDir == null) {
            return;
        }
        Path indexFile = storageDir.resolve("index.json");
        if (!Files.isRegularFile(indexFile)) {
            return;
        }
        try (InputStream in = Files.newInputStream(indexFile)) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            List<PhotoMeta> metas = GSON.fromJson(json, INDEX_TYPE);
            if (metas == null) {
                return;
            }
            int loaded = 0;
            for (PhotoMeta meta : metas) {
                Path filePath = storageDir.resolve(meta.fileId);
                if (!Files.isRegularFile(filePath)) {
                    continue;
                }
                long fileSize;
                try {
                    fileSize = Files.size(filePath);
                } catch (IOException ex) {
                    continue;
                }
                Photo photo = new Photo(meta.id, meta.filename, meta.mimeType, fileSize, meta.createdAtMs, filePath);
                photos.put(meta.id, photo);
                loaded++;
            }
            LOG.info("從磁碟載入 " + loaded + " 張照片");
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "無法載入照片索引：" + indexFile, ex);
        }
    }

    private void saveIndex() {
        if (storageDir == null) {
            return;
        }
        List<PhotoMeta> metas = new ArrayList<>();
        for (Photo p : photos.values()) {
            if (p.filePath != null) {
                metas.add(new PhotoMeta(p.id, p.filePath.getFileName().toString(), p.filename, p.mimeType, p.createdAtMs));
            }
        }
        Path indexFile = storageDir.resolve("index.json");
        try (OutputStream out = Files.newOutputStream(indexFile)) {
            out.write(GSON.toJson(metas).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "無法儲存照片索引：" + indexFile, ex);
        }
    }

    private static String sanitizeFilename(String filename) {
        if (filename == null) {
            return "photo";
        }
        String safe = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        return safe.length() > 100 ? safe.substring(0, 100) : safe;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            return filename.substring(dot);
        }
        return ".jpg";
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

    private static final class PhotoMeta {
        String id;
        String fileId;
        String filename;
        String mimeType;
        long createdAtMs;

        PhotoMeta(String id, String fileId, String filename, String mimeType, long createdAtMs) {
            this.id = id;
            this.fileId = fileId;
            this.filename = filename;
            this.mimeType = mimeType;
            this.createdAtMs = createdAtMs;
        }
    }
}
