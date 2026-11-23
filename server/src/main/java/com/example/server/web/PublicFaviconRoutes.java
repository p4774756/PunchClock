package com.example.server.web;

import io.javalin.Javalin;
import io.javalin.http.Context;

import java.io.IOException;
import java.io.InputStream;

/** 站點根路徑 favicon（/favicon.ico、/favicon.svg）。 */
public final class PublicFaviconRoutes {

    private PublicFaviconRoutes() {
    }

    public static void register(Javalin app) {
        app.get("/favicon.ico", ctx -> send(ctx, "/public/favicon.ico", "image/x-icon"));
        app.get("/favicon.svg", ctx -> send(ctx, "/public/favicon.svg", "image/svg+xml"));
    }

    private static void send(Context ctx, String path, String contentType) {
        try (InputStream in = PublicFaviconRoutes.class.getResourceAsStream(path)) {
            if (in == null) {
                ctx.status(404);
                return;
            }
            ctx.contentType(contentType);
            ctx.header("Cache-Control", "public, max-age=86400");
            ctx.result(in.readAllBytes());
        } catch (IOException e) {
            ctx.status(500);
        }
    }
}