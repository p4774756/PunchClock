package com.example.server.web;

import com.example.server.ClientRelease;
import com.example.server.ServerApp;
import com.example.server.store.DevStatusModels;
import com.example.server.store.DevStatusService;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PublicDownloadRoutesTest {

    private static final String DIGEST =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private Javalin app;
    private HttpClient http;
    private String base;
    private Path jar;

    @Before
    public void setUp() throws Exception {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @After
    public void tearDown() throws Exception {
        if (app != null) {
            app.stop();
            app = null;
        }
        if (jar != null) {
            Files.deleteIfExists(jar);
            jar = null;
        }
    }

    @Test
    public void downloadPageShowsJarWhenReleaseAvailable() throws Exception {
        startWithRelease();
        HttpResponse<String> page = get("/download");
        assertEquals(200, page.statusCode());
        String body = page.body();
        assertTrue(body.contains("SKLbaby.jar"));
        assertTrue(body.contains("下載 SKLbaby.jar（v1.8.0）"));
        assertTrue(body.contains("Java 11"));
        assertFalse(body.contains("punchclock-client-standalone"));
        assertTrue(body.contains("data-download-missing hidden"));
        assertTrue(body.matches("(?s).*data-download-jar href=\"/download/app.jar\">.*"));

        HttpResponse<byte[]> jarResp = http.send(
                HttpRequest.newBuilder(URI.create(base + "/download/app.jar"))
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, jarResp.statusCode());
        String disposition = jarResp.headers().firstValue("content-disposition").orElse("");
        assertTrue(disposition.contains("filename=\"SKLbaby.jar\""));
        assertFalse(disposition.contains("secret"));
    }

    @Test
    public void downloadPageShowsMissingWhenNoRelease() throws Exception {
        startWithoutRelease();
        HttpResponse<String> page = get("/download");
        assertEquals(200, page.statusCode());
        String body = page.body();
        assertTrue(body.contains("尚未附帶"));
        assertTrue(body.contains("data-download-jar href=\"/download/app.jar\" hidden"));
        assertTrue(body.contains("data-download-missing>"));
        assertFalse(body.contains("data-download-missing hidden"));
        assertTrue(body.contains("java -jar SKLbaby.jar"));
        assertTrue(body.contains("下載 SKLbaby.jar（v）"));

        HttpResponse<String> jarResp = get("/download/app.jar");
        assertEquals(404, jarResp.statusCode());
    }

    private void startWithRelease() throws Exception {
        jar = Files.createTempFile("public-download", ".jar");
        writeJar(jar);
        ClientRelease release = ClientRelease.fromJar(jar);
        DevStatusService devStatus = emptyDevStatus();
        app = new ServerApp(release, devStatus).start(0);
        base = "http://127.0.0.1:" + app.port();
    }

    private void startWithoutRelease() {
        DevStatusService devStatus = emptyDevStatus();
        app = new ServerApp(ClientRelease.NONE, devStatus).start(0);
        base = "http://127.0.0.1:" + app.port();
    }

    private static DevStatusService emptyDevStatus() {
        return new DevStatusService(repo -> new DevStatusModels.Snapshot(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptySet()
        ), "p4774756/PunchClock");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create(base + path))
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void writeJar(Path path) throws Exception {
        Properties props = new Properties();
        props.setProperty("version", "1.8.0");
        props.setProperty("sourceDigest", DIGEST);
        props.setProperty("buildTime", Instant.parse("2026-10-20T07:58:00Z").toString());
        try (OutputStream out = Files.newOutputStream(path);
             JarOutputStream jos = new JarOutputStream(out)) {
            jos.putNextEntry(new JarEntry("version.properties"));
            props.store(jos, "");
            jos.closeEntry();
            jos.putNextEntry(new JarEntry("release-notes.txt"));
            jos.write("note\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
    }
}