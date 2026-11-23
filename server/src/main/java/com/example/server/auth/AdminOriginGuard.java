package com.example.server.auth;

import io.javalin.http.Context;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Cookie 寫入請求的同源／跨站檢查。 */
public final class AdminOriginGuard {

    private AdminOriginGuard() {
    }

    public static boolean allowBrowserWrite(Context ctx, AuthConfig config) {
        String fetchSite = ctx.header("Sec-Fetch-Site");
        if (fetchSite != null && !fetchSite.isEmpty()) {
            String site = fetchSite.toLowerCase(Locale.ROOT);
            if ("cross-site".equals(site)) {
                return false;
            }
        }
        Set<String> allowed = expectedOrigins(ctx, config);
        String origin = ctx.header("Origin");
        if (origin != null && !origin.isEmpty()) {
            return allowed.contains(normalizeOrigin(origin));
        }
        String referer = ctx.header("Referer");
        if (referer != null && !referer.isEmpty()) {
            try {
                URI uri = URI.create(referer.trim());
                if (uri.getScheme() == null || uri.getHost() == null) {
                    return false;
                }
                String refOrigin = uri.getScheme() + "://" + uri.getAuthority();
                return allowed.contains(normalizeOrigin(refOrigin));
            } catch (Exception ex) {
                return false;
            }
        }
        return false;
    }

    static Set<String> expectedOrigins(Context ctx, AuthConfig config) {
        Set<String> out = new LinkedHashSet<>();
        String base = config.publicBaseUrl();
        if (base != null && !base.isEmpty()) {
            out.add(normalizeOrigin(base));
        }
        for (String extra : config.allowedOrigins()) {
            if (extra != null && !extra.isEmpty()) {
                out.add(normalizeOrigin(extra));
            }
        }
        if (out.isEmpty() && ctx != null) {
            out.add(normalizeOrigin(ctx.scheme() + "://" + ctx.host()));
        }
        return out;
    }

    public static String normalizeOrigin(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        try {
            URI uri = URI.create(trimmed);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return trimmed.toLowerCase(Locale.ROOT);
            }
            int port = uri.getPort();
            if (port < 0) {
                return (uri.getScheme() + "://" + uri.getHost()).toLowerCase(Locale.ROOT);
            }
            return (uri.getScheme() + "://" + uri.getHost() + ":" + port).toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }

    static Set<String> parseAllowedOriginsList(String csv) {
        if (csv == null || csv.isEmpty()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}