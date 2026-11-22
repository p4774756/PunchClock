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

import java.lang.reflect.Type;
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
        app.get("/dev-status", this::devStatusRedirect);
    }

    private void devStatusRedirect(Context ctx) {
        ctx.status(HttpStatus.MOVED_PERMANENTLY);
        ctx.header("Location", "/");
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
                    "message", "machine id 無效"
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
            if (view.document.now.current != null) {
                for (Item item : view.document.now.current) {
                    if (item != null && item.issue == issue) {
                        return nullToEmpty(item.title);
                    }
                }
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
}