package com.example.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ChunkedUploaderTest {

    private static final int CHUNK = 1000;
    private static final long[] FAST_RETRY = {10L};

    private HttpServer http;
    private String base;
    private final ByteArrayOutputStream stored = new ByteArrayOutputStream();
    private final AtomicInteger begins = new AtomicInteger();
    private final AtomicInteger puts = new AtomicInteger();
    private final AtomicInteger deletes = new AtomicInteger();
    private final AtomicLong lastOffset = new AtomicLong(-1);
    private final List<String> logs = new CopyOnWriteArrayList<>();
    private final List<String> statuses = new CopyOnWriteArrayList<>();
    /** 第幾次 PUT（從 1 起算）只收一半就回 503。 */
    private volatile int failPutNumber = -1;
    /** 第幾次 PUT 回 404（模擬伺服器重啟遺失進度）。 */
    private volatile int losePutNumber = -1;
    private volatile boolean beginNotFound;

    @Before
    public void setUp() throws Exception {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/api/peer/upload", this::handle);
        http.start();
        base = "http://127.0.0.1:" + http.getAddress().getPort();
    }

    @After
    public void tearDown() {
        if (http != null) {
            http.stop(0);
        }
    }

    private synchronized void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery();
        byte[] body = exchange.getRequestBody().readAllBytes();
        if ("POST".equals(method) && path.equals("/api/peer/upload")) {
            if (beginNotFound) {
                reply(exchange, 404, "{\"message\":\"Endpoint not found\"}");
                return;
            }
            int n = begins.incrementAndGet();
            stored.reset();
            reply(exchange, 200, "{\"success\":true,\"uploadId\":\"u" + n + "\",\"received\":0}");
            return;
        }
        String currentId = "u" + begins.get();
        if (!path.startsWith("/api/peer/upload/" + currentId)) {
            reply(exchange, 404, "{\"message\":\"上傳工作不存在或已過期\"}");
            return;
        }
        if ("PUT".equals(method)) {
            int n = puts.incrementAndGet();
            long offset = Long.parseLong(query.replaceAll(".*offset=(\\d+).*", "$1"));
            lastOffset.set(offset);
            if (n == losePutNumber) {
                begins.incrementAndGet();
                reply(exchange, 404, "{\"message\":\"上傳工作不存在或已過期\"}");
                return;
            }
            if (offset != stored.size()) {
                reply(exchange, 409, "{\"received\":" + stored.size() + "}");
                return;
            }
            if (n == failPutNumber) {
                stored.write(body, 0, body.length / 2);
                reply(exchange, 503, "{\"message\":\"proxy reset\"}");
                return;
            }
            stored.write(body);
            reply(exchange, 200, "{\"success\":true,\"received\":" + stored.size() + "}");
            return;
        }
        if ("GET".equals(method)) {
            reply(exchange, 200, "{\"success\":true,\"received\":" + stored.size() + "}");
            return;
        }
        if ("DELETE".equals(method)) {
            deletes.incrementAndGet();
            reply(exchange, 200, "{\"success\":true}");
            return;
        }
        if ("POST".equals(method) && path.endsWith("/complete")) {
            reply(exchange, 200, "{\"success\":true,\"message\":\"queued\",\"fileId\":\"f1\"}");
            return;
        }
        reply(exchange, 400, "{}");
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        byte[] out = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, out.length);
        exchange.getResponseBody().write(out);
        exchange.close();
    }

    private ChunkedUploader uploader() {
        HttpClient client = HttpClient.newHttpClient();
        return new ChunkedUploader(() -> client, base, "token", "worker-a", CHUNK, FAST_RETRY,
                logs::add, statuses::add);
    }

    private static Path sample(int size) throws IOException {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i * 31);
        }
        Path file = Files.createTempFile("chunked-", ".bin");
        Files.write(file, data);
        return file;
    }

    @Test
    public void uploadsAllChunksInOrder() throws Exception {
        Path file = sample(3500);
        AtomicLong lastProgress = new AtomicLong();
        ChunkedUploader.Result result = uploader().upload("worker-b", file, 3500, "a.bin", "file",
                (t, total) -> lastProgress.set(t), new TransferCancel());
        assertTrue(result.message, result.ok);
        assertEquals(4, puts.get());
        assertArrayEquals(Files.readAllBytes(file), stored.toByteArray());
        assertEquals(3500, lastProgress.get());
        assertEquals(List.of(
                "正在上傳「a.bin」第 1 / 4 段",
                "正在上傳「a.bin」第 2 / 4 段",
                "正在上傳「a.bin」第 3 / 4 段",
                "正在上傳「a.bin」第 4 / 4 段"), statuses);
    }

    @Test
    public void chunkLabel_countsByOffsetAndCapsAtTotal() {
        assertEquals("第 1 / 35 段", ChunkedUploader.chunkLabel(0, 280L * 1024 * 1024, 8 * 1024 * 1024));
        assertEquals("第 23 / 35 段",
                ChunkedUploader.chunkLabel(22L * 8 * 1024 * 1024 + 5, 280L * 1024 * 1024, 8 * 1024 * 1024));
        assertEquals("第 1 / 1 段", ChunkedUploader.chunkLabel(0, 10, 1000));
        assertEquals("第 4 / 4 段", ChunkedUploader.chunkLabel(3999, 3500, 1000));
    }

    @Test
    public void resumesFromServerOffsetAfterTransientFailure() throws Exception {
        failPutNumber = 2;
        Path file = sample(3500);
        ChunkedUploader.Result result = uploader().upload("worker-b", file, 3500, "a.bin", "file",
                null, new TransferCancel());
        assertTrue(result.message, result.ok);
        assertArrayEquals(Files.readAllBytes(file), stored.toByteArray());
        assertEquals(1, begins.get());
        assertTrue(logs.stream().anyMatch(line -> line.contains("[重試]") && line.contains("第 2 / 4 段中斷")));
        assertTrue(statuses.stream().anyMatch(s -> s.startsWith("第 2 / 4 段中斷")));
    }

    @Test
    public void restartsWhenServerLosesSession() throws Exception {
        losePutNumber = 3;
        Path file = sample(3500);
        ChunkedUploader.Result result = uploader().upload("worker-b", file, 3500, "a.bin", "file",
                null, new TransferCancel());
        assertTrue(result.message, result.ok);
        assertEquals(3, begins.get());
        assertArrayEquals(Files.readAllBytes(file), stored.toByteArray());
    }

    @Test
    public void reportsUnsupportedOnOldServer() throws Exception {
        beginNotFound = true;
        ChunkedUploader.Result result = uploader().upload("worker-b", sample(10), 10, "a.bin", "file",
                null, new TransferCancel());
        assertFalse(result.ok);
        assertTrue(result.unsupported);
        assertEquals(0, puts.get());
    }

    @Test
    public void cancelAbortsServerSession() throws Exception {
        Path file = sample(3500);
        TransferCancel cancel = new TransferCancel();
        try {
            uploader().upload("worker-b", file, 3500, "a.bin", "file",
                    (t, total) -> {
                        if (t >= CHUNK) {
                            cancel.cancel();
                        }
                    }, cancel);
            fail("expected cancel");
        } catch (TransferIo.CancelledException expected) {
            // ok
        }
        for (int i = 0; i < 50 && deletes.get() == 0; i++) {
            Thread.sleep(20);
        }
        assertEquals(1, deletes.get());
    }
}
