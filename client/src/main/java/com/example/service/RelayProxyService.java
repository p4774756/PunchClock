package com.example.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 本機 HTTP 中繼 Proxy：讓同事透過你的電腦連到雲端 Server。
 * <p>
 * 同事把他的 PunchClock Server 網址設成 http://你的IP:port，
 * 本服務會將請求轉發到實際的雲端 Server。
 * <p>
 * 範例：
 * <ul>
 *   <li>你的電腦啟動中繼，監聽 0.0.0.0:8888</li>
 *   <li>同事把 Server 設成 http://192.168.1.100:8888</li>
 *   <li>同事的心跳 POST /api/heartbeat → 你的電腦 → 實際 Server</li>
 * </ul>
 */
public class RelayProxyService {

    public static final int DEFAULT_PORT = 8888;
    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;

    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(10);
    private static final int THREAD_POOL_SIZE = 8;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private HttpServer server;
    private ExecutorService executor;
    private volatile HttpClient httpClient;
    private boolean trustAllSsl = false;

    private String targetServerUrl = "";
    private String heartbeatToken = "";
    private int port = DEFAULT_PORT;
    private Consumer<String> logger;

    public RelayProxyService() {
        this.httpClient = HeartbeatService.buildHttpClient(false);
    }

    /** 與心跳共用「信任所有 SSL」設定；公司網路做 SSL 攔截時轉發才不會 PKIX 失敗。 */
    public synchronized void setTrustAllSsl(boolean trustAllSsl) {
        if (this.trustAllSsl == trustAllSsl) {
            return;
        }
        this.trustAllSsl = trustAllSsl;
        this.httpClient = HeartbeatService.buildHttpClient(trustAllSsl);
    }

    public boolean isTrustAllSsl() {
        return trustAllSsl;
    }

    /** 套用 JVM Proxy 屬性後重建客戶端，讓後續轉發走新的 ProxySelector。 */
    public synchronized void refreshHttpClient() {
        this.httpClient = HeartbeatService.buildHttpClient(trustAllSsl);
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        return port;
    }

    public String getTargetServerUrl() {
        return targetServerUrl;
    }

    /**
     * 啟動中繼 Proxy。
     *
     * @param port            監聽埠（建議 8888）
     * @param targetServerUrl 實際雲端 Server 網址（如 https://xxx.onrender.com）
     * @param heartbeatToken  認證 Token（用於轉發請求）
     * @param logger          日誌輸出
     * @return 啟動成功返回 true
     */
    public synchronized boolean start(int port, String targetServerUrl, String heartbeatToken, Consumer<String> logger) {
        if (running.get()) {
            log("[中繼] 已在執行中，先停止再重啟");
            stop();
        }

        this.port = clampPort(port);
        this.targetServerUrl = formatUrl(targetServerUrl);
        this.heartbeatToken = heartbeatToken != null ? heartbeatToken.trim() : "";
        this.logger = logger;

        if (this.targetServerUrl.isEmpty()) {
            log("[中繼] 未設定目標 Server 網址");
            return false;
        }

        try {
            executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
            server = HttpServer.create(new InetSocketAddress("0.0.0.0", this.port), 0);
            server.setExecutor(executor);

            server.createContext("/", new RelayHandler());

            server.start();
            running.set(true);

            log("[中繼] 已啟動本機中繼 Proxy");
            log("[中繼] 監聽：0.0.0.0:" + this.port);
            log("[中繼] 目標：" + this.targetServerUrl + (trustAllSsl ? " [SSL 信任全部憑證]" : ""));
            log("[中繼] 同事請把 Server 網址設成 http://你的IP:" + this.port);

            return true;
        } catch (Exception ex) {
            log("[中繼] 啟動失敗：" + ex.getMessage());
            stop();
            return false;
        }
    }

    public synchronized void stop() {
        running.set(false);

        if (server != null) {
            try {
                server.stop(1);
            } catch (Exception ignored) {
            }
            server = null;
        }

        if (executor != null) {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (Exception ignored) {
            }
            executor = null;
        }

        log("[中繼] 已停止本機中繼 Proxy");
    }

