package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.MeridianQuestStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;

/** 經絡闖關進度（需登入）。 */
public final class MeridianQuestRoutes {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_BODY = 8 * 1024;
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private final AuthService authService;
    private final MeridianQuestStore store;

    public MeridianQuestRoutes(AuthService authService, MeridianQuestStore store) {
        this.authService = authService;
        this.store = store;
    }

    public void register(Javalin app) {
        app.get("/api/meridian-quest/progress", this::get);
        app.put("/api/meridian-quest/progress", this::put);
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
        String body = ctx.body();
        if (body == null || body.length() > MAX_BODY) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "內容過大或為空"));
            return;
        }
        Map<String, Object> incoming;
        try {
            incoming = GSON.fromJson(body, MAP_TYPE);
        } catch (RuntimeException ex) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("success", false, "message", "內容格式不對"));
            return;
        }
        if (incoming == null) {
            incoming = Map.of();
        }
        Map<String, Object> saved = store.mergeAndSave(incoming);
        ctx.json(payload(saved, true, "進度已更新"));
    }

    private static Map<String, Object> payload(Map<String, Object> progress, boolean success, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", success);
        body.put("message", message);
        body.put("progress", progress);
        return body;
    }
}