package com.example.server;

import com.example.ClientUpdatePolicy;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ServerAppClientUpdateTest {

    private static final String TOKEN = "punchclock-dev-secret";
    private static final String RELEASE = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String OTHER = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    private Javalin app;
    private HttpClient http;
    private String base;
    private Path jar;
    private ClientRelease release;

    @Before
    public void setUp() throws Exception {
        jar = Files.createTempFile("punchclock-client", ".jar");
        writeJar(jar);
        release = ClientRelease.fromJar(jar);
        app = new ServerApp(release).start(0);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @After
    public void tearDown() throws Exception {
        if (app != null) {
            app.stop();
        }
        Files.deleteIfExists(jar);
    }

    @Test
    public void heartbeatOffersUpdateOnlyWhenDigestDiffers() throws Exception {
        JsonObject different = heartbeat("1.8.0", OTHER);
        assertTrue(different.has("update"));
        JsonObject update = different.getAsJsonObject("update");
        assertEquals("1.8.0", update.get("version").getAsString());
        assertEquals(RELEASE, update.get("sourceDigest").getAsString());
        assertEquals(release.sha256(), update.get("sha256").getAsString());
        assertEquals(release.size(), update.get("size").getAsLong());
        assertEquals(ClientUpdatePolicy.JAR_PATH, update.get("path").getAsString());

        JsonObject same = heartbeat("1.8.0", RELEASE);
        assertFalse(same.has("update"));

        JsonObject dev = heartbeat("dev", OTHER);
        assertFalse(dev.has("update"));
    }

    @Test
    public void jarDownloadRequiresHeartbeatTokenAndMatchesHash() throws Exception {
        HttpResponse<byte[]> denied = http.send(
                HttpRequest.newBuilder(URI.create(base + ClientUpdatePolicy.JAR_PATH))
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(401, denied.statusCode());

        HttpResponse<byte[]> ok = http.send(
                HttpRequest.newBuilder(URI.create(base + ClientUpdatePolicy.JAR_PATH))
                        .header("Authorization", "Bearer " + TOKEN)
                        .GET().timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, ok.statusCode());
        assertEquals(release.size(), ok.body().length);
        Path downloaded = Files.createTempFile("downloaded-client", ".jar");
        try {
            Files.write(downloaded, ok.body());
            assertTrue(ClientUpdatePolicy.sha256Matches(downloaded, release.sha256()));
        } finally {
            Files.deleteIfExists(downloaded);
        }
    }

    private JsonObject heartbeat(String version, String digest) throws Exception {
        String json = "{\"clientId\":\"worker-update\",\"status\":\"ONLINE\",\"tasks\":[],\"heartbeatSeq\":1,"
                + "\"appVersion\":\"" + version + "\",\"appSourceDigest\":\"" + digest + "\"}";
        HttpResponse<String> hb = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/heartbeat"))
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .timeout(Duration.ofSeconds(5)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, hb.statusCode());
        return JsonParser.parseString(hb.body()).getAsJsonObject();
    }

    private static void writeJar(Path path) throws Exception {
        Properties props = new Properties();
        props.setProperty("version", "1.8.0");
        props.setProperty("sourceDigest", RELEASE);
        try (OutputStream out = Files.newOutputStream(path);
             JarOutputStream jos = new JarOutputStream(out)) {
            jos.putNextEntry(new JarEntry("version.properties"));
            props.store(jos, "");
            jos.closeEntry();
        }
    }
}
