package com.example.server.store;

import org.junit.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.net.ssl.SSLSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NewsFeedClientTest {

    private static final String SMALL_RSS = "<?xml version=\"1.0\"?><rss><channel>"
            + "<item><title>社區貓咪咖啡廳開幕</title></item>"
            + "</channel></rss>";

    @Test
    public void oversizedBodyIsAbortedWhileReadingAndFallsBack() throws Exception {
        AtomicLong readFromHuge = new AtomicLong();
        InputStream huge = countingStream(NewsFilterConstants.MAX_RESPONSE_BYTES * 4L, readFromHuge);
        FakeHttpClient http = new FakeHttpClient();
        http.enqueue(200, huge);
        http.enqueue(200, new ByteArrayInputStream(SMALL_RSS.getBytes(StandardCharsets.UTF_8)));

        NewsFeedClient client = new NewsFeedClient(
                http,
                (Supplier<String>) () -> "https://primary.example/rss",
                (Supplier<String>) () -> "https://fallback.example/rss");

        List<WorldService.NewsHeadline> headlines = client.fetch();
        assertEquals(1, headlines.size());
        assertEquals("社區貓咪咖啡廳開幕", headlines.get(0).title);
        assertTrue("超限來源應在讀取途中中止：" + readFromHuge.get(),
                readFromHuge.get() <= NewsFilterConstants.MAX_RESPONSE_BYTES + 64 * 1024);
    }

    @Test
    public void readLimitedStopsAtCap() throws IOException {
        AtomicLong read = new AtomicLong();
        assertNull(NewsFeedClient.readLimited(countingStream(1024L, read), 512));
        assertTrue(read.get() <= 1024);

        byte[] ok = NewsFeedClient.readLimited(new ByteArrayInputStream(new byte[300]), 512);
        assertEquals(300, ok.length);

        byte[] exact = NewsFeedClient.readLimited(new ByteArrayInputStream(new byte[512]), 512);
        assertEquals(512, exact.length);
    }

    @Test
    public void nonHttpsUrlIsRejectedWithoutRequest() throws Exception {
        FakeHttpClient http = new FakeHttpClient();
        http.enqueue(200, new ByteArrayInputStream(SMALL_RSS.getBytes(StandardCharsets.UTF_8)));

        NewsFeedClient client = new NewsFeedClient(
                http,
                (Supplier<String>) () -> "http://insecure.example/rss",
                (Supplier<String>) () -> "https://fallback.example/rss");

        assertEquals(1, client.fetch().size());
        assertEquals(1, http.sent);
    }

    private static InputStream countingStream(long total, AtomicLong counter) {
        return new InputStream() {
            private long left = total;

            @Override
            public int read() {
                if (left <= 0) {
                    return -1;
                }
                left -= 1;
                counter.incrementAndGet();
                return 'a';
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (left <= 0) {
                    return -1;
                }
                int n = (int) Math.min(len, left);
                java.util.Arrays.fill(b, off, off + n, (byte) 'a');
                left -= n;
                counter.addAndGet(n);
                return n;
            }
        };
    }

    /** 測試用假 HttpClient：照佇列依序回傳 InputStream body。 */
    private static final class FakeHttpClient extends HttpClient {
        private final Deque<int[]> statuses = new ArrayDeque<>();
        private final Deque<InputStream> bodies = new ArrayDeque<>();
        private int sent;

        void enqueue(int status, InputStream body) {
            statuses.add(new int[] { status });
            bodies.add(body);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            sent += 1;
            int status = statuses.isEmpty() ? 404 : statuses.poll()[0];
            InputStream body = bodies.isEmpty() ? InputStream.nullInputStream() : bodies.poll();
            return (HttpResponse<T>) new StreamResponse(request, status, body);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return CompletableFuture.completedFuture(send(request, handler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> promiseHandler) {
            return sendAsync(request, handler);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    private static final class StreamResponse implements HttpResponse<InputStream> {
        private final HttpRequest request;
        private final int status;
        private final InputStream body;

        StreamResponse(HttpRequest request, int status, InputStream body) {
            this.request = request;
            this.status = status;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<InputStream>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (a, b) -> true);
        }

        @Override
        public InputStream body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
