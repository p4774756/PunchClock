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
            } else {
                downloadQuery.set(exchange.getRequestURI().getRawQuery());
                byte[] body = "saved".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
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
    public void sendPeerFile_rejectsDisallowedType() throws Exception {
        Path src = Files.createTempFile("peer-upload-", ".exe");
        Files.writeString(src, "MZ");
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean ok = new AtomicBoolean(true);
        service.sendPeerFile("worker-b", src, msg -> {}, success -> {
            ok.set(Boolean.TRUE.equals(success));
            done.countDown();
        });
        assertTrue(done.await(3, TimeUnit.SECONDS));
        assertTrue(!ok.get());
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
    }
}