    private void log(String message) {
        if (logger != null) {
            logger.accept(message);
        }
    }

    public static int clampPort(int port) {
        if (port < MIN_PORT) {
            return DEFAULT_PORT;
        }
        if (port > MAX_PORT) {
            return DEFAULT_PORT;
        }
        return port;
    }

    private static String formatUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private class RelayHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();

            String fullPath = path + (query != null && !query.isEmpty() ? "?" + query : "");
            String targetUrl = targetServerUrl + fullPath;

            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            log("[中繼] " + clientIp + " " + method + " " + fullPath);

            try {
                byte[] requestBody = new byte[0];
                if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                    try (InputStream is = exchange.getRequestBody()) {
                        requestBody = is.readAllBytes();
                    }
                }

                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(targetUrl))
                        .timeout(REQUEST_TIMEOUT);

                copyRequestHeaders(exchange, builder);

                switch (method.toUpperCase()) {
                    case "GET":
                        builder.GET();
                        break;
                    case "POST":
                        builder.POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
                        break;
                    case "PUT":
                        builder.PUT(HttpRequest.BodyPublishers.ofByteArray(requestBody));
                        break;
                    case "DELETE":
                        builder.DELETE();
                        break;
                    case "PATCH":
                        builder.method("PATCH", HttpRequest.BodyPublishers.ofByteArray(requestBody));
                        break;
                    default:
                        builder.method(method, HttpRequest.BodyPublishers.noBody());
                }

                HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

                copyResponseHeaders(response, exchange);
                byte[] responseBody = response.body();

                exchange.sendResponseHeaders(response.statusCode(), responseBody.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBody);
                }

                log("[中繼] " + clientIp + " ← " + response.statusCode() + " (" + responseBody.length + " bytes)");

            } catch (Exception ex) {
                log("[中繼] 轉發失敗：" + ex.getMessage());
                sendError(exchange, 502, "中繼失敗：" + ex.getMessage());
            }
        }

        private void copyRequestHeaders(HttpExchange exchange, HttpRequest.Builder builder) {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType != null && !contentType.isBlank()) {
                builder.header("Content-Type", contentType);
            }

            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            if (auth != null && !auth.isBlank()) {
                builder.header("Authorization", auth);
            } else if (!heartbeatToken.isEmpty()) {
                builder.header("Authorization", "Bearer " + heartbeatToken);
            }

            String accept = exchange.getRequestHeaders().getFirst("Accept");
            if (accept != null && !accept.isBlank()) {
                builder.header("Accept", accept);
            }

            String clientHeader = exchange.getRequestHeaders().getFirst("X-PunchClock-Client");
            if (clientHeader != null && !clientHeader.isBlank() && isAsciiHeaderValue(clientHeader)) {
                builder.header("X-PunchClock-Client", clientHeader);
            }

            String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            String existingForwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
            if (existingForwarded != null && !existingForwarded.isBlank()) {
                builder.header("X-Forwarded-For", existingForwarded + ", " + clientIp);
            } else {
                builder.header("X-Forwarded-For", clientIp);
            }
        }

        private void copyResponseHeaders(HttpResponse<byte[]> response, HttpExchange exchange) {
            for (var entry : response.headers().map().entrySet()) {
                String key = entry.getKey();
                if (key == null || key.startsWith(":") || key.equalsIgnoreCase("transfer-encoding")
                        || key.equalsIgnoreCase("content-length")) {
                    continue;
                }
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    for (String v : values) {
                        exchange.getResponseHeaders().add(key, v);
                    }
                }
            }
        }

        private void sendError(HttpExchange exchange, int code, String message) {
            try {
                String body = "{\"error\":\"" + escapeJson(message) + "\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(code, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (IOException ignored) {
            }
        }

        private String escapeJson(String s) {
            if (s == null) {
                return "";
            }
            return s.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private boolean isAsciiHeaderValue(String value) {
            if (value == null) {
                return false;
            }
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 0x20 || c > 0x7e) {
                    return false;
                }
            }
            return true;
        }
    }
}
