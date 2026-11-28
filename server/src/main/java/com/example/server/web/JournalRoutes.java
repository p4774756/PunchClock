package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.JournalStore;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 心得動態牆 API。讀取與訪客留言公開；發文與管理需站長。 */
public final class JournalRoutes {

    private static final Gson GSON = new Gson();

    private final AuthService authService;
    private final JournalStore store;

    public JournalRoutes(AuthService authService, JournalStore store) {
        this.authService = authService;
        this.store = store;
    }

    public void register(Javalin app) {
        app.get("/api/journal", this::list);
        app.post("/api/journal", this::create);
        app.patch("/api/journal/{id}", this::patch);
        app.delete("/api/journal/{id}", this::deletePost);
        app.get("/api/journal/{id}/image", this::image);
        app.post("/api/journal/{id}/comments", this::comment);
        app.delete("/api/journal/{id}/comments/{commentId}", this::deleteComment);
    }

    private void list(Context ctx) {
        Long before = parseBefore(ctx.queryParam("before"));
        int limit = parseLimit(ctx.queryParam("limit"));
        List<JournalStore.Post> page = store.list(before, limit);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < page.size(); i++) {
            items.add(postJson(page.get(i)));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("items", items);
        if (!page.isEmpty() && store.hasMore(page.get(page.size() - 1).createdAt)) {
            body.put("nextBefore", page.get(page.size() - 1).createdAt);
        } else {
            body.put("nextBefore", null);
        }
        ctx.json(body);
    }

    private void create(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        ParsedPostForm form = parsePostForm(ctx);
        if (form.errorStatus > 0) {
            ctx.status(form.errorStatus).json(Map.of("success", false, "message", form.errorMessage));
            return;
        }
        JournalStore.Result result = store.createPost(
                form.body, form.tags, form.image, System.currentTimeMillis());
        respondPost(ctx, result, HttpStatus.CREATED);
    }

    private void patch(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String id = ctx.pathParam("id");
        ParsedPostForm form = parsePostForm(ctx);
        if (form.errorStatus > 0) {
            ctx.status(form.errorStatus).json(Map.of("success", false, "message", form.errorMessage));
            return;
        }
        boolean clearImage = "1".equals(ctx.header("X-Journal-Clear-Image"))
                || "true".equalsIgnoreCase(ctx.queryParam("clearImage"));
        JournalStore.Result result = store.updatePost(
                id, form.body, form.tags, form.image, clearImage, System.currentTimeMillis());
        respondPost(ctx, result, HttpStatus.OK);
    }

