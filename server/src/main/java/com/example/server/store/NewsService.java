package com.example.server.store;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 熱門頭條快取 15 分鐘；失敗沿用上次成功並標 stale。
 * 失敗後 2 分鐘內不再打上游。建構時不連線。
 */
public final class NewsService {

    public static final long TTL_MS = 15L * 60L * 1000L;
    public static final long FAILURE_BACKOFF_MS = 2L * 60L * 1000L;
    public static final int MAX_ITEMS = 15;
    public static final String UNAVAILABLE = "暫時讀不到新聞頭條";

    @FunctionalInterface
    public interface Fetcher {
        NewsModels.Feed fetch() throws IOException;
    }

    public static final class View {
        public final boolean stale;
        public final long fetchedAt;
        public final List<NewsModels.Item> items;
        public final String message;

        View(boolean stale, long fetchedAt, List<NewsModels.Item> items, String message) {
            this.stale = stale;
            this.fetchedAt = fetchedAt;
            this.items = items;
            this.message = message;
        }
    }

    private final Fetcher fetcher;
    private List<NewsModels.Item> cached = Collections.emptyList();
    private long fetchedAt;
    private boolean failing;
    private long lastFailureAt;

    public NewsService(Fetcher fetcher) {
        this.fetcher = fetcher;
    }

    public synchronized View get(long now) {
        if (!cached.isEmpty() && now - fetchedAt < TTL_MS) {
            return view(false, fetchedAt, cached, null);
        }
        if (failing && now - lastFailureAt < FAILURE_BACKOFF_MS) {
            return failedView(lastFailureAt);
        }
        try {
            NewsModels.Feed parsed = fetcher.fetch();
            List<NewsModels.Item> items = parsed.items;
            if (items.isEmpty()) {
                throw new IOException("empty feed");
            }
            cached = items;
            fetchedAt = now;
            failing = false;
            return view(false, now, cached, null);
        } catch (RuntimeException | IOException ex) {
            failing = true;
            lastFailureAt = now;
            return failedView(now);
        }
    }

    private View failedView(long at) {
        if (!cached.isEmpty()) {
            return view(true, fetchedAt, cached, null);
        }
        return view(false, at, Collections.emptyList(), UNAVAILABLE);
    }

    private View view(boolean stale, long at, List<NewsModels.Item> items, String message) {
        return new View(stale, at, items, message);
    }
}