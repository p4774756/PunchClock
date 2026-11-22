package com.example.server.store;

import java.io.IOException;
import java.util.Collections;

/**
 * 讀 GitHub Milestone 施工清單。快取 10 分鐘；失敗時沿用上次成功的結果。
 * 失敗後 2 分鐘內不再打 GitHub，避免限流時每個訪客都卡住一條請求。
 * 建構時不連線，第一次查詢才打 GitHub，避免啟動被網路卡住。
 */
public final class RoadmapService {

    public static final long TTL_MS = 10L * 60L * 1000L;
    public static final long FAILURE_BACKOFF_MS = 2L * 60L * 1000L;
    public static final String UNAVAILABLE = "暫時讀不到施工進度";
    public static final String DEFAULT_REPO = "p4774756/PunchClock";

    @FunctionalInterface
    public interface Fetcher {
        RoadmapModels.Document fetch(String repo) throws IOException;
    }

    public static final class View {
        public final boolean stale;
        public final long fetchedAt;
        public final String source;
        public final Integer current;
        public final String done;
        public final java.util.List<RoadmapModels.Phase> phases;
        public final String message;

        View(boolean stale, long fetchedAt, String source, Integer current, String done,
             java.util.List<RoadmapModels.Phase> phases, String message) {
            this.stale = stale;
            this.fetchedAt = fetchedAt;
            this.source = source;
            this.current = current;
            this.done = done;
            this.phases = phases;
            this.message = message;
        }
    }

    private final Fetcher fetcher;
    private final String repo;
    private final String source;

    private RoadmapModels.Document cached;
    private long fetchedAt;
    private boolean failing;
    private long lastFailureAt;

    public RoadmapService(Fetcher fetcher) {
        this(fetcher, repoProperty());
    }

    public RoadmapService(Fetcher fetcher, String repo) {
        this.fetcher = fetcher;
        this.repo = repo == null || repo.isBlank() ? DEFAULT_REPO : repo.trim();
        this.source = "https://github.com/" + this.repo + "/milestones";
    }

    public static String repoProperty() {
        String repo = System.getProperty("punchclock.roadmap.repo", DEFAULT_REPO);
        return repo == null || repo.isBlank() ? DEFAULT_REPO : repo.trim();
    }

    public synchronized View get(long now) {
        if (cached != null && now - fetchedAt < TTL_MS) {
            return view(false, fetchedAt, cached, null);
        }
        if (failing && now - lastFailureAt < FAILURE_BACKOFF_MS) {
            return failedView(lastFailureAt);
        }
        try {
            RoadmapModels.Document parsed = fetcher.fetch(repo);
            cached = parsed;
            fetchedAt = now;
            failing = false;
            return view(false, now, parsed, null);
        } catch (RuntimeException | IOException ex) {
            failing = true;
            lastFailureAt = now;
            return failedView(now);
        }
    }

    private View failedView(long at) {
        if (cached != null) {
            return view(true, fetchedAt, cached, null);
        }
        return view(false, at, new RoadmapModels.Document(null, Collections.emptyList(), null), UNAVAILABLE);
    }

    private View view(boolean stale, long at, RoadmapModels.Document document, String message) {
        return new View(stale, at, source, document.current, document.done, document.phases, message);
    }
}