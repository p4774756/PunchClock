package com.example.server.auth;

import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;

import java.util.Map;

/** 管理員登入 {@code auth} cookie 的屬性（HttpOnly、SameSite、Secure）。 */
public final class AuthCookies {

    static final String NAME = "auth";
    private static final int MAX_AGE_SEC = 60 * 60 * 24;
    private static final String COOKIE_PATH = "/";

    private AuthCookies() {
    }

    public static void setAuthCookie(Context ctx, String value, Map<String, String> env) {
        ctx.cookie(buildCookie(value, MAX_AGE_SEC, secureForRequest(ctx, env)));
    }

    public static void clearAuthCookie(Context ctx, Map<String, String> env) {
        ctx.cookie(buildCookie("", 0, secureForRequest(ctx, env)));
    }

    static boolean secureForRequest(Context ctx, Map<String, String> env) {
        if (AuthConfig.isProduction(env)) {
            return true;
        }
        return "https".equalsIgnoreCase(ctx.scheme());
    }

    private static Cookie buildCookie(String value, int maxAge, boolean secure) {
        Cookie cookie = new Cookie(NAME, value, COOKIE_PATH, maxAge);
        cookie.setHttpOnly(true);
        cookie.setSameSite(SameSite.LAX);
        cookie.setSecure(secure);
        return cookie;
    }
}