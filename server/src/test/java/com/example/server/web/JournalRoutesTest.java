package com.example.server.web;

import com.example.server.AdminTestServer;
import com.example.server.store.JournalStore;
import com.google.gson.Gson;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JournalRoutesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final Gson GSON = new Gson();

    private Javalin app;
    private HttpClient http;
    private String base;
    private String adminCookie;

    @Before
    public void setUp() throws Exception {
        JournalStore store = new JournalStore(folder.getRoot().toPath());
        AdminTestServer.IsolatedAuth auth = AdminTestServer.isolatedAuth();
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jetty.multipartConfig.maxFileSize(2, SizeUnit.MB);
            config.jetty.multipartConfig.maxTotalRequestSize(2, SizeUnit.MB);
            config.jetty.multipartConfig.maxInMemoryFileSize(2, SizeUnit.MB);
        }).start(0);
        AdminTestServer.registerLogin(app, auth.authService());
        new JournalRoutes(auth.authService(), store).register(app);
        base = "http://127.0.0.1:" + app.port();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        adminCookie = AdminTestServer.loginCookie(base, auth.adminPassword(), http);
    }

    @After
    public void tearDown() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    public void guestsCanReadAndCommentButCannotPost() throws Exception {
        HttpResponse<String> list = send("GET", "/api/journal", null, null, (String[]) null);
        assertEquals(200, list.statusCode());

        HttpResponse<String> denied = send(
                "POST", "/api/journal", "application/json",
                "{\"body\":\"訪客\"}".getBytes(StandardCharsets.UTF_8),
                (String[]) null);
        assertEquals(401, denied.statusCode());
        assertTrue(denied.body().contains("未登入或權限不足"));

        HttpResponse<String> created = send(
                "POST", "/api/journal", "application/json",
                "{\"body\":\"站長文\"}".getBytes(StandardCharsets.UTF_8),
                "Cookie", adminCookie);
        assertEquals(201, created.statusCode());
        String id = idOf(created.body());

        HttpResponse<String> comment = send(
                "POST", "/api/journal/" + id + "/comments", "application/json",
                "{\"nickname\":\"小明\",\"body\":\"讚\"}".getBytes(StandardCharsets.UTF_8),
                (String[]) null);
        assertEquals(201, comment.statusCode());
        assertFalse(comment.body().contains("<script>"));

        HttpResponse<String> after = send("GET", "/api/journal", null, null, (String[]) null);
        assertTrue(after.body().contains("讚"));
    }

    @Test
    public void commentRateLimitReturns429() throws Exception {
        HttpResponse<String> created = send(
                "POST", "/api/journal", "application/json",
                "{\"body\":\"限流\"}".getBytes(StandardCharsets.UTF_8),
                "Cookie", adminCookie);
        String id = idOf(created.body());
        HttpResponse<String> ok = send(
                "POST", "/api/journal/" + id + "/comments", "application/json",
                "{\"body\":\"一\"}".getBytes(StandardCharsets.UTF_8),
                (String[]) null);
        assertEquals(201, ok.statusCode());
        HttpResponse<String> blocked = send(
                "POST", "/api/journal/" + id + "/comments", "application/json",
                "{\"body\":\"二\"}".getBytes(StandardCharsets.UTF_8),
                (String[]) null);
        assertEquals(429, blocked.statusCode());
    }

    @Test
    public void xssPayloadStoredAsPlainText() throws Exception {
        HttpResponse<String> created = send(
                "POST", "/api/journal", "application/json",
                "{\"body\":\"<img onerror=alert(1)>\"}".getBytes(StandardCharsets.UTF_8),
                "Cookie", adminCookie);
        assertEquals(201, created.statusCode());
        String id = idOf(created.body());
        HttpResponse<String> list = send("GET", "/api/journal", null, null, (String[]) null);
        assertTrue(list.body().contains("<img onerror=alert(1)>"));
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
        } else if ("PATCH".equals(method)) {
            if (contentType != null) {
                builder.header("Content-Type", contentType);
            }
            builder.method("PATCH", HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
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
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
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