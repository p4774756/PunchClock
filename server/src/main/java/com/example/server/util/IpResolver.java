package com.example.server.util;

import io.javalin.http.Context;

public final class IpResolver {

    private IpResolver() {
    }

    public static String clientIp(Context ctx) {
        String forwarded = ctx.header("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        String ip = ctx.ip();
        return ip == null ? "unknown" : ip;
    }
}
