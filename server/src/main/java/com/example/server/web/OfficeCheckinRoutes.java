package com.example.server.web;

import com.example.server.auth.AuthService;
import com.example.server.store.ClientStore;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** 辦公室打卡鐘：訪客摘要與管理員詳情。 */
public final class OfficeCheckinRoutes {

    private final ClientStore clientStore;
    private final AuthService authService;

    public OfficeCheckinRoutes(ClientStore clientStore, AuthService authService) {
        this.clientStore = clientStore;
        this.authService = authService;
    }

    public void register(Javalin app) {
        app.get("/checkin", this::checkinPage);
        app.get("/api/office/checkin/public", this::getPublic);
        app.get("/api/office/checkin", this::getDetail);
    }

    private void checkinPage(Context ctx) {
        boolean authenticated = authService.isAuth(ctx);
        String html = readResource("/public/checkin.html")
                .replace("{{AUTH_BODY_CLASS}}", authenticated ? "is-authenticated" : "is-guest")
                .replace("{{AUTH_JS_BOOL}}", authenticated ? "true" : "false");
        ctx.header("Cache-Control", "no-cache, must-revalidate");
        ctx.html(html);
    }

    private void getPublic(Context ctx) {
        ctx.json(OfficeCheckinPublicPayload.build(clientStore));
    }

    private void getDetail(Context ctx) {
        if (!authService.isAuth(ctx)) {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of(
                    "success", false,
                    "message", "請先登入以查看完整打卡紀錄"
            ));
            return;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("clients", clientStore.publicClientsSnapshot());
        out.put("detail", true);
        ctx.json(out);
    }

    private static String readResource(String path) {
        try (InputStream in = OfficeCheckinRoutes.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resource: " + path, e);
        }
    }
}