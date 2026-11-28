package com.example.server.auth;

import com.example.server.AdminTestServer;
import com.example.server.util.IpResolver;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.junit.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AuthServiceTest {

    @Test
    public void isAuthFromCookieHeader_acceptsValidSessionAndRejectsInvalid() {
        AuthService auth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", "unit-test-pw")));
        String token = auth.createSessionToken();
        String header = "foo=1; auth=" + token + "; bar=2";
        assertTrue(auth.isAuthFromCookieHeader(header));
        assertFalse(auth.isAuthFromCookieHeader("auth=wrong"));
        assertFalse(auth.isAuthFromCookieHeader(null));
        auth.revokeSession(token);
        assertFalse(auth.isAuthFromCookieHeader(header));
    }

    @Test
    public void twoSuccessfulLoginsIssueDifferentCookies() throws Exception {
        String password = "login-flow-test";
        AuthService auth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", password)));
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        try {
            AdminTestServer.registerLogin(app, auth);
            String base = "http://127.0.0.1:" + app.port();
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            String cookie1 = postLoginCookie(http, base, password);
            String cookie2 = postLoginCookie(http, base, password);
            assertNotEquals(cookie1, cookie2);
            assertTrue(auth.isAuthFromCookieHeader(cookie1));
            assertTrue(auth.isAuthFromCookieHeader(cookie2));
            assertFalse(cookie1.contains(password));
            String tokenPart = cookie1.substring("auth=".length());
            assertFalse(password.equals(tokenPart));
        } finally {
            app.stop();
        }
    }

    @Test
    public void logoutRevokesSessionImmediately() throws Exception {
        String password = "logout-revoke-test";
        AuthService auth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", password)));
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        try {
            AdminTestServer.registerLogin(app, auth);
            app.get("/logout", ctx -> {
                auth.revokeSession(ctx.cookie("auth"));
                AuthCookies.clearAuthCookie(ctx, Map.of());
                ctx.status(204);
            });
            String base = "http://127.0.0.1:" + app.port();
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            String cookie = postLoginCookie(http, base, password);
            assertTrue(auth.isAuthFromCookieHeader(cookie));
            http.send(
                    HttpRequest.newBuilder(URI.create(base + "/logout"))
                            .header("Cookie", cookie)
                            .GET()
                            .timeout(Duration.ofSeconds(5))
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            assertFalse(auth.isAuthFromCookieHeader(cookie));
        } finally {
            app.stop();
        }
    }

    @Test
    public void newAuthServiceInvalidatesPriorSessions() {
        AuthService oldAuth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", "pw-a")));
        String token = oldAuth.createSessionToken();
        assertTrue(oldAuth.isAuthFromCookieHeader("auth=" + token));
        AuthService restarted = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", "pw-a")));
        assertFalse(restarted.isAuthFromCookieHeader("auth=" + token));
    }

    @Test
    public void checkAdminPasswordUsesBcryptVerification() {
        AuthService auth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", "right-password")));
        assertTrue(auth.checkAdminPassword("right-password"));
        assertFalse(auth.checkAdminPassword("wrong-password"));
        assertFalse(auth.checkAdminPassword(null));
    }

    @Test
    public void heartbeatRejectsWrongLengthOrContent() throws Exception {
        AuthService auth = new AuthService(AuthConfig.from(Map.of(
                "ADMIN_PASSWORD", "pw",
                "HEARTBEAT_SECRET", "heartbeat-token-value")));
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        try {
            app.post("/hb", ctx -> ctx.result(String.valueOf(auth.isHeartbeatAuthorized(ctx))));
            String base = "http://127.0.0.1:" + app.port();
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            assertEquals("true", postBearer(http, base, "heartbeat-token-value"));
            assertEquals("false", postBearer(http, base, "heartbeat-token-wrong"));
            assertEquals("false", postBearer(http, base, "short"));
        } finally {
            app.stop();
        }
    }

    @Test
    public void spoofedForwardedForAccumulatesUnderSameIpKey() throws Exception {
        String previous = System.getProperty("punchclock.trustProxy");
        System.clearProperty("punchclock.trustProxy");
        try {
            AuthService auth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", "lock-pw")));
            Javalin app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
            try {
                app.post("/login", ctx -> failedLogin(ctx, auth));
                String base = "http://127.0.0.1:" + app.port();
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                for (int i = 0; i < 5; i++) {
                    HttpResponse<String> res = postWrongLogin(http, base, "203.0.113." + (10 + i));
                    assertEquals(401, res.statusCode());
                }
                HttpResponse<String> locked = postWrongLogin(http, base, "203.0.113.99");
                assertEquals(403, locked.statusCode());
            } finally {
                app.stop();
            }
        } finally {
            if (previous == null) {
                System.clearProperty("punchclock.trustProxy");
            } else {
                System.setProperty("punchclock.trustProxy", previous);
            }
        }
    }

    @Test
    public void globalLoginCapBlocksAfterManyDistinctSources() {
        AuthService auth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", "global-pw")));
        for (int i = 0; i < 25; i++) {
            auth.recordLoginFailure("src-" + i);
        }
        assertTrue(auth.getLoginLockStatus("brand-new-ip").isLocked());
    }

    private static void failedLogin(Context ctx, AuthService auth) {
        String ip = IpResolver.clientIp(ctx);
        AuthService.LoginLockStatus before = auth.getLoginLockStatus(ip);
        if (before.isLocked()) {
            ctx.status(403);
            return;
        }
        if (!auth.checkAdminPassword(ctx.formParam("password"))) {
            auth.recordLoginFailure(ip);
            ctx.status(401);
        }
    }

    private static String postBearer(HttpClient http, String base, String token) throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(base + "/hb"))
                        .header("Authorization", "Bearer " + token)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(5))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.body();
    }

    private static HttpResponse<String> postWrongLogin(HttpClient http, String base, String spoofedForwardedFor)
            throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(base + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("X-Forwarded-For", spoofedForwardedFor)
                        .POST(HttpRequest.BodyPublishers.ofString("password=wrong"))
                        .timeout(Duration.ofSeconds(5))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String postLoginCookie(HttpClient http, String base, String password) throws Exception {
        String body = "password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(base + "/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(5))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        for (String header : response.headers().allValues("Set-Cookie")) {
            if (header.startsWith("auth=")) {
                return header.split(";", 2)[0];
            }
        }
        throw new AssertionError("missing auth cookie");
    }
}