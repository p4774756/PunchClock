package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.auth.DevStatusTokenAuth;
import com.example.server.store.DevMachineStore;
import com.example.server.store.DevStatusModels.Item;
import com.example.server.store.DevStatusService;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DevMachineRoutes {

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

    private final AuthService authService;
    private final DevStatusTokenAuth tokenAuth;
    private final DevMachineStore store;
    private final DevStatusService devStatusService;
    private final Gson gson;

    public DevMachineRoutes(AuthService authService, DevMachineStore store,
                            DevStatusService devStatusService, Gson gson) {
        this(authService, new DevStatusTokenAuth(), store, devStatusService, gson);
    }

    DevMachineRoutes(AuthService authService, DevStatusTokenAuth tokenAuth, DevMachineStore store,
                     DevStatusService devStatusService, Gson gson) {
        this.authService = authService;
        this.tokenAuth = tokenAuth;
        this.store = store;
        this.devStatusService = devStatusService;
        this.gson = gson;
    }

    public void register(Javalin app) {
        app.post("/api/dev-machines/heartbeat", this::heartbeat);
        app.get("/api/dev-machines", this::list);
        app.get("/dev-status", this::devStatusPage);
    }

    private void devStatusPage(Context ctx) {
        boolean authenticated = authService.isAuth(ctx);
        ctx.header("Cache-Control", "no-cache, must-revalidate");
        String html = com.example.server.util.DonatePageTokens.apply(readResource("/public/dev-status.html"))
                .replace("{{AUTH_JS_BOOL}}", authenticated ? "true" : "false");
        ctx.html(html);
    }

    private void heartbeat(Context ctx) {
        if (!tokenAuth.isConfigured()) {
            ctx.status(HttpStatus.SERVICE_UNAVAILABLE).json(Map.of(
                    "success", false,
                    "message", "開發機狀態回報未啟用"
            ));
            return;
        }
        if (!tokenAuth.authorize(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of(
                    "success", false,
                    "message", "Unauthorized"
            ));
            return;
        }
        byte[] bodyBytes = ctx.bodyAsBytes();
        if (bodyBytes.length > DevMachineStore.MAX_BODY_BYTES) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                    "success", false,
                    "message", "request body 過大"
            ));
            return;
        }
        Map<String, Object> body;
        try {
            body = gson.fromJson(ctx.body(), MAP_TYPE);
        } catch (JsonSyntaxException ex) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                    "success", false,
                    "message", "JSON 格式錯誤"
            ));
            return;
        }
        if (body == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                    "success", false,
                    "message", "缺少內容"
            ));
            return;
        }
        Object machineRaw = body.get("machine");
        if (machineRaw == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                    "success", false,
                    "message", "缺少 machine"
            ));
            return;
        }
        String machine = String.valueOf(machineRaw);
        DevMachineStore.ApplyResult result = store.applyHeartbeat(machine, body);
        if (result == DevMachineStore.ApplyResult.BAD_MACHINE) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                    "success", false,
                    "message", "machine 不在白名單"
            ));
            return;
        }
        if (result == DevMachineStore.ApplyResult.BAD_BODY) {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                    "success", false,
                    "message", "欄位格式錯誤"
            ));
            return;
        }
        ctx.json(Map.of("success", true));
    }

    private void list(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of(
                    "success", false,
                    "message", "請先登入"
            ));
            return;
        }
        long now = store.currentTimeMillis();
        DevStatusService.View view = devStatusService.get(now);
        DevMachineStore.IssueTitleResolver titles = issue -> issueTitle(view, issue);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("viewer", "admin");
        payload.put("machines", store.listForApi(now, titles));
        ctx.json(payload);
    }

    private static String issueTitle(DevStatusService.View view, int issue) {
        if (view == null || view.document == null) {
            return "";
        }
        String from = findInItems(view.document.items, issue);
        if (!from.isEmpty()) {
            return from;
        }
        if (view.document.now != null) {
            if (view.document.now.current != null && view.document.now.current.issue == issue) {
                return nullToEmpty(view.document.now.current.title);
            }
            if (view.document.now.next != null && view.document.now.next.issue == issue) {
                return nullToEmpty(view.document.now.next.title);
            }
            if (view.document.now.queue != null) {
                for (var entry : view.document.now.queue) {
                    Item item = entry.item;
                    if (item != null && item.issue == issue) {
                        return nullToEmpty(item.title);
                    }
                }
            }
        }
        return "";
    }

    private static String findInItems(List<Item> items, int issue) {
        if (items == null) {
            return "";
        }
        for (Item item : items) {
            if (item != null && item.issue == issue) {
                return nullToEmpty(item.title);
            }
        }
        return "";
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String readResource(String path) {
        try (InputStream in = com.example.server.ServerApp.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }
}