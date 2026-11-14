package com.example.server.store;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 讀 GitHub issue 的分期清單。快取 10 分鐘；失敗時沿用上次成功的結果。
 * 建構時不連線，第一次查詢才打 GitHub，避免啟動被網路卡住。
 */
public final class RoadmapService {

    public static final long TTL_MS = 10L * 60L * 1000L;
    public static final String UNAVAILABLE = "暫時讀不到施工進度";
    public static final String DEFAULT_REPO = "p4774756/PunchClock";
    public static final int DEFAULT_ISSUE = 36;

    @FunctionalInterface
    public interface Fetcher {
        String fetch(String repo, int issue) throws IOException;
    }

    public static final class View {
        public final boolean stale;
        public final long fetchedAt;
        public final String source;
        public final Integer current;
        public final String done;
        public final List<RoadmapParser.Phase> phases;
        public final String message;

        View(boolean stale, long fetchedAt, String source, Integer current, String done,
             List<RoadmapParser.Phase> phases, String message) {
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
    private final int issue;
    private final String source;

    private RoadmapParser.Document cached;
    private long fetchedAt;

    public RoadmapService(Fetcher fetcher) {
        this(fetcher, repoProperty(), issueProperty());
    }

    public RoadmapService(Fetcher fetcher, String repo, int issue) {
        this.fetcher = fetcher;
        this.repo = repo == null || repo.isBlank() ? DEFAULT_REPO : repo.trim();
        this.issue = issue;
        this.source = "https://github.com/" + this.repo + "/issues/" + issue;
    }

    public static String repoProperty() {
        String repo = System.getProperty("punchclock.roadmap.repo", DEFAULT_REPO);
        return repo == null || repo.isBlank() ? DEFAULT_REPO : repo.trim();
    }

    public static int issueProperty() {
        String raw = System.getProperty("punchclock.roadmap.issue", Integer.toString(DEFAULT_ISSUE));
        try {
            return Integer.parseInt(raw.trim());
        } catch (RuntimeException ex) {
            return DEFAULT_ISSUE;
        }
    }

    public synchronized View get(long now) {
        if (cached != null && now - fetchedAt < TTL_MS) {
            return view(false, fetchedAt, cached, null);
        }
        try {
            String body = fetcher.fetch(repo, issue);
            RoadmapParser.Document parsed = RoadmapParser.parse(body);
            cached = parsed;
            fetchedAt = now;
            return view(false, now, parsed, null);
        } catch (RuntimeException | IOException ex) {
            if (cached != null) {
                return view(true, fetchedAt, cached, null);
            }
            return view(false, now, new RoadmapParser.Document(null, Collections.emptyList(), null), UNAVAILABLE);
        }
    }

    private View view(boolean stale, long at, RoadmapParser.Document document, String message) {
        return new View(stale, at, source, document.current, document.done, document.phases, message);
    }
}
