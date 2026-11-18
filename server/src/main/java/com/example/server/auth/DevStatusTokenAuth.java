package com.example.server.auth;

import io.javalin.http.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 開發筆電心跳 Bearer；僅讀環境變數 DEV_STATUS_TOKEN，無預設值。 */
public final class DevStatusTokenAuth {

    private final String configuredToken;

    public DevStatusTokenAuth() {
        this(System.getenv("DEV_STATUS_TOKEN"));
    }

    public DevStatusTokenAuth(String envValue) {
        configuredToken = envValue == null || envValue.isEmpty() ? null : envValue;
    }

    public boolean isConfigured() {
        return configuredToken != null;
    }

    public boolean authorize(Context ctx) {
        if (!isConfigured()) {
            return false;
        }
        String authHeader = ctx.header("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        String provided = authHeader.substring(7);
        return constantTimeEquals(configuredToken, provided);
    }

    static boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            MessageDigest.isEqual(a, a);
            return false;
        }
        return MessageDigest.isEqual(a, b);
    }
}