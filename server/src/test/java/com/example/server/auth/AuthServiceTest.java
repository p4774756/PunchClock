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
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthServiceTest {

    @Test
    public void isAuthFromCookieHeader_matchesAuthCookie() {
        AuthService auth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", "unit-test-pw")));
        String header = "foo=1; auth=" + auth.authCookieValue() + "; bar=2";
        assertTrue(auth.isAuthFromCookieHeader(header));
        assertFalse(auth.isAuthFromCookieHeader("auth=wrong"));
        assertFalse(auth.isAuthFromCookieHeader(null));
    }

    @Test
    public void loginPostSetsCookieMatchingIsAuth() throws Exception {
        String password = "login-flow-test";
        AuthService auth = new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", password)));
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
            String cookie = null;
            for (String header : response.headers().allValues("Set-Cookie")) {
                if (header.startsWith("auth=")) {
                    cookie = header.split(";", 2)[0];
                    break;
                }
            }
            assertTrue("login should set auth cookie", cookie != null);
            assertTrue(auth.isAuthFromCookieHeader(cookie));
            assertFalse(cookie.contains(password));
        } finally {
            app.stop();
        }
    }
}