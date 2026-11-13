package com.example.server.web;

import com.example.server.ServerApp;
import com.example.server.auth.AdminAuthCookie;
import io.javalin.Javalin;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AtlasRoutesTest {

    private Javalin app;
    private HttpClient http;
    private String base;
    private Path root;

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("punchclock-atlas");
        Path appDir = root.resolve("tcm-3d");
        Files.createDirectories(appDir.resolve("models"));
        Files.writeString(appDir.resolve("index.html"), "<!doctype html><title>中醫人體</title>atlas-ok");
        Files.writeString(appDir.resolve("models").resolve("body.txt"), "model-bytes");
        Files.writeString(root.resolve("secret.txt"), "do-not-leak");
        System.setProperty(AtlasRoutes.ROOT_PROPERTY, root.toString());
        app = new ServerApp().start(0);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @After
    public void tearDown() throws Exception {
        if (app != null) {
            app.stop();
        }
        System.clearProperty(AtlasRoutes.ROOT_PROPERTY);
        if (root != null) {
            Files.walk(root)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // 測試目錄清不掉不影響斷言
                        }
                    });
        }
    }

    @Test
    public void guestsCanOpenTheAtlas() throws Exception {
        HttpResponse<String> response = send("GET", "/atlas/tcm-3d/", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("atlas-ok"));
    }

    @Test
    public void signedInUserCanReadThePageAndANestedFile() throws Exception {
        String cookie = adminCookie();
        HttpResponse<String> page = send("GET", "/atlas/tcm-3d/", cookie);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("atlas-ok"));
        assertTrue(page.headers().firstValue("content-type").orElse("").startsWith("text/html"));
        assertTrue(page.headers().firstValue("cache-control").orElse("").contains("must-revalidate"));

        HttpResponse<String> model = send("GET", "/atlas/tcm-3d/models/body.txt", cookie);
        assertEquals(200, model.statusCode());
        assertEquals("model-bytes", model.body());
        assertTrue(model.headers().firstValue("cache-control").orElse("").contains("max-age=300"));
    }

    @Test
    public void modelBinGetsLongCache() throws Exception {
        Files.writeString(root.resolve("tcm-3d").resolve("models").resolve("body-0.bin"), "bin");
        HttpResponse<String> response = send("GET", "/atlas/tcm-3d/models/body-0.bin", null);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("cache-control").orElse("").contains("max-age=604800"));
    }

    @Test
    public void pathEscapeDoesNotLeaveTheAppDirectory() throws Exception {
        HttpResponse<String> response = send("GET", "/atlas/tcm-3d/../secret.txt", adminCookie());
        assertFalse(response.body().contains("do-not-leak"));
    }

    @Test
    public void unknownAppIsNotFound() throws Exception {
        HttpResponse<String> response = send("GET", "/atlas/other/", adminCookie());
        assertEquals(404, response.statusCode());
    }

    private HttpResponse<String> send(String method, String path, String cookie) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if ("GET".equals(method)) {
            builder.GET();
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String adminCookie() {
        return AdminAuthCookie.header();
    }
}
