package com.example.service;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RelayTransferTest {

    private static final long[] FAST_RETRY = {10L, 10L, 10L};

    private HttpServer http;
    private String base;
    private final FakeRelay relay = new FakeRelay();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private Path tempDir;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("relay-test");
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        http.createContext("/api/peer/relay", relay::handle);
        http.start();
        base = "http://127.0.0.1:" + http.getAddress().getPort();
    }

    @After
    public void tearDown() throws Exception {
        if (http != null) {
            http.stop(0);
        }
        try (var walk = Files.walk(tempDir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void senderAndReceiverTransferThroughRelayWithRetry() throws Exception {
        byte[] payload = new byte[10_000];
        new Random(42).nextBytes(payload);
        Path source = tempDir.resolve("source.bin");
        Files.write(source, payload);
        relay.failNextTakeWith502.set(1);

        CompletableFuture<RelayTransfer.SendResult> sending = CompletableFuture.supplyAsync(() -> {
            try {
                return transfer("worker-a").send("worker-b", source, payload.length, "source.bin", "file",
                        null, new TransferCancel());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        relay.awaitBegun();

        Path dest = tempDir.resolve("out").resolve("received.bin");
        AtomicInteger lastProgress = new AtomicInteger();
        RelayTransfer.ReceiveResult received = transfer("worker-b").receive(relay.id, dest,
                (done, total) -> lastProgress.set((int) done), new TransferCancel());

        assertTrue(received.message, received.ok);
        assertArrayEquals(payload, Files.readAllBytes(dest));
        assertFalse(Files.exists(dest.resolveSibling("received.bin.part")));
        assertEquals(payload.length, lastProgress.get());
        RelayTransfer.SendResult sent = sending.get(10, TimeUnit.SECONDS);
        assertEquals(sent.message, RelayTransfer.SendStatus.DELIVERED, sent.status);
        assertTrue("window must cap buffered chunks", relay.maxBuffered <= 2);
    }

    @Test
    public void declinedRelayFailsSenderWithServerMessage() throws Exception {
        Path source = tempDir.resolve("a.txt");
        Files.writeString(source, "hello");
        CompletableFuture<RelayTransfer.SendResult> sending = CompletableFuture.supplyAsync(() -> {
            try {
                return transfer("worker-a").send("worker-b", source, 5, "a.txt", "file", null, new TransferCancel());
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        relay.awaitBegun();
        transfer("worker-b").cancelQuietly(relay.id);

        RelayTransfer.SendResult sent = sending.get(10, TimeUnit.SECONDS);
        assertEquals(RelayTransfer.SendStatus.FAILED, sent.status);
        assertEquals("對方拒絕接收", sent.message);
    }

    @Test
    public void beginReportsUnsupportedServerAndUnavailableRecipient() throws Exception {
        Path source = tempDir.resolve("a.txt");
        Files.writeString(source, "hello");

        relay.beginOverride = new Object[]{404, "{\"success\":false}"};
        assertEquals(RelayTransfer.SendStatus.UNSUPPORTED_SERVER, transfer("worker-a")
                .send("worker-b", source, 5, "a.txt", "file", null, new TransferCancel()).status);

        relay.beginOverride = new Object[]{409,
                "{\"success\":false,\"code\":\"RECIPIENT_OFFLINE\",\"message\":\"對方目前不在線\"}"};
        RelayTransfer.SendResult offline = transfer("worker-a")
                .send("worker-b", source, 5, "a.txt", "file", null, new TransferCancel());
        assertEquals(RelayTransfer.SendStatus.RECIPIENT_UNAVAILABLE, offline.status);
        assertEquals("對方目前不在線", offline.message);
    }

    @Test
    public void receiverCancelDeletesPartFileAndNotifiesServer() throws Exception {
        Path source = tempDir.resolve("a.bin");
        Files.write(source, new byte[4096]);
        relay.holdPushes = true;
        CompletableFuture.runAsync(() -> {
            try {
                transfer("worker-a").send("worker-b", source, 4096, "a.bin", "file", null, new TransferCancel());
            } catch (IOException ignored) {
                // sender ends when relay is cancelled
            }
        });
        relay.awaitBegun();

        TransferCancel cancel = new TransferCancel();
        Path dest = tempDir.resolve("cancel.bin");
        CompletableFuture<Void> receiving = CompletableFuture.runAsync(() -> {
            try {
                transfer("worker-b").receive(relay.id, dest, null, cancel);
            } catch (TransferIo.CancelledException expected) {
                // expected
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        relay.awaitState("ACCEPTED");
        cancel.cancel();
        receiving.get(10, TimeUnit.SECONDS);

        relay.awaitState("CANCELLED");
        assertFalse(Files.exists(dest));
        assertFalse(Files.exists(dest.resolveSibling("cancel.bin.part")));
    }

    private RelayTransfer transfer(String clientId) {
        return new RelayTransfer(() -> client, base, "punchclock-dev-secret", clientId,
                1024, FAST_RETRY, 300L, msg -> { }, msg -> { });
    }

    /** 最小化的伺服器直傳行為（與 server 的 RelayStore 協定一致），只供桌面端測試。 */
    private static final class FakeRelay {
        final Gson gson = new Gson();
        final AtomicInteger failNextTakeWith502 = new AtomicInteger();
        volatile Object[] beginOverride;
        volatile boolean holdPushes;
        volatile String id;
        int maxBuffered;
        String state = "";
        long size;
        long pushed;
        long acked;
        long version;
        final ArrayDeque<byte[]> chunks = new ArrayDeque<>();
        final ArrayDeque<Long> offsets = new ArrayDeque<>();

        void awaitBegun() throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5_000;
            while (id == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            assertTrue("sender never began relay", id != null);
        }

        void awaitState(String expected) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < deadline) {
                synchronized (this) {
                    if (expected.equals(state)) {
                        return;
                    }
                }
                Thread.sleep(10);
            }
            synchronized (this) {
                assertEquals(expected, state);
            }
        }

        void handle(HttpExchange ex) throws IOException {
            try {
                String path = ex.getRequestURI().getPath();
                Map<String, String> q = query(ex.getRequestURI());
                String method = ex.getRequestMethod();
                byte[] body = ex.getRequestBody().readAllBytes();
                if (path.equals("/api/peer/relay") && "POST".equals(method)) {
                    Object[] override = beginOverride;
                    if (override != null) {
                        reply(ex, (Integer) override[0], (String) override[1]);
                        return;
                    }
                    Map<?, ?> json = gson.fromJson(new String(body, StandardCharsets.UTF_8), Map.class);
                    synchronized (this) {
                        size = ((Number) json.get("size")).longValue();
                        state = "WAITING";
                        version = 1;
                        id = "relay1";
                    }
                    replyView(ex, 200, q);
                    return;
                }
                String rest = path.substring("/api/peer/relay/".length());
                if (rest.endsWith("/accept")) {
                    synchronized (this) {
                        if (!"WAITING".equals(state)) {
                            replyView(ex, 410, q);
                            return;
                        }
                        state = "ACCEPTED";
                        changed();
                    }
                    replyView(ex, 200, q);
                } else if (rest.endsWith("/complete")) {
                    synchronized (this) {
                        state = "COMPLETED";
                        chunks.clear();
                        offsets.clear();
                        changed();
                    }
                    replyView(ex, 200, q);
                } else if (rest.endsWith("/chunk") && "PUT".equals(method)) {
                    long offset = Long.parseLong(q.get("offset"));
                    synchronized (this) {
                        if (isTerminal()) {
                            replyView(ex, 410, q);
                            return;
                        }
                        if (offset != pushed || chunks.size() >= 2) {
                            replyView(ex, 409, q);
                            return;
                        }
                        chunks.addLast(body);
                        offsets.addLast(offset);
                        pushed += body.length;
                        maxBuffered = Math.max(maxBuffered, chunks.size());
                        changed();
                    }
                    replyView(ex, 200, q);
                } else if (rest.endsWith("/chunk")) {
                    takeChunk(ex, Long.parseLong(q.get("offset")), Long.parseLong(q.getOrDefault("wait", "0")), q);
                } else if ("DELETE".equals(method)) {
                    synchronized (this) {
                        if (!isTerminal()) {
                            boolean recipient = "worker-b".equals(q.get("clientId"));
                            state = recipient && "WAITING".equals(state) ? "DECLINED" : "CANCELLED";
                            chunks.clear();
                            offsets.clear();
                            changed();
                        }
                    }
                    replyView(ex, 200, q);
                } else {
                    long since = Long.parseLong(q.getOrDefault("since", "0"));
                    long wait = Long.parseLong(q.getOrDefault("wait", "0"));
                    synchronized (this) {
                        long deadline = System.currentTimeMillis() + wait;
                        while (version <= since && !isTerminal() && System.currentTimeMillis() < deadline) {
                            this.wait(Math.max(1, deadline - System.currentTimeMillis()));
                        }
                    }
                    replyView(ex, 200, q);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                ex.close();
            }
        }

        private void takeChunk(HttpExchange ex, long offset, long wait, Map<String, String> q)
                throws IOException, InterruptedException {
            if (failNextTakeWith502.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                reply(ex, 502, "{\"message\":\"bad gateway\"}");
                return;
            }
            byte[] data = null;
            synchronized (this) {
                long deadline = System.currentTimeMillis() + wait;
                while (true) {
                    if (isTerminal()) {
                        replyView(ex, 410, q);
                        return;
                    }
                    while (!offsets.isEmpty() && offsets.peekFirst() + chunks.peekFirst().length <= offset) {
                        offsets.pollFirst();
                        chunks.pollFirst();
                        acked = offset;
                        changed();
                    }
                    if (!holdPushes && !offsets.isEmpty() && offsets.peekFirst() == offset) {
                        data = chunks.peekFirst();
                        break;
                    }
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0) {
                        break;
                    }
                    this.wait(remaining);
                }
            }
            if (data == null) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            ex.getResponseHeaders().add("X-Relay-Offset", String.valueOf(offset));
            ex.sendResponseHeaders(200, data.length);
            ex.getResponseBody().write(Arrays.copyOf(data, data.length));
        }

        private boolean isTerminal() {
            return !"WAITING".equals(state) && !"ACCEPTED".equals(state);
        }

        private void changed() {
            version++;
            notifyAll();
        }

        private void replyView(HttpExchange ex, int status, Map<String, String> q) throws IOException {
            Map<String, Object> view = new LinkedHashMap<>();
            synchronized (this) {
                view.put("success", status == 200);
                view.put("relayId", id);
                view.put("state", state);
                view.put("size", size);
                view.put("pushed", pushed);
                view.put("acked", acked);
                view.put("version", version);
                if ("DECLINED".equals(state)) {
                    view.put("message", "對方拒絕接收");
                }
                if ("worker-a".equals(q.get("clientId"))) {
                    view.put("canPush", "ACCEPTED".equals(state) && pushed < size && chunks.size() < 2);
                }
            }
            reply(ex, status, gson.toJson(view));
        }

        private static void reply(HttpExchange ex, int status, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
        }

        private static Map<String, String> query(URI uri) {
            Map<String, String> map = new LinkedHashMap<>();
            String raw = uri.getRawQuery();
            if (raw == null) {
                return map;
            }
            for (String pair : raw.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    map.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
            return map;
        }
    }
}
