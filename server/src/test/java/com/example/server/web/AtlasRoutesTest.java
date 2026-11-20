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
import java.util.zip.GZIPOutputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
        Files.createDirectories(appDir.resolve("assets"));
        Files.writeString(appDir.resolve("index.html"), "<!doctype html><title>中醫人體</title>atlas-ok");
        Files.writeString(appDir.resolve("assets").resolve("index-a1b2c3d4.js"), "chunk");
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
        HttpResponse<String> response = send("GET", "/atlas/tcm-3d/", null, null, null);
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("atlas-ok"));
        assertTrue(response.headers().firstValue("etag").isPresent());
        assertTrue(response.headers().firstValue("last-modified").isPresent());
    }

    @Test
    public void htmlSupportsNotModified() throws Exception {
        HttpResponse<String> first = send("GET", "/atlas/tcm-3d/", null, null, null);
        String etag = first.headers().firstValue("etag").orElseThrow();
        HttpResponse<String> second = send("GET", "/atlas/tcm-3d/", null, etag, null);
        assertEquals(304, second.statusCode());
        assertEquals(etag, second.headers().firstValue("etag").orElse(""));
    }

    @Test
    public void fingerprintedAssetsGetPublicImmutableYear() throws Exception {
        HttpResponse<String> response = send("GET", "/atlas/tcm-3d/assets/index-a1b2c3d4.js", null, null, null);
        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("cache-control").orElse("").contains("public"));
        assertTrue(response.headers().firstValue("cache-control").orElse("").contains("31536000"));
        assertTrue(response.headers().firstValue("cache-control").orElse("").contains("immutable"));
        assertTrue(response.headers().firstValue("etag").isPresent());
        assertTrue(response.headers().firstValue("last-modified").isPresent());
        String etag = response.headers().firstValue("etag").orElseThrow();
        HttpResponse<String> cached = send("GET", "/atlas/tcm-3d/assets/index-a1b2c3d4.js", null, etag, null);
        assertEquals(304, cached.statusCode());
    }

    @Test
    public void versionedModelGetsYearImmutableAndContentLength() throws Exception {
        byte[] glb = "glb-versioned".getBytes(StandardCharsets.UTF_8);
        Path glbPath = root.resolve("tcm-3d").resolve("models").resolve("anime-assistant.glb");
        Files.write(glbPath, glb);
        HttpResponse<byte[]> response = sendBytes(
                "GET", "/atlas/tcm-3d/models/anime-assistant.glb?v=abc123", null, null, null);
        assertEquals(200, response.statusCode());
        assertEquals(Integer.toString(glb.length), response.headers().firstValue("content-length").orElse(""));
        String cache = response.headers().firstValue("cache-control").orElse("");
        assertTrue(cache.contains("public"));
        assertTrue(cache.contains("31536000"));
        assertTrue(cache.contains("immutable"));
        assertTrue(response.headers().firstValue("etag").isPresent());
        assertTrue(response.headers().firstValue("last-modified").isPresent());
    }

    @Test
    public void signedInUserCanReadThePageAndANestedFile() throws Exception {
        String cookie = adminCookie();
        HttpResponse<String> page = send("GET", "/atlas/tcm-3d/", cookie, null, null);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("atlas-ok"));
        assertTrue(page.headers().firstValue("content-type").orElse("").startsWith("text/html"));
        assertTrue(page.headers().firstValue("cache-control").orElse("").contains("must-revalidate"));

        HttpResponse<String> model = send("GET", "/atlas/tcm-3d/models/body.txt", cookie, null, null);
        assertEquals(200, model.statusCode());
        assertEquals("model-bytes", model.body());
        assertTrue(model.headers().firstValue("cache-control").orElse("").contains("max-age=300"));
    }

    @Test
    public void modelGlbGetsGzipEncodingAndDistinctEtag() throws Exception {
        byte[] glb = "glb-binary-stub".getBytes(StandardCharsets.UTF_8);
        Path glbPath = root.resolve("tcm-3d").resolve("models").resolve("anime-assistant.glb");
        Files.write(glbPath, glb);
        ByteArrayOutputStream gzBuffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(gzBuffer)) {
            gz.write(glb);
        }
        byte[] gzip = gzBuffer.toByteArray();
        Files.write(Path.of(glbPath.toString() + ".gz"), gzip);

        HttpResponse<byte[]> plain = sendBytes("GET", "/atlas/tcm-3d/models/anime-assistant.glb", null, null, null);
        assertEquals(200, plain.statusCode());
        assertEquals(glb.length, plain.body().length);
        String plainEtag = plain.headers().firstValue("etag").orElseThrow();
        assertFalse(plain.headers().firstValue("content-encoding").isPresent());

        HttpResponse<byte[]> zipped = sendBytes(
                "GET", "/atlas/tcm-3d/models/anime-assistant.glb", null, null, "gzip");
        assertEquals(200, zipped.statusCode());
        assertEquals(Integer.toString(gzip.length), zipped.headers().firstValue("content-length").orElse(""));
        assertEquals("gzip", zipped.headers().firstValue("content-encoding").orElse(""));
        assertTrue(zipped.headers().firstValue("vary").orElse("").toLowerCase().contains("accept-encoding"));
        assertEquals(gzip.length, zipped.body().length);
        String gzipEtag = zipped.headers().firstValue("etag").orElseThrow();
        assertNotEquals(plainEtag, gzipEtag);

        HttpResponse<byte[]> cached = sendBytes(
                "GET", "/atlas/tcm-3d/models/anime-assistant.glb", null, gzipEtag, "gzip");
        assertEquals(304, cached.statusCode());
    }

    @Test
    public void pathEscapeDoesNotLeaveTheAppDirectory() throws Exception {
        HttpResponse<String> response = send("GET", "/atlas/tcm-3d/../secret.txt", adminCookie(), null, null);
        assertFalse(response.body().contains("do-not-leak"));
    }

    @Test
    public void unknownAppIsNotFound() throws Exception {
        HttpResponse<String> response = send("GET", "/atlas/other/", adminCookie(), null, null);
        assertEquals(404, response.statusCode());
    }

    private HttpResponse<String> send(String method, String path, String cookie, String ifNoneMatch, String acceptEncoding)
            throws Exception {
        return http.send(buildRequest(method, path, cookie, ifNoneMatch, acceptEncoding), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<byte[]> sendBytes(String method, String path, String cookie, String ifNoneMatch, String acceptEncoding)
            throws Exception {
        return http.send(buildRequest(method, path, cookie, ifNoneMatch, acceptEncoding), HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpRequest buildRequest(String method, String path, String cookie, String ifNoneMatch, String acceptEncoding) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5));
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if (ifNoneMatch != null) {
            builder.header("If-None-Match", ifNoneMatch);
        }
        if (acceptEncoding != null) {
            builder.header("Accept-Encoding", acceptEncoding);
        }
        if ("GET".equals(method)) {
            builder.GET();
        }
        return builder.build();
    }

    private String adminCookie() {
        return AdminAuthCookie.header();
    }
}