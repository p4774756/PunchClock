package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.PhotoStore;
import com.example.server.store.SlidePrep;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        InputStream in;
        try {
            in = photoStore.openPdf(photoId);
        } catch (Exception ex) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這份簡報"));
            return;
        }
        if (in == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這份簡報"));
            return;
        }
        ctx.contentType("application/pdf");
        ctx.header("Content-Length", Long.toString(photo.size));
        ctx.header("Content-Disposition", inlinePdfDisposition(photo.filename));
        ctx.header("X-Content-Type-Options", "nosniff");
        ctx.header("Cache-Control", "private");
        ctx.result(in);
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
