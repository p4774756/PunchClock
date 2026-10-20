package com.example.service;

import org.junit.Test;

import static org.junit.Assert.*;

public class RelayProxyServiceTest {

    @Test
    public void clampPortReturnsDefaultForInvalidPorts() {
        assertEquals(RelayProxyService.DEFAULT_PORT, RelayProxyService.clampPort(0));
        assertEquals(RelayProxyService.DEFAULT_PORT, RelayProxyService.clampPort(-1));
        assertEquals(RelayProxyService.DEFAULT_PORT, RelayProxyService.clampPort(100));
        assertEquals(RelayProxyService.DEFAULT_PORT, RelayProxyService.clampPort(1023));
        assertEquals(RelayProxyService.DEFAULT_PORT, RelayProxyService.clampPort(70000));
    }

    @Test
    public void describeFailureShowsClassWhenMessageIsNull() {
        assertEquals("ConnectException", RelayProxyService.describeFailure(new java.net.ConnectException()));
        assertEquals("IOException（boom） ← ConnectException",
                RelayProxyService.describeFailure(new java.io.IOException("boom", new java.net.ConnectException())));
    }

    @Test
    public void clampPortAcceptsValidPorts() {
        assertEquals(1024, RelayProxyService.clampPort(1024));
        assertEquals(8888, RelayProxyService.clampPort(8888));
        assertEquals(65535, RelayProxyService.clampPort(65535));
    }

    @Test
    public void serviceStartsAndStops() {
        RelayProxyService service = new RelayProxyService();
        assertFalse(service.isRunning());

        boolean started = service.start(9999, "http://localhost:3000", "token", null);
        assertTrue(started);
        assertTrue(service.isRunning());
        assertEquals(9999, service.getPort());
        assertEquals("http://localhost:3000", service.getTargetServerUrl());

        service.stop();
        assertFalse(service.isRunning());
    }

    @Test
    public void serviceDoesNotStartWithoutTargetUrl() {
        RelayProxyService service = new RelayProxyService();
        boolean started = service.start(8888, "", "token", null);
        assertFalse(started);
        assertFalse(service.isRunning());
    }

    @Test
    public void trustAllSslDefaultsOffAndCanToggle() {
        RelayProxyService service = new RelayProxyService();
        assertFalse(service.isTrustAllSsl());
        service.setTrustAllSsl(true);
        assertTrue(service.isTrustAllSsl());
        service.setTrustAllSsl(false);
        assertFalse(service.isTrustAllSsl());
    }

    /** 上游用 chunked（沒有 Content-Length）回應，例如心跳回應較大時；中繼不能因此失敗。 */
    @Test
    public void relaysChunkedResponseWithoutContentLength() throws Exception {
        byte[] payload = new byte[200 * 1024];
        java.util.Arrays.fill(payload, (byte) 'x');
        com.sun.net.httpserver.HttpServer upstream =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0); // 0 = chunked
            try (java.io.OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        });
        upstream.start();
        RelayProxyService service = new RelayProxyService();
        java.util.List<String> logs = new java.util.concurrent.CopyOnWriteArrayList<>();
        try {
            int relayPort = freePort();
            assertTrue(service.start(relayPort,
                    "http://127.0.0.1:" + upstream.getAddress().getPort(), "token", logs::add));
            java.net.http.HttpResponse<byte[]> response = java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://127.0.0.1:" + relayPort + "/api/heartbeat"))
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertEquals(payload.length, response.body().length);
            assertTrue(logs.toString(), logs.stream().noneMatch(line -> line.contains("轉發失敗")));
        } finally {
            service.stop();
            upstream.stop(0);
        }
    }

    /** 上游回 204 或 Content-Length: 0 時，中繼要把回應結束掉，不能讓呼叫端一直等。 */
    @Test
    public void relaysEmptyResponseAndFinishesIt() throws Exception {
        com.sun.net.httpserver.HttpServer upstream =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        upstream.start();
        RelayProxyService service = new RelayProxyService();
        try {
            int relayPort = freePort();
            assertTrue(service.start(relayPort,
                    "http://127.0.0.1:" + upstream.getAddress().getPort(), "token", null));
            java.net.http.HttpResponse<byte[]> response = java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://127.0.0.1:" + relayPort + "/x"))
                            .timeout(java.time.Duration.ofSeconds(5))
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(204, response.statusCode());
            assertEquals(0, response.body().length);
        } finally {
            service.stop();
            upstream.stop(0);
        }
    }

    private static int freePort() throws java.io.IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    public void serviceTrimsTrailingSlash() {
        RelayProxyService service = new RelayProxyService();
        service.start(9998, "http://localhost:3000/", "token", null);
        assertEquals("http://localhost:3000", service.getTargetServerUrl());
        service.stop();
    }
}
