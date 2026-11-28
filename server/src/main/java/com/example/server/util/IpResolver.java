package com.example.server.util;

import io.javalin.http.Context;

/** 登入登入鎖定、塗鴉限速等共用的客戶端 IP 解析。 */
public final class IpResolver {

    private IpResolver() {
    }

    /**
     * 優先 {@code CF-Connecting-IP}；僅在 {@code punchclock.trustProxy=true} 時採用
     * {@code X-Forwarded-For} 最右段（受信代理追加的一跳），避免請求端偽造第一段。
     */
    public static String clientIp(Context ctx) {
        String cf = ctx.header("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) {
            return cf.trim();
        }
        if (Boolean.getBoolean("punchclock.trustProxy")) {
            String forwarded = ctx.header("X-Forwarded-For");
            if (forwarded != null && !forwarded.isEmpty()) {
                String[] parts = forwarded.split(",");
                String last = parts[parts.length - 1].trim();
                if (!last.isEmpty()) {
                    return last;
                }
            }
        }
        String ip = ctx.ip();
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }
}