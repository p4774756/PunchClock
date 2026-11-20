package com.example.server.store;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 台灣新聞 RSS：Google News zh-TW，失敗改中央社。 */
public final class NewsFeedClient implements WorldService.NewsFetcher {

    public static final String GOOGLE_NEWS_RSS =
            "https://news.google.com/rss?hl=zh-TW&gl=TW&ceid=TW:zh-Hant";
    public static final String CNA_RSS = "https://feeds.feedburner.com/rsscna/allnews";

    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http;
    private final Supplier<String> primaryUrl;
    private final Supplier<String> fallbackUrl;

    public NewsFeedClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    NewsFeedClient(HttpClient http) {
        this(http, () -> GOOGLE_NEWS_RSS, () -> CNA_RSS);
    }

    NewsFeedClient(HttpClient http, Supplier<String> primaryUrl, Supplier<String> fallbackUrl) {
        this.http = http;
        this.primaryUrl = primaryUrl;
        this.fallbackUrl = fallbackUrl;
    }

    @Override
    public List<WorldService.NewsHeadline> fetch() throws IOException {
        byte[] body = fetchBytes(primaryUrl.get());
        if (body == null) {
            body = fetchBytes(fallbackUrl.get());
        }
        if (body == null) {
            throw new IOException("news rss");
        }
        List<String> titles = RssNewsParser.parseHeadlines(body, NewsFilterConstants.MAX_HEADLINES);
        List<WorldService.NewsHeadline> out = new ArrayList<>();
        for (String title : titles) {
            out.add(new WorldService.NewsHeadline(title));
        }
        return out;
    }

    private byte[] fetchBytes(String url) {
        if (url == null || url.isBlank() || !url.startsWith("https://")) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(READ_TIMEOUT)
                    .header("Accept", "application/rss+xml, application/xml, text/xml")
                    .header("User-Agent", "PunchClock")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    return null;
                }
                return readLimited(in, NewsFilterConstants.MAX_RESPONSE_BYTES);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    /**
     * 邊讀邊計數，超過上限立刻放棄這個來源（不先把整份回應配置成 byte[]）。
     * 超限回 null，讓呼叫端改試備援。
     */
    static byte[] readLimited(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] chunk = new byte[8 * 1024];
        int total = 0;
        while (true) {
            int read = in.read(chunk, 0, chunk.length);
            if (read < 0) {
                return buffer.toByteArray();
            }
            total += read;
            if (total > maxBytes) {
                return null;
            }
            buffer.write(chunk, 0, read);
        }
    }
}