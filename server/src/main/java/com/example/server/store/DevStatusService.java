package com.example.server.store;

import com.example.server.store.DevStatusModels.Document;
import com.example.server.store.DevStatusModels.NowBlock;
import com.example.server.store.DevStatusModels.Snapshot;
import com.example.server.store.DevStatusModels.Stats;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 公開開發進度。快取 10 分鐘；失敗沿用上次成功並標 stale。
 * 失敗後 2 分鐘內不再打 GitHub。建構時不連線。
 * 網路請求在鎖外執行，同時只允許一次刷新（single-flight）。
 */
public final class DevStatusService {

    public static final long TTL_MS = 10L * 60L * 1000L;
    public static final long FAILURE_BACKOFF_MS = 2L * 60L * 1000L;
    public static final String UNAVAILABLE = "暫時讀不到開發進度";
    public static final String DEFAULT_REPO = "p4774756/PunchClock";

    @FunctionalInterface
    public interface Fetcher {
        Snapshot fetch(String repo) throws IOException;
    }

    public static final class View {
        public final boolean stale;
        public final long fetchedAt;
        public final String source;
        public final Document document;
        public final String message;

        View(boolean stale, long fetchedAt, String source, Document document, String message) {
            this.stale = stale;
            this.fetchedAt = fetchedAt;
            this.source = source;
            this.document = document;
            this.message = message;
        }
    }

    private final Fetcher fetcher;
    private final String repo;
    private final String source;

    private Document cached;
    private long fetchedAt;
    private boolean failing;
    private long lastFailureAt;
    private CompletableFuture<View> inFlight;

    public DevStatusService(Fetcher fetcher) {
        this(fetcher, repoProperty());
    }

    public DevStatusService(Fetcher fetcher, String repo) {
        this.fetcher = fetcher;
        this.repo = repo == null || repo.isBlank() ? DEFAULT_REPO : repo.trim();
        this.source = "https://github.com/" + this.repo;
    }

    public static String repoProperty() {
        String repo = System.getProperty("punchclock.devstatus.repo", DEFAULT_REPO);
        return repo == null || repo.isBlank() ? DEFAULT_REPO : repo.trim();
    }

    public View get(long now) {
        CompletableFuture<View> pending;
        boolean leader;
        synchronized (this) {
            if (cached != null && now - fetchedAt < TTL_MS) {
                return view(false, fetchedAt, cached, null);
            }
            if (failing && now - lastFailureAt < FAILURE_BACKOFF_MS) {
                return failedView(lastFailureAt);
            }
            if (inFlight != null) {
                pending = inFlight;
                leader = false;
            } else {
                pending = new CompletableFuture<>();
                inFlight = pending;
                leader = true;
            }
        }
        if (!leader) {
            return await(pending);
        }
        try {
            View result = refresh(now);
            pending.complete(result);
            return result;
        } catch (RuntimeException ex) {
            View fallback;
            synchronized (this) {
                failing = true;
                lastFailureAt = now;
                fallback = failedView(now);
            }
            pending.complete(fallback);
            return fallback;
        } finally {
            synchronized (this) {
                if (inFlight == pending) {
                    inFlight = null;
                }
            }
        }
    }

    private View refresh(long now) {
        try {
            Snapshot snapshot = fetcher.fetch(repo);
            Document document = DevStatusMapper.map(snapshot, repo, now);
            synchronized (this) {
                cached = document;
                fetchedAt = now;
                failing = false;
                return view(false, now, document, null);
            }
        } catch (RuntimeException | IOException ex) {
            synchronized (this) {
                failing = true;
                lastFailureAt = now;
                return failedView(now);
            }
        }
    }

    private static View await(CompletableFuture<View> pending) {
        try {
            return pending.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("dev-status interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new CompletionException(cause);
        }
    }

    private View failedView(long at) {
        if (cached != null) {
            return view(true, fetchedAt, cached, null);
        }
        return view(false, at, emptyDocument(), UNAVAILABLE);
    }

    private View view(boolean stale, long at, Document document, String message) {
        return new View(stale, at, source, document, message);
    }

    static Document emptyDocument() {
        return new Document(
                new NowBlock(null, null, Collections.emptyList()),
                Collections.emptyList(),
                Collections.emptyList(),
                new Stats("本週（週一 00:00 起，台北）", 0, 0)
        );
    }
}
