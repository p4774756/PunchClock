package com.example.server.store;

import com.example.server.store.DevStatusModels.Document;
import com.example.server.store.DevStatusModels.IssueRow;
import com.example.server.store.DevStatusModels.NowBlock;
import com.example.server.store.DevStatusModels.Snapshot;
import com.example.server.store.DevStatusModels.Stats;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
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

    public interface Fetcher {
        Snapshot fetch(String repo) throws IOException;

        default IssueRow fetchIssue(String repo, int number) throws IOException {
            return null;
        }
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

    private static final long OFFICE_ISSUE_TTL_MS = 5L * 60L * 1000L;

    private Document cached;
    private Snapshot cachedSnapshot;
    private long fetchedAt;
    private boolean failing;
    private long lastFailureAt;
    private CompletableFuture<View> inFlight;
    private final Map<Integer, IssueRow> officeIssueCache = new ConcurrentHashMap<>();
    private final Map<Integer, Long> officeIssueFetchedAt = new ConcurrentHashMap<>();

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

    /** 觸發同步後回傳最新 GitHub 快照（供辦公室公開 API 口語查詢）。 */
    public Snapshot githubSnapshot(long now) {
        get(now);
        synchronized (this) {
            return cachedSnapshot;
        }
    }

    /**
     * 快照缺票時補抓單一 issue（辦公室進行中／排隊標題）；失敗或無資料回 null。
     */
    public IssueRow issueForOffice(long now, Snapshot github, int issue) {
        if (issue <= 0) {
            return null;
        }
        if (github != null && github.issues != null) {
            for (IssueRow row : github.issues) {
                if (row != null && row.number == issue) {
                    return row;
                }
            }
        }
        IssueRow cachedRow = officeIssueCache.get(issue);
        Long cachedAt = officeIssueFetchedAt.get(issue);
        if (cachedRow != null && cachedAt != null && now - cachedAt < OFFICE_ISSUE_TTL_MS) {
            return cachedRow;
        }
        try {
            IssueRow fetched = fetcher.fetchIssue(repo, issue);
            if (fetched != null) {
                officeIssueCache.put(issue, fetched);
                officeIssueFetchedAt.put(issue, now);
            }
            return fetched;
        } catch (IOException ex) {
            return cachedRow;
        }
    }

    public String repo() {
        return repo;
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
                cachedSnapshot = snapshot;
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
                new NowBlock(Collections.emptyList(), null, Collections.emptyList()),
                Collections.emptyList(),
                Collections.emptyList(),
                new Stats("本週（週一 00:00 起，台北）", 0, 0)
        );
    }
}
