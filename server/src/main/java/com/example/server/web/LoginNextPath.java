package com.example.server.web;

import java.util.Set;

/** 登入後導回：僅允許站內白名單路徑，擋 open redirect（勿再 URL decode，Javalin 已解碼一次）。 */
public final class LoginNextPath {

    private static final Set<String> ALLOWED = Set.of("/dev-status");

    private LoginNextPath() {
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String path = raw.trim();
        if (path.isEmpty()) {
            return null;
        }
        if (!ALLOWED.contains(path)) {
            return null;
        }
        return path;
    }
}