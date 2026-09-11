package com.example.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HeartbeatServicePeerFileTest {

    private HttpServer http;
    private HeartbeatService service;
    private final AtomicReference<byte[]> posted = new AtomicReference<>();
    private final AtomicReference<String> downloadQuery = new AtomicReference<>();
    private final AtomicReference<String> downloadAuth = new AtomicReference<>();
    private final AtomicReference<String> downloadClientHeader = new AtomicReference<>();
    private final AtomicReference<String> downloadMethod = new AtomicReference<>();

    @Before
    public void setUp() throws Exception {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/api/heartbeat", exchange -> {
            byte[] body = "{\"success\":true,\"actions\":[],\"peers\":[]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        http.createContext("/api/peer/file", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                posted.set(requestBody);
                byte[] body = ("{\"success\":true,\"fileId\":\"abc123\",\"filename\":\"notes.txt\","
                        + "\"size\":5,\"message\":\"ok\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                downloadMethod.set("DELETE");
                downloadAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                downloadQuery.set(exchange.getRequestURI().getRawQuery());
                byte[] body = "{\"success\":true,\"message\":\"已清除\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                String path = exchange.getRequestURI().getPath();
                downloadAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                downloadClientHeader.set(exchange.getRequestHeaders().getFirst("X-PunchClock-Client"));
                downloadQuery.set(exchange.getRequestURI().getRawQuery());
                if (!path.endsWith("/data")) {
                    exchange.getResponseHeaders().add("Location", path + "/data");
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    byte[] body = "saved".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            }
            exchange.close();
        });
        http.start();

        service = new HeartbeatService();
        service.setClientId("worker-a");
        service.setHeartbeatToken("punchclock-dev-secret");
        CountDownLatch online = new CountDownLatch(1);
        service.startHeartbeat("http://127.0.0.1:" + http.getAddress().getPort(),
                msg -> {}, ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        online.countDown();
                    }
                });
        assertTrue(online.await(8, TimeUnit.SECONDS));
    }

    @After
    public void tearDown() {
        if (service != null) {
            service.stopHeartbeat();
        }
        if (http != null) {
            http.stop(0);
        }
    }

    @Test
    public void sendPeerFile_postsMultipartWhenOnline() throws Exception {
        Path src = Files.createTempFile("peer-upload-", ".txt");
        Files.writeString(src, "hello");
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        service.sendPeerFile("worker-b", src, msg -> {}, success -> {
            ok.set(Boolean.TRUE.equals(success));
            done.countDown();
        });
        assertTrue(done.await(8, TimeUnit.SECONDS));
        assertTrue(ok.get());
        String postedBody = new String(posted.get(), StandardCharsets.UTF_8);
        assertTrue(postedBody.contains("worker-b"));
        assertTrue(postedBody.contains("hello"));
        assertTrue(postedBody.contains("filename"));
    }

    @Test
    public void sendPeerFile_acceptsAnyExtension() throws Exception {
        Path src = Files.createTempFile("peer-upload-", ".exe");
        Files.writeString(src, "MZ");
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        service.sendPeerFile("worker-b", src, msg -> {}, success -> {
            ok.set(Boolean.TRUE.equals(success));
            done.countDown();
        });
        assertTrue(done.await(8, TimeUnit.SECONDS));
        assertTrue(ok.get());
        String postedBody = new String(posted.get(), StandardCharsets.UTF_8);
        assertTrue(postedBody.contains("payload") || postedBody.contains("MZ") || postedBody.contains("filename"));
    }

    @Test
    public void sendPeerFile_packsDirectoryAsZip() throws Exception {
        Path dir = Files.createTempDirectory("peer-folder-");
        Files.writeString(dir.resolve("inside.txt"), "folder-body");
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        service.sendPeerFile("worker-b", dir, msg -> {}, success -> {
            ok.set(Boolean.TRUE.equals(success));
            done.countDown();
        });
        assertTrue(done.await(8, TimeUnit.SECONDS));
        assertTrue(ok.get());
        String postedBody = new String(posted.get(), StandardCharsets.ISO_8859_1);
        assertTrue(postedBody.contains("kind"));
        assertTrue(postedBody.contains("folder") || postedBody.contains("PK"));
    }

    @Test
    public void sendPeerFile_acceptsFileLargerThanFormerFiveMegLimit() throws Exception {
        Path src = Files.createTempFile("peer-upload-", ".txt");
        Files.write(src, new byte[6 * 1024 * 1024]);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        service.sendPeerFile("worker-b", src, msg -> {}, success -> {
            ok.set(Boolean.TRUE.equals(success));
            done.countDown();
        });
        assertTrue(done.await(15, TimeUnit.SECONDS));
        assertTrue(ok.get());
        assertTrue(posted.get() != null && posted.get().length > 6 * 1024 * 1024);
    }

    @Test
    public void downloadPeerFile_writesBytesForRecipient() throws Exception {
        Path dest = Files.createTempFile("peer-download-", ".txt");
        Files.deleteIfExists(dest);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        service.downloadPeerFile("abc123", dest, msg -> {}, success -> {
            ok.set(Boolean.TRUE.equals(success));
            done.countDown();
        });
        assertTrue(done.await(8, TimeUnit.SECONDS));
        assertTrue(ok.get());
        assertEquals("saved", Files.readString(dest));
        assertTrue(downloadQuery.get().contains("clientId=worker-a"));
        assertEquals("Bearer punchclock-dev-secret", downloadAuth.get());
    }

    @Test
    public void downloadPeerFile_chineseClientIdUsesAsciiHeader() throws Exception {
        service.setClientId("王小明");
        Path dest = Files.createTempFile("peer-download-zh-", ".txt");
        Files.deleteIfExists(dest);
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        service.downloadPeerFile("abc123", dest, msg -> {}, success -> {
            ok.set(Boolean.TRUE.equals(success));
            done.countDown();
        });
        assertTrue(done.await(8, TimeUnit.SECONDS));
        assertTrue(ok.get());
        assertTrue(downloadQuery.get().contains("clientId="));
        assertTrue(downloadQuery.get().contains("%E7%8E%8B") || downloadQuery.get().contains("王小明"));
        String header = downloadClientHeader.get();
        assertTrue(header != null && header.chars().allMatch(c -> c >= 0x20 && c <= 0x7e));
        assertEquals("saved", Files.readString(dest));
    }

    @Test
    public void deletePeerFile_sendsDeleteForOwner() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(false);
        service.deletePeerFile("abc123", msg -> {}, success -> {
            ok.set(Boolean.TRUE.equals(success));
            done.countDown();
        });
        assertTrue(done.await(8, TimeUnit.SECONDS));
        assertTrue(ok.get());
        assertEquals("DELETE", downloadMethod.get());
        assertTrue(downloadQuery.get().contains("clientId=worker-a"));
    }
}
