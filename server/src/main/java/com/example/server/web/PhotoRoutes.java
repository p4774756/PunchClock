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

/** 飲品牆：照片與筆記。 */
public final class PhotoRoutes {

    private final AuthService authService;
    private final PhotoStore photoStore = new PhotoStore();

    public PhotoRoutes(AuthService authService) {
        this.authService = authService;
    }

    public void register(Javalin app) {
        app.post("/api/photos", this::photoUpload);
        app.post("/api/photos/notes", this::noteCreate);
        app.put("/api/photos/notes/{noteId}", this::noteUpdate);
        app.get("/api/photos", this::photoList);
        app.delete("/api/photos/{photoId}", this::photoDelete);
        app.delete("/api/photos", this::photoDeleteAll);
    }

    public static final class NoteBody {
        public String text;
        public String link;
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
            ctx.json(Map.of("success", true, "message", "已從飲品牆拿下來"));
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
        ctx.json(Map.of("success", true, "message", "已清掉 " + removed + " 張", "removed", removed));
    }

    private void noteCreate(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        NoteBody body;
        try {
            body = ctx.bodyAsClass(NoteBody.class);
        } catch (Exception ex) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "請寫一點內容"));
            return;
        }
        if (body == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "請寫一點內容"));
            return;
        }
        writeNoteResult(ctx, photoStore.addNote(body.text, body.link));
    }

    private void noteUpdate(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        NoteBody body;
        try {
            body = ctx.bodyAsClass(NoteBody.class);
        } catch (Exception ex) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "請寫一點內容"));
            return;
        }
        if (body == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "請寫一點內容"));
            return;
        }
        writeNoteResult(ctx, photoStore.updateNote(ctx.pathParam("noteId"), body.text, body.link));
    }

    private static void writeNoteResult(Context ctx, PhotoStore.NoteResult result) {
        if (!result.ok) {
            HttpStatus status = "找不到這張筆記".equals(result.message)
                    ? HttpStatus.NOT_FOUND
                    : HttpStatus.BAD_REQUEST;
            ctx.status(status).json(Map.of("success", false, "message", result.message));
            return;
        }
        Map<String, Object> note = new LinkedHashMap<>();
        note.put("id", result.note.id);
        note.put("kind", "note");
        note.put("text", result.note.text);
        note.put("link", result.note.link);
        note.put("createdAtMs", result.note.createdAtMs);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", result.message);
        response.put("note", note);
        ctx.json(response);
    }
}
