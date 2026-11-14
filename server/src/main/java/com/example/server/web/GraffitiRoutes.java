package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.GraffitiStore;
import com.example.server.util.IpResolver;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 訪客塗鴉牆。看和送出不用登入，刪除要站長。 */
public final class GraffitiRoutes {

    private final AuthService authService;
    private final GraffitiStore store;

    public GraffitiRoutes(AuthService authService, GraffitiStore store) {
        this.authService = authService;
        this.store = store;
    }

    public void register(Javalin app) {
        app.get("/api/graffiti", this::list);
        app.get("/api/graffiti/{file}", this::png);
        app.post("/api/graffiti", this::create);
        app.delete("/api/graffiti/{id}", this::delete);
    }

    private void list(Context ctx) {
        Long before = parseBefore(ctx.queryParam("before"));
        int limit = parseLimit(ctx.queryParam("limit"));
        List<GraffitiStore.Item> page = store.list(before, limit);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < page.size(); i++) {
            items.add(itemJson(page.get(i)));
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

    private void png(Context ctx) {
        String file = ctx.pathParam("file");
        if (file == null || !file.endsWith(".png")) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這幅塗鴉"));
            return;
        }
        String id = file.substring(0, file.length() - 4);
        byte[] png = store.readPng(id);
        if (png == null) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這幅塗鴉"));
            return;
        }
        ctx.contentType("image/png");
        ctx.header("Cache-Control", "public, max-age=86400");
        ctx.result(png);
    }

    private void create(Context ctx) {
        String declared = ctx.header("Content-Length");
        if (declared != null) {
            try {
                if (Long.parseLong(declared.trim()) > GraffitiStore.MAX_BYTES) {
                    ctx.status(413).json(Map.of("success", false, "message", GraffitiStore.SIZE_MESSAGE));
                    return;
                }
            } catch (NumberFormatException ignored) {
                // 沒有合法長度就等讀到內容再檢查
            }
        }
        String type = ctx.contentType() == null ? "" : ctx.contentType().toLowerCase();
        byte[] raw;
        String signature = null;
        try {
            if (type.startsWith("multipart/")) {
                UploadedFile file = ctx.uploadedFile("image");
                if (file == null) {
                    raw = null;
                } else {
                    try (InputStream in = file.content()) {
                        raw = readLimited(in);
                    }
                }
                signature = ctx.formParam("signature");
            } else {
                raw = readLimited(new ByteArrayInputStream(ctx.bodyAsBytes()));
            }
        } catch (IOException ex) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", GraffitiStore.IMAGE_MESSAGE));
            return;
        }
        if (raw == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", GraffitiStore.IMAGE_MESSAGE));
            return;
        }
        if (signature == null) {
            signature = ctx.header("X-Graffiti-Signature");
        }
        if (signature == null) {
            signature = ctx.queryParam("signature");
        }
        GraffitiStore.Result result = store.add(raw, signature, IpResolver.clientIp(ctx), System.currentTimeMillis());
        if (!result.ok) {
            ctx.status(result.status).json(Map.of("success", false, "message", result.message));
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("message", result.message);
        body.put("item", itemJson(result.item));
        ctx.status(HttpStatus.CREATED).json(body);
    }

    private void delete(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        String id = ctx.pathParam("id");
        if (!store.delete(id)) {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("success", false, "message", "找不到這幅塗鴉"));
            return;
        }
        ctx.json(Map.of("success", true, "message", "已從牆上拿下"));
    }

    private static Map<String, Object> itemJson(GraffitiStore.Item item) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", item.id);
        json.put("signature", item.signature);
        json.put("createdAt", item.createdAt);
        json.put("url", "/api/graffiti/" + item.id + ".png");
        return json;
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
            return 24;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return 24;
        }
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        byte[] data = in.readNBytes(GraffitiStore.MAX_BYTES + 1);
        if (data.length > GraffitiStore.MAX_BYTES) {
            return new byte[GraffitiStore.MAX_BYTES + 1];
        }
        return data;
    }
}
