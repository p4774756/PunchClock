package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.PhotoStore;
import com.example.server.store.SlidePrep;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** 飲品牆：PDF、PowerPoint 與 Pages。 */
public final class PhotoRoutes {

    private final AuthService authService;
    private final PhotoStore photoStore = new PhotoStore();
    private final SlidePrep slides = SlidePrep.create();

    public PhotoRoutes(AuthService authService) {
        this.authService = authService;
    }

    public void register(Javalin app) {
        app.post("/api/photos", this::photoUpload);
        app.get("/api/photos", this::photoList);
        app.get("/api/photos/{photoId}/file", this::photoFile);
        app.get("/api/photos/{photoId}/preview", this::photoPreview);
        app.delete("/api/photos/{photoId}", this::photoDelete);
        app.delete("/api/photos", this::photoDeleteAll);
    }

    private void photoUpload(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        UploadedFile uploaded;
        try {
            uploaded = ctx.uploadedFile("file");
            if (uploaded == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "請選擇要上傳的簡報"));
                return;
            }
            byte[] data;
            try (InputStream in = uploaded.content()) {
                data = in.readAllBytes();
            }
            SlidePrep.Kind kind = SlidePrep.classify(uploaded.filename(), uploaded.contentType());
            if (kind != SlidePrep.Kind.PDF && kind != SlidePrep.Kind.PPT && kind != SlidePrep.Kind.PAGES) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                        "success", false, "message", "只接受 PDF、PowerPoint 或 Pages"));
                return;
            }
            if (!photoStore.isPersistent()) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                        "success", false, "message", "伺服器沒有飲品牆目錄，無法留簡報"));
                return;
            }
            SlidePrep.Result prepared = slides.prepare(uploaded.filename(), data);
            if (!prepared.ok) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", prepared.message));
                return;
            }
            PhotoStore.UploadResult result = photoStore.savePdf(uploaded.filename(), prepared.pdf);
            if (!result.ok) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", result.message));
                return;
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", result.message);
            response.put("photo", photoStore.describe(result.photo));
            ctx.json(response);
        } catch (Exception ex) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "上傳失敗：" + ex.getMessage()));
        }
    }

    private void photoList(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        ctx.json(Map.of("success", true, "photos", photoStore.slides()));
    }

    private void photoFile(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String photoId = ctx.pathParam("photoId");
        PhotoStore.Photo photo = photoStore.get(photoId);
        if (photo == null || !photo.isPdf()) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這份簡報"));
            return;
        }
        Path path = photo.filePath();
        long size;
        try {
            size = path == null ? -1L : Files.size(path);
        } catch (IOException ex) {
            size = -1L;
        }
        if (path == null || size <= 0L) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這份簡報"));
            return;
        }
        // Chrome 的 PDF 閱讀器會分段要檔。壓縮過或長度對不上時，它會顯示 0 頁。
        ctx.minSizeForCompression(Integer.MAX_VALUE);
        ctx.contentType("application/pdf");
        ctx.header("Accept-Ranges", "bytes");
        ctx.header("Content-Disposition", inlinePdfDisposition(photo.filename));
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("Cache-Control", "private");
        String range = ctx.header("Range");
        ByteSpan span = byteSpan(range, size);
        if (range != null && !range.isBlank() && span == null) {
            ctx.status(416);
            ctx.header("Content-Range", "bytes */" + size);
            return;
        }
        long start = span == null ? 0L : span.start;
        long end = span == null ? size - 1L : span.end;
        if (span != null) {
            ctx.status(206);
            ctx.header("Content-Range", "bytes " + start + "-" + end + "/" + size);
        }
        long length = end - start + 1L;
        ctx.header("Content-Length", Long.toString(length));
        try {
            InputStream in = Files.newInputStream(path);
            if (!skipFully(in, start)) {
                in.close();
                ctx.status(416);
                ctx.header("Content-Range", "bytes */" + size);
                return;
            }
            ctx.result(new BoundedInputStream(in, length));
        } catch (IOException ex) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這份簡報"));
        }
    }

    private void photoPreview(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        byte[] jpeg;
        try {
            jpeg = photoStore.previewJpeg(ctx.pathParam("photoId"));
        } catch (IOException ex) {
            jpeg = null;
        }
        if (jpeg == null || jpeg.length == 0) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "沒有預覽"));
            return;
        }
        ctx.minSizeForCompression(Integer.MAX_VALUE);
        ctx.contentType("image/jpeg");
        ctx.header("Content-Length", Integer.toString(jpeg.length));
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("Cache-Control", "private");
        ctx.result(jpeg);
    }

    /** 單一 bytes 範圍。格式不對、多段、或超出檔案時回 null。 */
    public static ByteSpan byteSpan(String header, long size) {
        if (header == null || size <= 0L) {
            return null;
        }
        String value = header.trim();
        if (value.length() < 7 || !value.regionMatches(true, 0, "bytes=", 0, 6)) {
            return null;
        }
        String spec = value.substring(6).trim();
        int comma = spec.indexOf(',');
        if (comma >= 0) {
            return null;
        }
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String left = spec.substring(0, dash).trim();
        String right = spec.substring(dash + 1).trim();
        try {
            long start;
            long end;
            if (left.isEmpty()) {
                if (right.isEmpty()) {
                    return null;
                }
                long tail = Long.parseLong(right);
                if (tail <= 0L) {
                    return null;
                }
                start = Math.max(0L, size - tail);
                end = size - 1L;
            } else {
                start = Long.parseLong(left);
                end = right.isEmpty() ? size - 1L : Long.parseLong(right);
            }
            if (start < 0L || start >= size || end < start) {
                return null;
            }
            if (end >= size) {
                end = size - 1L;
            }
            return new ByteSpan(start, end);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static final class ByteSpan {
        public final long start;
        public final long end;

        ByteSpan(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    private static boolean skipFully(InputStream in, long count) throws IOException {
        long left = count;
        while (left > 0L) {
            long skipped = in.skip(left);
            if (skipped > 0L) {
                left -= skipped;
                continue;
            }
            if (in.read() < 0) {
                return false;
            }
            left--;
        }
        return true;
    }

    private static final class BoundedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        BoundedInputStream(InputStream in, long remaining) {
            this.in = in;
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0L) {
                return -1;
            }
            int value = in.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0L) {
                return -1;
            }
            int asked = (int) Math.min(length, remaining);
            int count = in.read(buffer, offset, asked);
            if (count > 0) {
                remaining -= count;
            }
            return count;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    public static String inlinePdfDisposition(String filename) {
        String base = filename == null ? "" : filename.trim();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = base.replaceAll("[\\r\\n\"\\\\]", "_").trim();
        if (base.isEmpty()) {
            base = "slides";
        }
        String encoded = URLEncoder.encode(base + ".pdf", StandardCharsets.UTF_8).replace("+", "%20");
        return "inline; filename=\"slides.pdf\"; filename*=UTF-8''" + encoded;
    }

    private void photoDelete(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String photoId = ctx.pathParam("photoId");
        if (photoStore.delete(photoId)) {
            ctx.json(Map.of("success", true, "message", "已從飲品牆拿下來"));
        } else {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這份簡報"));
        }
    }

    private void photoDeleteAll(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        int removed = photoStore.deleteAll();
        ctx.json(Map.of("success", true, "message", "已清掉 " + removed + " 份", "removed", removed));
    }
}
