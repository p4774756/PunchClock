package com.example.server.web;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** 登入後導回：僅允許站內白名單路徑，擋 open redirect（勿再 URL decode，Javalin 已解碼一次）。 */
public final class LoginNextPath {

    private static final Set<String> ALLOWED_BASE = Set.of("/", "/office", "/console", "/atlas/tcm-3d", "/atlas/tcm-3d/");

    private static final Pattern SAFE_PATH = Pattern.compile("^(/|/office|/console|/atlas/tcm-3d/?)([?#].*)?$");

    private static final Pattern SAFE_HASH = Pattern.compile("^#(?:devices|panel-devices|system|panel-system)$");

    private static final Pattern SAFE_QUERY = Pattern.compile(
            "^\\?(?:open=(?:roadmap|tasks))(?:&[a-zA-Z0-9_.%-]+=[a-zA-Z0-9_.%-]*)*$");

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
        if (path.indexOf('\\') >= 0 || path.contains("://")) {
            return null;
        }
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (ch < 0x20 || ch == 0x7f) {
                return null;
            }
        }
        if (!SAFE_PATH.matcher(path).matches()) {
            return null;
        }
        String base = path;
        String suffix = "";
        int q = path.indexOf('?');
        int h = path.indexOf('#');
        int cut = -1;
        if (q >= 0 && h >= 0) {
            cut = Math.min(q, h);
        } else if (q >= 0) {
            cut = q;
        } else if (h >= 0) {
            cut = h;
        }
        if (cut >= 0) {
            base = path.substring(0, cut);
            suffix = path.substring(cut);
        }
        if (base.isEmpty()) {
            base = "/";
        }
        if (!ALLOWED_BASE.contains(base)) {
            return null;
        }
        if (base.startsWith("/atlas/")) {
            if (!suffix.isEmpty()) {
                return null;
            }
            return path;
        }
        if (suffix.isEmpty()) {
            return path;
        }
        if (!suffix.startsWith("?") && !suffix.startsWith("#")) {
            return null;
        }
        int qPos = suffix.indexOf('?');
        int hPos = suffix.indexOf('#');
        String query = "";
        String hash = "";
        if (qPos >= 0) {
            if (hPos >= 0 && hPos > qPos) {
                query = suffix.substring(qPos, hPos);
                hash = suffix.substring(hPos);
            } else {
                query = suffix.substring(qPos);
            }
        } else if (hPos >= 0) {
            hash = suffix.substring(hPos);
        }
        if (!query.isEmpty()) {
            String qLower = query.toLowerCase(Locale.ROOT);
            if (!SAFE_QUERY.matcher(qLower).matches()) {
                return null;
            }
            if (!"/".equals(base) && !"/console".equals(base)) {
                return null;
            }
        }
        if (!hash.isEmpty() && !SAFE_HASH.matcher(hash).matches()) {
            return null;
        }
        if (!hash.isEmpty() && !"/console".equals(base)) {
            return null;
        }
        return path;
    }
}