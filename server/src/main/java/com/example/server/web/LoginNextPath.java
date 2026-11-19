package com.example.server.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** 登入後導回：僅允許站內單一路徑，擋 open redirect。 */
public final class LoginNextPath {

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
        if (!isSafePath(path)) {
            return null;
        }
        try {
            String decoded = URLDecoder.decode(path, StandardCharsets.UTF_8);
            if (!decoded.equals(path) && !isSafePath(decoded)) {
                return null;
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isSafePath(String path) {
        if (!path.startsWith("/")) {
            return false;
        }
        if (path.startsWith("//")) {
            return false;
        }
        if (path.contains("\\")) {
            return false;
        }
        if (path.contains("://")) {
            return false;
        }
        for (int i = 1; i < path.length() - 1; i++) {
            if (path.charAt(i) == '/' && path.charAt(i + 1) == '/') {
                return false;
            }
        }
        return true;
    }
}