    private void deletePost(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String id = ctx.pathParam("id");
        if (!store.deletePost(id)) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這篇心得"));
            return;
        }
        ctx.json(Map.of("success", true, "message", "已刪除心得"));
    }

    private void image(Context ctx) {
        String id = ctx.pathParam("id");
        JournalStore.Post post = store.find(id);
        if (post == null || post.imageExt == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到圖片"));
            return;
        }
        String ext = JournalStore.safeImageExt(post.imageExt);
        if (ext == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到圖片"));
            return;
        }
        byte[] bytes = store.readImage(id, ext);
        if (bytes == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到圖片"));
            return;
        }
        String type = JournalStore.contentTypeForExt(ext);
        ctx.contentType(type != null ? type : "application/octet-stream");
        ctx.header("Cache-Control", "public, max-age=86400");
        ctx.result(bytes);
    }

    private void comment(Context ctx) {
        String postId = ctx.pathParam("id");
        Map<String, Object> body = readJsonBody(ctx);
        String nickname = stringField(body, "nickname");
        String text = stringField(body, "body");
        String honeypot = stringField(body, "website");
        JournalStore.Result result = store.addComment(
                postId, nickname, text, honeypot, GraffitiRoutes.clientIp(ctx), System.currentTimeMillis());
        if (!result.ok) {
            ctx.status(result.status).json(Map.of("success", false, "message", result.message));
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("message", result.message);
        payload.put("comment", commentJson(result.comment));
        ctx.status(HttpStatus.CREATED).json(payload);
    }

    private void deleteComment(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String postId = ctx.pathParam("id");
        String commentId = ctx.pathParam("commentId");
        if (!store.deleteComment(postId, commentId)) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到留言"));
            return;
        }
        ctx.json(Map.of("success", true, "message", "已刪除留言"));
    }

    private static void respondPost(Context ctx, JournalStore.Result result, HttpStatus okStatus) {
        if (!result.ok) {
            ctx.status(result.status).json(Map.of("success", false, "message", result.message));
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", result.message);
        body.put("item", postJson(result.post));
        ctx.status(okStatus).json(body);
    }

    private static ParsedPostForm parsePostForm(Context ctx) {
        ParsedPostForm form = new ParsedPostForm();
        String type = ctx.contentType() == null ? "" : ctx.contentType().toLowerCase();
        try {
            if (type.startsWith("multipart/")) {
                form.body = ctx.formParam("body");
                form.tags = splitTags(ctx.formParam("tags"));
                UploadedFile file = ctx.uploadedFile("image");
                if (file != null) {
                    try (InputStream in = file.content()) {
                        form.image = readLimited(in);
                    }
                }
            } else {
                Map<String, Object> json = readJsonBody(ctx);
                form.body = stringField(json, "body");
                Object tags = json.get("tags");
                if (tags instanceof List) {
                    List<?> raw = (List<?>) tags;
                    form.tags = new ArrayList<>();
                    for (int i = 0; i < raw.size(); i++) {
                        if (raw.get(i) != null) {
                            form.tags.add(String.valueOf(raw.get(i)));
                        }
                    }
                } else if (tags != null) {
                    form.tags = splitTags(String.valueOf(tags));
                }
            }
        } catch (IOException ex) {
            form.errorStatus = 400;
            form.errorMessage = JournalStore.IMAGE_MESSAGE;
            return form;
        }
        if (form.image != null && form.image.length > JournalStore.MAX_BYTES + 1) {
            form.errorStatus = 413;
            form.errorMessage = JournalStore.SIZE_MESSAGE;
        }
        return form;
    }

    private static Map<String, Object> postJson(JournalStore.Post post) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", post.id);
        json.put("body", post.body);
        json.put("tags", post.tags == null ? List.of() : post.tags);
        json.put("createdAt", post.createdAt);
        json.put("updatedAt", post.updatedAt);
        if (post.imageExt != null) {
            json.put("imageUrl", "/api/journal/" + post.id + "/image");
        } else {
            json.put("imageUrl", null);
        }
        List<Map<String, Object>> comments = new ArrayList<>();
        if (post.comments != null) {
            for (int i = 0; i < post.comments.size(); i++) {
                comments.add(commentJson(post.comments.get(i)));
            }
        }
        json.put("comments", comments);
        return json;
    }

    private static Map<String, Object> commentJson(JournalStore.Comment comment) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", comment.id);
        json.put("nickname", comment.nickname);
        json.put("body", comment.body);
        json.put("createdAt", comment.createdAt);
        return json;
    }

    private static String stringField(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) {
            return null;
        }
        return String.valueOf(map.get(key));
    }

    private static List<String> splitTags(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        String[] parts = raw.split("[,，\\s]+");
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isBlank()) {
                tags.add(parts[i].trim());
            }
        }
        return tags;
    }

    private static Long parseBefore(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int parseLimit(String raw) {
        if (raw == null || raw.isBlank()) {
            return 20;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return 20;
        }
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        byte[] data = in.readNBytes(JournalStore.MAX_BYTES + 1);
        if (data.length > JournalStore.MAX_BYTES) {
            return new byte[JournalStore.MAX_BYTES + 1];
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJsonBody(Context ctx) {
        try {
            Map<String, Object> map = GSON.fromJson(ctx.body(), RouteSupport.MAP_TYPE);
            return map == null ? Map.of() : map;
        } catch (JsonSyntaxException ex) {
            return Map.of();
        }
    }

    private static final class ParsedPostForm {
        String body;
        List<String> tags = new ArrayList<>();
        byte[] image;
        int errorStatus;
        String errorMessage;
    }
}