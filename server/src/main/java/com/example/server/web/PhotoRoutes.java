package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.PhotoStore;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** 菜單牆照片。 */
public final class PhotoRoutes {

    private final AuthService authService;
    private final PhotoStore photoStore = new PhotoStore();

    public PhotoRoutes(AuthService authService) {
        this.authService = authService;
    }

    public void register(Javalin app) {
        app.post("/api/photos", this::photoUpload);
        app.get("/api/photos", this::photoList);
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
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "請選擇要上傳的照片"));
                return;
            }
            byte[] data;
            try (InputStream in = uploaded.content()) {
                data = in.readAllBytes();
            }
            PhotoStore.UploadResult result = photoStore.upload(
                    uploaded.filename(),
                    uploaded.contentType(),
                    data
            );
            if (!result.ok) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", result.message));
                return;
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", result.message);
            response.put("photo", Map.of(
                    "id", result.photo.id,
                    "filename", result.photo.filename,
                    "mimeType", result.photo.mimeType,
                    "dataUrl", "data:" + result.photo.mimeType + ";base64," + result.photo.base64Data(),
                    "size", result.photo.size,
                    "createdAtMs", result.photo.createdAtMs
            ));
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
        ctx.json(Map.of("success", true, "photos", photoStore.snapshot()));
    }

    private void photoDelete(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String photoId = ctx.pathParam("photoId");
        if (photoStore.delete(photoId)) {
            ctx.json(Map.of("success", true, "message", "已刪除照片"));
        } else {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到該照片"));
        }
    }

    private void photoDeleteAll(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        int removed = photoStore.deleteAll();
        ctx.json(Map.of("success", true, "message", "已清除 " + removed + " 張照片", "removed", removed));
    }
}
