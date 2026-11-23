package com.example.server.store;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 依序抓取 Google 新聞繁中焦點與科技主題 RSS；全失敗時再以自由時報、Yahoo 奇摩備援。
 * 建構時不連線。
 */
public final class GoogleNewsRssClient implements NewsService.Fetcher {

    static final String USER_AGENT = "PunchClockNews/1.0 (+https://github.com/p4774756/PunchClock)";
    static final int MAX_BODY_BYTES = 512 * 1024;

    static final String FOCUS_FEED =
            "https://news.google.com/rss?hl=zh-TW&gl=TW&ceid=TW:zh-Hant";
    static final String TECH_FEED =
            "https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=zh-TW&gl=TW&ceid=TW:zh-Hant";

    private static final String[] FALLBACK_FEEDS = {
            "https://news.ltn.com.tw/rss/all.xml",
            "https://tw.news.yahoo.com/rss/taiwan"
    };

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public NewsModels.Feed fetch() throws IOException {
        List<NewsModels.Item> merged = new ArrayList<>();
        IOException last = null;
        try {
            merged.addAll(parseUrl(FOCUS_FEED));
        } catch (IOException ex) {
            last = ex;
        }
        try {
            merged.addAll(parseUrl(TECH_FEED));
        } catch (IOException ex) {
            if (last == null) {
                last = ex;
            }
        }
        List<NewsModels.Item> primary = NewsRssParser.dedupeAndLimit(merged, NewsService.MAX_ITEMS);
        if (!primary.isEmpty()) {
            return new NewsModels.Feed(primary);
        }
        for (String url : FALLBACK_FEEDS) {
            try {
                List<NewsModels.Item> fb = NewsRssParser.dedupeAndLimit(parseUrl(url), NewsService.MAX_ITEMS);
                if (!fb.isEmpty()) {
                    return new NewsModels.Feed(fb);
                }
            } catch (IOException ex) {
                last = ex;
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IOException("news feeds empty");
    }

    List<NewsModels.Item> parseUrl(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", ex);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("http " + response.statusCode());
        }
        String body = readLimited(response.body());
        try {
            return NewsRssParser.parse(body);
        } catch (IllegalArgumentException ex) {
            throw new IOException("rss", ex);
        }
    }

    private static String readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int total = 0;
        int n;
        while ((n = in.read(chunk)) >= 0) {
            total += n;
            if (total > MAX_BODY_BYTES) {
                throw new IOException("rss too large");
            }
            buf.write(chunk, 0, n);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }
}