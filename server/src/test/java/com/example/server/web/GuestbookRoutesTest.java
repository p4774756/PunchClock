package com.example.server.web;

import com.example.server.AdminTestServer;
import com.example.server.store.GuestbookStore;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GuestbookRoutesTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Javalin app;
    private HttpClient http;
    private String base;
    private String adminCookie;
    private GuestbookStore store;

    @Before
    public void setUp() throws Exception {
        store = new GuestbookStore(folder.getRoot().toPath());
        AdminTestServer.IsolatedAuth auth = AdminTestServer.isolatedAuth();
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jetty.multipartConfig.maxFileSize(1, SizeUnit.MB);
            config.jetty.multipartConfig.maxTotalRequestSize(1, SizeUnit.MB);
            config.jetty.multipartConfig.maxInMemoryFileSize(1, SizeUnit.MB);
        }).start(0);
        AdminTestServer.registerLogin(app, auth.authService());
        new GuestbookRoutes(auth.authService(), store).register(app);
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
    public void guestsCanPostButCannotModerate() throws Exception {
        String json = "{\"nickname\":\"小明\",\"message\":\"<b>hi</b>\",\"emoji\":\"smile\"}";
        HttpResponse<String> created = send("POST", "/api/guestbook", "application/json", json.getBytes(StandardCharsets.UTF_8), null);
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains("<b>hi</b>"));

        HttpResponse<String> list = send("GET", "/api/guestbook?page=1", null, null, null);
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("小明"));

        String id = idOf(created.body());

        HttpResponse<String> deniedHide = send("POST", "/api/guestbook/" + id + "/hide", null, null, null);
        assertEquals(401, deniedHide.statusCode());

        HttpResponse<String> deniedDel = send("DELETE", "/api/guestbook/" + id, null, null, null);
        assertEquals(401, deniedDel.statusCode());

        HttpResponse<String> hidden = send("POST", "/api/guestbook/" + id + "/hide", null, null, "Cookie", adminCookie);
        assertEquals(200, hidden.statusCode());

        HttpResponse<String> guestList = send("GET", "/api/guestbook?page=1", null, null, null);
        assertFalse(guestList.body().contains(id));

        HttpResponse<String> adminList = send("GET", "/api/guestbook?page=1", null, null, "Cookie", adminCookie);
        assertTrue(adminList.body().contains("\"hidden\":true"));
    }

    @Test
    public void rejectsInvalidDoodle() throws Exception {
        String json = "{\"nickname\":\"a\",\"message\":\"b\",\"doodle\":\"not-image\"}";
        HttpResponse<String> bad = send("POST", "/api/guestbook", "application/json", json.getBytes(StandardCharsets.UTF_8), null);
        assertEquals(400, bad.statusCode());

        byte[] huge = png(GuestbookStore.DOODLE_WIDTH, GuestbookStore.DOODLE_HEIGHT);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"nickname\":\"c\",\"message\":\"d\",\"doodle\":\"data:image/png;base64,");
        sb.append(java.util.Base64.getEncoder().encodeToString(huge));
        sb.append("\"}");
        HttpResponse<String> ok = send("POST", "/api/guestbook", "application/json", sb.toString().getBytes(StandardCharsets.UTF_8), null);
        assertEquals(201, ok.statusCode());
        String id = idOf(ok.body());

        HttpResponse<byte[]> file = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/guestbook/" + id + ".png"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, file.statusCode());
    }

    @Test
    public void honeypotBlocksSpam() throws Exception {
        String json = "{\"nickname\":\"bot\",\"message\":\"spam\",\"website\":\"http://evil\"}";
        HttpResponse<String> blocked = send("POST", "/api/guestbook", "application/json", json.getBytes(StandardCharsets.UTF_8), null);
        assertEquals(400, blocked.statusCode());
    }

    @Test
    public void hiddenDoodleReturns404ForGuests() throws Exception {
        byte[] webp = webpFixture();
        String b64 = java.util.Base64.getEncoder().encodeToString(webp);
        String json = "{\"nickname\":\"藏圖\",\"message\":\"doodle\",\"doodle\":\"data:image/webp;base64," + b64 + "\"}";
        HttpResponse<String> created = send("POST", "/api/guestbook", "application/json", json.getBytes(StandardCharsets.UTF_8), null);
        assertEquals(201, created.statusCode());
        String id = idOf(created.body());

        HttpResponse<byte[]> visible = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/guestbook/" + id + ".webp"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, visible.statusCode());

        send("POST", "/api/guestbook/" + id + "/hide", null, null, "Cookie", adminCookie);

        HttpResponse<byte[]> guest = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/guestbook/" + id + ".webp"))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(404, guest.statusCode());

        HttpResponse<byte[]> admin = http.send(
                HttpRequest.newBuilder(URI.create(base + "/api/guestbook/" + id + ".webp"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Cookie", adminCookie)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, admin.statusCode());
    }

    @Test
    public void acceptsWebpDoodleAndRejectsBadWebp() throws Exception {
        String bad = "{\"nickname\":\"w2\",\"message\":\"nope\",\"doodle\":\"data:image/webp;base64,AAAA\"}";
        HttpResponse<String> rejected = send("POST", "/api/guestbook", "application/json", bad.getBytes(StandardCharsets.UTF_8), null);
        assertEquals(400, rejected.statusCode());

        byte[] ok = webpFixture();
        String b64 = java.util.Base64.getEncoder().encodeToString(ok);
        String json = "{\"nickname\":\"webp\",\"message\":\"ok\",\"doodle\":\"data:image/webp;base64," + b64 + "\"}";
        HttpResponse<String> created = send("POST", "/api/guestbook", "application/json", json.getBytes(StandardCharsets.UTF_8), null);
        assertEquals(201, created.statusCode());
        assertTrue(created.body().contains(".webp"));
    }

    @Test
    public void statsRequiresAuth() throws Exception {
        HttpResponse<String> denied = send("GET", "/api/guestbook/stats", null, null, null);
        assertEquals(401, denied.statusCode());

        HttpResponse<String> ok = send("GET", "/api/guestbook/stats", null, null, "Cookie", adminCookie);
        assertEquals(200, ok.statusCode());
        assertTrue(ok.body().contains("visible"));
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
        } else if ("POST".equals(method)) {
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

    private static byte[] webpFixture() throws Exception {
        try (InputStream in = GuestbookRoutesTest.class.getResourceAsStream("/guestbook/doodle-240x120.webp")) {
            assertTrue(in != null);
            return in.readAllBytes();
        }
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.GREEN);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}