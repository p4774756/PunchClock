package com.example.server.auth;

import com.example.server.AdminTestServer;
import com.example.server.store.AccountDataPaths;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class AuthServiceTest {

    private Path accountDir;

    private AuthService newAuth(String password) throws Exception {
        accountDir = Files.createTempDirectory("auth-svc-test");
        return new AuthService(AuthConfig.from(Map.of("ADMIN_PASSWORD", password)), accountDir);
    }

    @After
    public void tearDown() throws Exception {
        if (accountDir != null) {
            Files.walk(accountDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    @Test
    public void isAuthFromCookieHeader_matchesSessionCookie() throws Exception {
        AuthService auth = newAuth("unit-test-pw!!");
        String sessionId = auth.login("root", "unit-test-pw!!").orElseThrow().sessionId;
        String header = "foo=1; auth=" + sessionId + "; bar=2";
        assertTrue(auth.isAuthFromCookieHeader(header));
        assertFalse(auth.isAuthFromCookieHeader("auth=wrong"));
        assertFalse(auth.isAuthFromCookieHeader(null));
        assertFalse(auth.isAuthFromCookieHeader("auth=" + auth.authCookieValue()));
    }

    @Test
    public void loginPostSetsCookieMatchingIsAuth() throws Exception {
        String password = "login-flow-test!";
        AuthService auth = newAuth(password);
        System.setProperty(AccountDataPaths.DIR_PROPERTY, accountDir.toString());
        Javalin app = Javalin.create(config -> config.showJavalinBanner = false).start(0);
        try {
            AdminTestServer.registerLogin(app, auth);
            String base = "http://127.0.0.1:" + app.port();
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            String body = "username=root&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
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
            assertNotEquals(auth.authCookieValue(), cookie.substring("auth=".length()));
        } finally {
            app.stop();
            System.clearProperty(AccountDataPaths.DIR_PROPERTY);
        }
    }
}