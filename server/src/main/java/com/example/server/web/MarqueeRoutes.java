package com.example.server.web;

import com.example.MarqueeNotice;
import com.example.server.auth.AuthService;
import com.example.server.store.MarqueeStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/** 後台設定桌面端跑馬燈。 */
public final class MarqueeRoutes {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final AuthService authService;
    private final MarqueeStore store;

    public MarqueeRoutes(AuthService authService, MarqueeStore store) {
        this.authService = authService;
        this.store = store;
    }

    public void register(Javalin app) {
        app.get("/api/marquee", this::get);
        app.put("/api/marquee", this::put);
    }

    private void get(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        ctx.json(payload(store.current(), true, "ok"));
    }

    private void put(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("success", false, "message", "未登入或權限不足"));
            return;
        }
        Map<String, Object> body;
        try {
            body = GSON.fromJson(ctx.body(), RouteSupport.MAP_TYPE);
        } catch (RuntimeException ex) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "內容格式不對"));
            return;
        }
        if (body == null) {
            body = Map.of();
        }
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("enabled")));
        String text = body.get("text") == null ? "" : String.valueOf(body.get("text"));
        MarqueeNotice saved = store.update(enabled, text);
        ctx.json(payload(saved, true, saved.enabled ? "跑馬燈已開啟" : "跑馬燈已關閉"));
    }

    private static Map<String, Object> payload(MarqueeNotice notice, boolean success, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put("message", message);
        body.put("enabled", notice.enabled);
        body.put("text", notice.text);
        body.put("maxChars", MarqueeNotice.MAX_CHARS);
        return body;
    }
}
