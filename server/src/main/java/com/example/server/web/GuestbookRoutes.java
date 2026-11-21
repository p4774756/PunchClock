package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.GuestbookStore;
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

/** 訪客簽名簿。讀取與送出公開；隱藏／刪除要站長。 */
public final class GuestbookRoutes {

    private final AuthService authService;
    private final GuestbookStore store;

    public GuestbookRoutes(AuthService authService, GuestbookStore store) {
        this.authService = authService;
        this.store = store;
    }

    public void register(Javalin app) {
        app.get("/api/guestbook", this::list);
        app.get("/api/guestbook/stats", this::stats);
        app.get("/api/guestbook/{file}", this::png);
        app.post("/api/guestbook", this::create);
        app.post("/api/guestbook/{id}/hide", this::hide);
        app.delete("/api/guestbook/{id}", this::delete);
    }

    private void list(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int limit = parseInt(ctx.queryParam("limit"), 5);
        boolean admin = authService.isAuth(ctx);
        GuestbookStore.Page result = store.list(page, limit, admin);
        List<Map<String, Object>> items = new ArrayList<>();
        for (GuestbookStore.Item item : result.items) {
            items.add(itemJson(item, admin));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("items", items);
        body.put("page", result.page);
        body.put("totalPages", result.totalPages);
        body.put("total", result.total);
        ctx.json(body);
    }

    private void stats(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        long dayAgo = System.currentTimeMillis() - GuestbookStore.DAY_MS;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("visible", store.countVisible());
        body.put("today", store.countSince(dayAgo));
        ctx.json(body);
    }

    private void png(Context ctx) {
        String file = ctx.pathParam("file");
        if (file == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到塗鴉"));
            return;
        }
        String id;
        if (file.endsWith(".png")) {
            id = file.substring(0, file.length() - 4);
        } else if (file.endsWith(".webp")) {
            id = file.substring(0, file.length() - 5);
        } else {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到塗鴉"));
            return;
        }
        boolean admin = authService.isAuth(ctx);
        byte[] data = store.readDoodleBytes(id, admin);
        if (data == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到塗鴉"));
            return;
        }
        String type = store.readDoodleContentType(id, admin);
        ctx.contentType(type != null ? type : "image/png");
        ctx.header("Cache-Control", "public, max-age=86400");
        ctx.result(data);
    }

    private void create(Context ctx) {
        GuestbookStore.AddRequest req = new GuestbookStore.AddRequest();
        String type = ctx.contentType() == null ? "" : ctx.contentType().toLowerCase();
        try {
            if (type.startsWith("multipart/")) {
                req.nickname = ctx.formParam("nickname");
                req.message = ctx.formParam("message");
                req.emoji = ctx.formParam("emoji");
                String honeypot = ctx.formParam("website");
                req.honeypotFilled = honeypot != null && !honeypot.isBlank();
                UploadedFile file = ctx.uploadedFile("doodle");
                if (file != null) {
                    try (InputStream in = file.content()) {
                        req.doodleRaw = readLimited(in);
                    }
                }
            } else if (type.contains("json") || type.isBlank()) {
                Map<?, ?> body = ctx.bodyAsClass(Map.class);
                if (body != null) {
                    req.nickname = stringField(body, "nickname");
                    req.message = stringField(body, "message");
                    req.emoji = stringField(body, "emoji");
                    String honeypot = stringField(body, "website");
                    req.honeypotFilled = honeypot != null && !honeypot.isBlank();
                    String doodle = stringField(body, "doodle");
                    if (doodle != null && !doodle.isBlank()) {
                        req.doodleRaw = GuestbookStore.decodeDoodlePayload(doodle);
                        if (req.doodleRaw == null) {
                            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", GuestbookStore.DOODLE_MESSAGE));
                            return;
                        }
                    }
                }
            } else {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "不支援的內容類型"));
                return;
            }
        } catch (IOException ex) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", GuestbookStore.DOODLE_MESSAGE));
            return;
        }
        if (req.doodleRaw != null && req.doodleRaw.length > GuestbookStore.DOODLE_MAX_BYTES + 1) {
            ctx.status(413).json(Map.of("success", false, "message", GuestbookStore.DOODLE_MESSAGE));
            return;
        }
        GuestbookStore.Result result = store.add(req, GraffitiRoutes.clientIp(ctx), System.currentTimeMillis());
        if (!result.ok) {
            ctx.status(result.status).json(Map.of("success", false, "message", result.message));
            return;
        }
        boolean admin = authService.isAuth(ctx);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", result.message);
        body.put("item", itemJson(result.item, admin));
        ctx.status(HttpStatus.CREATED).json(body);
    }

    private void hide(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String id = ctx.pathParam("id");
        if (!store.hide(id)) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這則留言"));
            return;
        }
        ctx.json(Map.of("success", true, "message", "已隱藏"));
    }

    private void delete(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String id = ctx.pathParam("id");
        if (!store.delete(id)) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這則留言"));
            return;
        }
        ctx.json(Map.of("success", true, "message", "已刪除"));
    }

    private static Map<String, Object> itemJson(GuestbookStore.Item item, boolean admin) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", item.id);
        json.put("nickname", item.nickname);
        json.put("message", item.message);
        json.put("emoji", item.emoji);
        json.put("createdAt", item.createdAt);
        json.put("doodleUrl", GuestbookStore.doodleUrlFor(item));
        if (admin) {
            json.put("hidden", item.hidden);
        }
        return json;
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String stringField(Map<?, ?> body, String key) {
        Object val = body.get(key);
        return val == null ? null : String.valueOf(val);
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        byte[] data = in.readNBytes(GuestbookStore.DOODLE_MAX_BYTES + 1);
        if (data.length > GuestbookStore.DOODLE_MAX_BYTES) {
            return new byte[GuestbookStore.DOODLE_MAX_BYTES + 1];
        }
        return data;
    }
}