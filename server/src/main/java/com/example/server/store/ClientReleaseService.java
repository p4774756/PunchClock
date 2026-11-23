package com.example.server.store;

import com.example.server.ClientRelease;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * GitHub 最新桌面端 Release。快取 10 分鐘；失敗沿用上次成功結果。
 * 失敗後 2 分鐘內不再打 GitHub。建構時不連線。
 */
public final class ClientReleaseService {

    public static final long TTL_MS = 10L * 60L * 1000L;
    public static final long FAILURE_BACKOFF_MS = 2L * 60L * 1000L;
    public static final String DEFAULT_REPO = "p4774756/PunchClock";

    @FunctionalInterface
    public interface Fetcher {
        ClientRelease fetch(String repo) throws IOException;
    }

    private final Fetcher fetcher;
    private final String repo;

    private ClientRelease cached = ClientRelease.NONE;
    private long fetchedAt;
    private boolean failing;
    private long lastFailureAt;
    private CompletableFuture<ClientRelease> inFlight;

    public ClientReleaseService(Fetcher fetcher) {
        this(fetcher, DEFAULT_REPO);
    }

    public ClientReleaseService(Fetcher fetcher, String repo) {
        this.fetcher = fetcher;
        this.repo = repo == null || repo.isBlank() ? DEFAULT_REPO : repo.trim();
    }

    public static ClientReleaseService fixed(ClientRelease release) {
        ClientRelease value = release == null ? ClientRelease.NONE : release;
        return new ClientReleaseService(ignored -> value);
    }

    public static ClientReleaseService live() {
        return new ClientReleaseService(new GithubClientReleaseClient());
    }

    public ClientRelease get(long now) {
        CompletableFuture<ClientRelease> pending;
        boolean leader;
        synchronized (this) {
            if (cached.isAvailable() && now - fetchedAt < TTL_MS) {
                return cached;
            }
            if (failing && now - lastFailureAt < FAILURE_BACKOFF_MS) {
                return cached;
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
            ClientRelease result = refresh(now);
            pending.complete(result);
            return result;
        } catch (RuntimeException ex) {
            ClientRelease fallback;
            synchronized (this) {
                failing = true;
                lastFailureAt = now;
                fallback = cached;
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

    private ClientRelease refresh(long now) {
        try {
            ClientRelease release = fetcher.fetch(repo);
            synchronized (this) {
                cached = release;
                fetchedAt = now;
                failing = false;
                return release;
            }
        } catch (RuntimeException | IOException ex) {
            synchronized (this) {
                failing = true;
                lastFailureAt = now;
                return cached;
            }
        }
    }

    private static ClientRelease await(CompletableFuture<ClientRelease> pending) {
        try {
            return pending.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("client-release interrupted", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new CompletionException(cause);
        }
    }
}