package com.example.server.web;

import com.example.server.auth.AdminAuthCookie;
import com.example.server.auth.AuthService;
import com.example.server.store.GraffitiStore;
import io.javalin.Javalin;
import io.javalin.config.SizeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GraffitiRoutesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Javalin app;
    private HttpClient http;
    private String base;

    @Before
    public void setUp() throws Exception {
        GraffitiStore store = new GraffitiStore(folder.getRoot().toPath());
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jetty.multipartConfig.maxFileSize(1, SizeUnit.MB);
            config.jetty.multipartConfig.maxTotalRequestSize(1, SizeUnit.MB);
            config.jetty.multipartConfig.maxInMemoryFileSize(1, SizeUnit.MB);
        }).start(0);
        new GraffitiRoutes(new AuthService(), store).register(app);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    public void guestsCanListAndPostButCannotDelete() throws Exception {
        HttpResponse<String> list = send("GET", "/api/graffiti", null, null, null);
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("\"items\""));

        HttpResponse<String> bad = send("POST", "/api/graffiti", "image/png", "nope".getBytes(StandardCharsets.UTF_8), null);
        assertEquals(400, bad.statusCode());
        assertTrue(bad.body().contains(GraffitiStore.IMAGE_MESSAGE));
        assertFalse(bad.body().contains("未登入"));

        byte[] image = png(512, 256);
        String signed = "/api/graffiti?signature=" + URLEncoder.encode("小明", StandardCharsets.UTF_8);
        HttpResponse<String> created = send("POST", signed, "image/png", image);
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("小明"));
        String id = idOf(created.body());

        HttpResponse<byte[]> file = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/graffiti/" + id + ".png"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, file.statusCode());
        assertTrue(file.headers().firstValue("content-type").orElse("").contains("image/png"));
        assertTrue(file.headers().firstValue("cache-control").orElse("").contains("max-age=86400"));
        assertEquals((byte) 0x89, file.body()[0]);

        HttpResponse<String> denied = send("DELETE", "/api/graffiti/" + id, null, null, null);
        assertEquals(401, denied.statusCode());
        assertTrue(denied.body().contains("未登入或權限不足"));

        HttpResponse<String> removed = send("DELETE", "/api/graffiti/" + id, null, null, "Cookie", AdminAuthCookie.header());
        assertEquals(200, removed.statusCode());
        assertTrue(removed.body().contains("已從牆上拿下"));

        HttpResponse<String> gone = send("GET", "/api/graffiti/" + id + ".png", null, null, null);
        assertEquals(404, gone.statusCode());
        HttpResponse<String> after = send("GET", "/api/graffiti", null, null, null);
        assertFalse(after.body().contains(id));
    }

    @Test
    public void forgedForwardedForStillSharesTheRateLimit() throws Exception {
        String previous = System.getProperty("punchclock.trustProxy");
        System.clearProperty("punchclock.trustProxy");
        try {
            byte[] image = png(512, 256);
            for (int i = 0; i < 3; i++) {
                HttpResponse<String> ok = send(
                        "POST", "/api/graffiti?signature=a", "image/png", image,
                        "X-Forwarded-For", "10.1.1." + i);
                assertEquals(201, ok.statusCode());
            }
            HttpResponse<String> blocked = send(
                    "POST", "/api/graffiti?signature=a", "image/png", image,
                    "X-Forwarded-For", "10.9.9.9");
            assertEquals(429, blocked.statusCode());
            assertTrue(blocked.body().contains(GraffitiStore.RATE_MESSAGE));
        } finally {
            if (previous == null) {
                System.clearProperty("punchclock.trustProxy");
            } else {
                System.setProperty("punchclock.trustProxy", previous);
            }
        }
    }

    @Test
    public void multipartSubmitStoresSignature() throws Exception {
        byte[] image = png(512, 256);
        String boundary = "----punchclock";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Disposition: form-data; name=\"signature\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write("阿明\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Disposition: form-data; name=\"image\"; filename=\"graffiti.png\"\r\n".getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(image);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpResponse<String> created = send(
                "POST",
                "/api/graffiti",
                "multipart/form-data; boundary=" + boundary,
                body.toByteArray(),
                null);
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("阿明"));
    }

    private HttpResponse<String> send(String method, String path, String contentType, byte[] body, String... header) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(5));
        if (header != null && header.length == 2 && header[0] != null) {
            builder.header(header[0], header[1]);
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else if ("DELETE".equals(method)) {
            builder.DELETE();
        } else {
            if (contentType != null) {
                builder.header("Content-Type", contentType);
            }
            builder.POST(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String idOf(String json) {
        int key = json.indexOf("\"id\":\"");
        assertTrue(json, key >= 0);
        int start = key + 6;
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
