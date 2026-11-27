package com.example.server.auth;

import com.example.server.AdminTestServer;
import io.javalin.Javalin;
import org.junit.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthCookiesTest {

    @Test
    public void localHttpLoginCookieHasHttpOnlyAndLaxWithoutSecure() throws Exception {
        String password = "cookie-attrs-test";
        assertCookieHeaders(password, Map.of(), false);
    }

    @Test
    public void renderEnvLoginCookieIncludesSecure() throws Exception {
        String password = "cookie-secure-test";
        AuthService auth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", password)));
        Map<String, String> cookieEnv = Map.of("RENDER", "true");
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        try {
            app.post("/login", ctx -> {
                if (auth.checkAdminPassword(ctx.formParam("password"))) {
                    AuthCookies.setAuthCookie(ctx, auth.authCookieValue(), cookieEnv);
                    ctx.status(204);
                } else {
                    ctx.status(401);
                }
            });
            String base = "http://127.0.0.1:" + app.port();
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            String body = "password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(base + "/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String setCookie = authSetCookieHeader(response);
            assertTrue(setCookie.toLowerCase(Locale.ROOT).contains("httponly"));
            assertTrue(setCookie.toLowerCase(Locale.ROOT).contains("samesite=lax"));
            assertTrue(setCookie.toLowerCase(Locale.ROOT).contains("secure"));
        } finally {
            app.stop();
        }
    }

    @Test
    public void logoutClearsAuthWithMatchingAttributes() throws Exception {
        String password = "cookie-logout-test";
        AuthService auth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", password)));
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        try {
            AdminTestServer.registerLogin(app, auth);
            app.get("/logout", ctx -> {
                AuthCookies.clearAuthCookie(ctx, Map.of());
                ctx.status(204);
            });
            String base = "http://127.0.0.1:" + app.port();
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            String cookie = loginAndExtractCookie(http, base, password);
            HttpResponse<String> logout = http.send(
                    HttpRequest.newBuilder(URI.create(base + "/logout"))
                            .header("Cookie", cookie)
                            .GET()
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            assertTrue("logout should clear auth cookie", hasClearedAuthCookie(logout.headers()));
        } finally {
            app.stop();
        }
    }

    private static void assertCookieHeaders(String password, Map<String, String> env, boolean expectSecure)
            throws Exception {
        AuthService auth = new AuthService(AuthConfig.from(withAdminPassword(env, password)));
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        try {
            AdminTestServer.registerLogin(app, auth);
            String base = "http://127.0.0.1:" + app.port();
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            String body = "password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(base + "/login"))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String setCookie = authSetCookieHeader(response);
            assertTrue(setCookie.toLowerCase(Locale.ROOT).contains("httponly"));
            assertTrue(setCookie.toLowerCase(Locale.ROOT).contains("samesite=lax"));
            if (expectSecure) {
                assertTrue(setCookie.toLowerCase(Locale.ROOT).contains("secure"));
            } else {
                assertFalse(setCookie.toLowerCase(Locale.ROOT).contains("secure"));
            }
        } finally {
            app.stop();
        }
    }

    private static Map<String, String> withAdminPassword(Map<String, String> env, String password) {
        Map<String, String> merged = new HashMap<>(env);
        merged.put("ADMIN_PASSWORD", password);
        return merged;
    }

    private static String loginAndExtractCookie(HttpClient http, String base, String password) throws Exception {
        String body = "password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(base + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(5))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return authSetCookieHeader(response).split(";", 2)[0];
    }

    private static String authSetCookieHeader(HttpResponse<String> response) {
        for (String header : response.headers().allValues("Set-Cookie")) {
            if (header.startsWith(AuthCookies.NAME + "=")) {
                return header;
            }
        }
        throw new AssertionError("missing auth Set-Cookie");
    }

    private static boolean hasClearedAuthCookie(java.net.http.HttpHeaders headers) {
        for (String header : headers.allValues("Set-Cookie")) {
            if (!header.startsWith(AuthCookies.NAME + "=")) {
                continue;
            }
            String lower = header.toLowerCase(Locale.ROOT);
            boolean cleared = lower.contains("max-age=0") || lower.startsWith(AuthCookies.NAME + "=;");
            return cleared;
        }
        return false;
    }
}