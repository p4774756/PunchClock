package com.example.server;

import com.example.server.store.AccountStore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdminCsrfTest {

    private Path accountDir;
    private AdminTestServer server;
    private HttpClient http;

    @Before
    public void setUp() throws Exception {
        accountDir = Files.createTempDirectory("admin-csrf");
        System.setProperty(com.example.server.store.AccountDataPaths.DIR_PROPERTY, accountDir.toString());
        server = AdminTestServer.builder()
                .adminPassword("CsrfAdmin9!")
                .accountDataDir(accountDir)
                .start();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.stop();
        }
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
    public void crossOriginDeleteClientForbidden() throws Exception {
        String cookie = server.loginCookie(http);
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/clients/x"))
                        .DELETE()
                        .header("Cookie", cookie)
                        .header("Origin", "http://evil.example")
                        .header("X-Csrf-Token", server.csrfForAuthCookie(cookie))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(403, res.statusCode());
        assertTrue(res.body().contains("來源驗證失敗"));
    }

    @Test
    public void missingCsrfForbidden() throws Exception {
        String cookie = server.loginCookie(http);
        HttpResponse<String> res = http.send(
                HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/clients/x"))
                        .DELETE()
                        .header("Cookie", cookie)
                        .header("Origin", server.originHeaderValue())
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(403, res.statusCode());
        assertTrue(res.body().contains("CSRF"));
    }

    @Test
    public void validOriginAndCsrfAllowsAccountCreate() throws Exception {
        String cookie = server.loginCookie(http);
        String body = "{\"username\":\"newbie\",\"role\":\"member\",\"password\":\"Newbie9!!\"}";
        HttpResponse<String> res = http.send(
                server.writeRequest(cookie, HttpRequest.newBuilder(URI.create(server.baseUrl() + "/api/accounts")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, res.statusCode());
        Map<String, Object> json = new Gson().fromJson(res.body(), new TypeToken<Map<String, Object>>() { }.getType());
        assertTrue(Boolean.TRUE.equals(json.get("success")));
        assertTrue(server.authService().accountStore().find("newbie").isPresent());
    }
}