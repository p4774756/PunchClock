package com.example.server.web;

import com.example.server.AdminTestServer;
import com.example.server.ClientRelease;
import com.example.server.auth.CsrfTokens;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpSecurityTest {

    private AdminTestServer server;
    private HttpClient http;
    private String base;
    private String adminCookie;
    private String csrf;

    @Before
    public void setUp() throws Exception {
        server = AdminTestServer.builder().clientRelease(ClientRelease.NONE).start();
        base = server.baseUrl();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        adminCookie = server.loginCookie(http);
        csrf = server.csrfHeaderFromCookie(adminCookie);
    }

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void pingGuestOmitsVersion() throws Exception {
        HttpResponse<String> ping = send("GET", "/ping", null, null, null);
        assertEquals(200, ping.statusCode());
        assertTrue(ping.body().contains("\"message\":\"pong\""));
        assertFalse(ping.body().contains("version"));
        assertFalse(ping.body().contains("buildTime"));
    }

    @Test
    public void pingAdminIncludesVersion() throws Exception {
        HttpResponse<String> ping = send("GET", "/ping", adminCookie, null, null);
        assertEquals(200, ping.statusCode());
        assertTrue(ping.body().contains("\"version\""));
        assertTrue(ping.body().contains("\"buildTime\""));
    }

    @Test
    public void securityHeadersOnLoginPage() throws Exception {
        HttpResponse<String> login = send("GET", "/login", null, null, null);
        assertEquals(200, login.statusCode());
        assertEquals("nosniff", header(login, "X-Content-Type-Options"));
        assertEquals("DENY", header(login, "X-Frame-Options"));
        assertEquals("strict-origin-when-cross-origin", header(login, "Referrer-Policy"));
        assertTrue(header(login, "Content-Security-Policy").contains("frame-ancestors 'none'"));
        assertTrue(header(login, "Content-Security-Policy").contains("fonts.googleapis.com"));
    }

    @Test
    public void guestOversizedBodyRejected() throws Exception {
        byte[] body = new byte[(int) HttpSecurity.UNAUTHENTICATED_MAX_BODY_BYTES + 1];
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/graffiti"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(413, response.statusCode());
        assertTrue(response.body().contains("過大"));
    }

    @Test
    public void bearerRelayStillAllowsLargerBegin() throws Exception {
        String json = "{\"senderId\":\"worker-a\",\"recipientId\":\"worker-b\",\"fileName\":\"big.bin\",\"chunks\":2}";
        byte[] body = new byte[(int) HttpSecurity.UNAUTHENTICATED_MAX_BODY_BYTES + 512];
        System.arraycopy(json.getBytes(StandardCharsets.UTF_8), 0, body, 0, json.length());
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/peer/relay"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer punchclock-dev-secret")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertTrue(response.statusCode() != 413);
    }

    @Test
    public void adminWriteRequiresCsrf() throws Exception {
        HttpResponse<String> denied = send("POST", "/api/clients/x/cancel-schedule", adminCookie, null, null);
        assertEquals(403, denied.statusCode());
        assertTrue(denied.body().contains("CSRF"));

        HttpResponse<String> ok = send("POST", "/api/clients/x/cancel-schedule", adminCookie, csrf, null);
        assertEquals(200, ok.statusCode());
    }

    @Test
    public void getLogoutDoesNotClearSession() throws Exception {
        HttpResponse<String> logout = send("GET", "/logout", adminCookie, null, null);
        assertEquals(200, logout.statusCode());
        assertTrue(logout.body().contains("請從站內頁面登出"));

        HttpResponse<String> status = send("GET", "/api/status", adminCookie, null, null);
        assertEquals(200, status.statusCode());
    }

    @Test
    public void postLogoutClearsSession() throws Exception {
        HttpResponse<String> logout = send("POST", "/logout", adminCookie, csrf, null);
        assertEquals(302, logout.statusCode());
        HttpResponse<String> status = send("GET", "/api/status", adminCookie, null, null);
        assertEquals(401, status.statusCode());
    }

    private HttpResponse<String> send(String method, String path, String cookie, String csrfToken, byte[] body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if (csrfToken != null) {
            builder.header(CsrfTokens.HEADER, csrfToken);
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else if ("POST".equals(method)) {
            if (body == null) {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                builder.POST(HttpRequest.BodyPublishers.ofByteArray(body));
            }
        } else {
            throw new IllegalArgumentException(method);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String header(HttpResponse<String> response, String name) {
        return response.headers().firstValue(name).orElse("");
    }
}