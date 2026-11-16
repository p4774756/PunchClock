package com.example.pet;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 只綁 {@code 127.0.0.1} 的本機 hook 監聽。寵物開啟且 Cursor 反應開啟時啟動。
 * 請求很快回應；狀態變更交給呼叫端切回 EDT，不在 HTTP 執行緒做 Swing。
 */
public final class DesktopPetHookServer {

    public static final int DEFAULT_PORT = 17373;
    public static final int PORT_ATTEMPTS = 20;
    public static final String DEFAULT_HOOK_FILE_NAME = "milu-hook.json";

    public static final class HookEvent {
        public final String eventName;
        public final String conversationId;
        public final String status;

        public HookEvent(String eventName, String conversationId, String status) {
            this.eventName = eventName;
            this.conversationId = conversationId;
            this.status = status;
        }
    }

    private final Path hookFile;
    private final Consumer<HookEvent> onEvent;
    private HttpServer server;
    private ExecutorService executor;
    private String token = "";
    private int port = -1;
    private boolean running;

    public DesktopPetHookServer(Path hookFile, Consumer<HookEvent> onEvent) {
        this.hookFile = Objects.requireNonNull(hookFile, "hookFile");
        this.onEvent = Objects.requireNonNull(onEvent, "onEvent");
    }

    public static Path defaultHookFile() {
        return Path.of(System.getProperty("user.home", "."), ".punchclock", DEFAULT_HOOK_FILE_NAME);
    }

    public synchronized boolean start() {
        if (running) {
            return true;
        }
        token = UUID.randomUUID().toString().replace("-", "");
        IOException last = null;
        for (int i = 0; i < PORT_ATTEMPTS; i++) {
            int candidate = DEFAULT_PORT + i;
            try {
                bind(candidate);
                writeHookFile();
                running = true;
                return true;
            } catch (IOException ex) {
                last = ex;
                stopQuietly();
            }
        }
        clearHookFile();
        if (last != null) {
            System.err.println("[桌面寵物] Cursor hook 監聽無法啟動：" + last.getMessage());
        }
        return false;
    }

    public synchronized void stop() {
        stopQuietly();
        clearHookFile();
        running = false;
        port = -1;
        token = "";
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized int port() {
        return port;
    }

    public synchronized String token() {
        return token;
    }

    public synchronized String bindHost() {
        if (server == null) {
            return "";
        }
        InetSocketAddress address = server.getAddress();
        return address == null ? "" : address.getHostString();
    }

    Path hookFile() {
        return hookFile;
    }

    private void bind(int candidate) throws IOException {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", candidate);
        server = HttpServer.create(address, 0);
        executor = Executors.newFixedThreadPool(2, new DaemonFactory());
        server.setExecutor(executor);
        server.createContext("/hook", this::handle);
        server.createContext("/", this::handle);
        server.start();
        InetSocketAddress bound = server.getAddress();
        if (bound == null || !"127.0.0.1".equals(bound.getHostString())) {
            throw new IOException("hook server must bind 127.0.0.1");
        }
        port = bound.getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                write(exchange, 405, "{\"ok\":false}");
                return;
            }
            if (!authorized(exchange)) {
                write(exchange, 401, "{\"ok\":false}");
                return;
            }
            String body = readBody(exchange.getRequestBody(), 65536);
            String eventName = extractJsonString(body, "hook_event_name");
            String conversationId = extractJsonString(body, "conversation_id");
            if (conversationId.isEmpty()) {
                conversationId = extractJsonString(body, "session_id");
            }
            String status = extractJsonString(body, "status");
            try {
                onEvent.accept(new HookEvent(eventName, conversationId, status));
            } catch (RuntimeException ex) {
                // 不讓回呼例外卡住 Cursor；仍回 200。
            }
            write(exchange, 200, "{\"ok\":true}");
        } catch (RuntimeException ex) {
            write(exchange, 200, "{\"ok\":true}");
        }
    }

    private boolean authorized(HttpExchange exchange) {
        if (token == null || token.isEmpty()) {
            return true;
        }
        Headers headers = exchange.getRequestHeaders();
        String header = first(headers, "X-Milu-Token");
        if (token.equals(header)) {
            return true;
        }
        String query = exchange.getRequestURI() != null ? exchange.getRequestURI().getRawQuery() : null;
        return token.equals(queryParam(query, "token"));
    }

    private void writeHookFile() throws IOException {
        Path parent = hookFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String json = "{\n"
                + "  \"host\": \"127.0.0.1\",\n"
                + "  \"port\": " + port + ",\n"
                + "  \"token\": \"" + token + "\",\n"
                + "  \"url\": \"http://127.0.0.1:" + port + "/hook\"\n"
                + "}\n";
        Path tmp = hookFile.resolveSibling(hookFile.getFileName() + ".tmp");
        Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, hookFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(tmp, hookFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void clearHookFile() {
        try {
            Files.deleteIfExists(hookFile);
        } catch (IOException ex) {
            // 刪不掉時腳本仍會因連線失敗而安靜略過。
        }
    }

    private void stopQuietly() {
        if (server != null) {
            try {
                server.stop(0);
            } catch (RuntimeException ex) {
                // 結束時忽略。
            }
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
    }

    private static void write(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String readBody(InputStream in, int max) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[2048];
        int total = 0;
        int read;
        while ((read = in.read(chunk)) >= 0) {
            total += read;
            if (total > max) {
                break;
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toString(StandardCharsets.UTF_8.name());
    }

    static String extractJsonString(String json, String key) {
        if (json == null || key == null || key.isEmpty()) {
            return "";
        }
        String needle = "\"" + key + "\"";
        int at = json.indexOf(needle);
        if (at < 0) {
            return "";
        }
        int colon = json.indexOf(':', at + needle.length());
        if (colon < 0) {
            return "";
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return "";
        }
        i++;
        StringBuilder value = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (c == '\\' && i < json.length()) {
                char next = json.charAt(i++);
                if (next == 'n') {
                    value.append('\n');
                } else if (next == 't') {
                    value.append('\t');
                } else if (next == 'r') {
                    value.append('\r');
                } else {
                    value.append(next);
                }
                continue;
            }
            if (c == '"') {
                break;
            }
            value.append(c);
        }
        return value.toString().trim();
    }

    private static String first(Headers headers, String name) {
        if (headers == null) {
            return "";
        }
        java.util.List<String> values = headers.get(name);
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return "";
        }
        return values.get(0).trim();
    }

    private static String queryParam(String query, String name) {
        if (query == null || query.isEmpty() || name == null) {
            return "";
        }
        String[] parts = query.split("&");
        for (String part : parts) {
            int eq = part.indexOf('=');
            String key = eq < 0 ? part : part.substring(0, eq);
            if (!name.equals(key)) {
                continue;
            }
            if (eq < 0) {
                return "";
            }
            return urlDecode(part.substring(eq + 1));
        }
        return "";
    }

    private static String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception ex) {
            return value;
        }
    }

    private static final class DaemonFactory implements ThreadFactory {
        private final AtomicInteger n = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "milu-hook-" + n.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
