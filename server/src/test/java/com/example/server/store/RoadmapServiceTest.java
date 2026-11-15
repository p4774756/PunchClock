package com.example.server.store;

import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RoadmapServiceTest {

    private static final String BODY = "## 分期\n"
            + "- [x] **Phase 3 群島地基**：地基\n"
            + "- [ ] **Phase 4 進屋系統**：門口\n";

    @Test
    public void cachesForTenMinutesThenRefetches() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RoadmapService service = new RoadmapService((repo, issue) -> {
            calls.incrementAndGet();
            assertEquals("p4774756/PunchClock", repo);
            assertEquals(36, issue);
            return BODY;
        }, "p4774756/PunchClock", 36);

        RoadmapService.View first = service.get(1_000L);
        assertFalse(first.stale);
        assertEquals(Integer.valueOf(4), first.current);
        assertEquals(1_000L, first.fetchedAt);
        assertEquals("https://github.com/p4774756/PunchClock/issues/36", first.source);
        assertNull(first.message);
        assertEquals(1, calls.get());

        RoadmapService.View cached = service.get(1_000L + RoadmapService.TTL_MS - 1);
        assertFalse(cached.stale);
        assertEquals(1_000L, cached.fetchedAt);
        assertEquals(1, calls.get());

        RoadmapService.View again = service.get(1_000L + RoadmapService.TTL_MS);
        assertFalse(again.stale);
        assertEquals(1_000L + RoadmapService.TTL_MS, again.fetchedAt);
        assertEquals(2, calls.get());
    }

    @Test
    public void failureKeepsLastSuccessAndMarksStale() {
        AtomicInteger calls = new AtomicInteger();
        RoadmapService service = new RoadmapService((repo, issue) -> {
            if (calls.incrementAndGet() == 1) {
                return BODY;
            }
            throw new IOException("rate limit");
        }, "p4774756/PunchClock", 36);

        assertEquals(Integer.valueOf(4), service.get(5_000L).current);
        RoadmapService.View stale = service.get(5_000L + RoadmapService.TTL_MS);
        assertTrue(stale.stale);
        assertEquals(Integer.valueOf(4), stale.current);
        assertEquals(5_000L, stale.fetchedAt);
        assertEquals(2, stale.phases.size());
        assertNull(stale.message);
    }

    @Test
    public void firstFailureReturnsEmptyListAndMessage() {
        RoadmapService service = new RoadmapService((repo, issue) -> {
            throw new IOException("down");
        }, "p4774756/PunchClock", 36);

        RoadmapService.View view = service.get(9L);
        assertFalse(view.stale);
        assertTrue(view.phases.isEmpty());
        assertNull(view.current);
        assertEquals(RoadmapService.UNAVAILABLE, view.message);
        assertEquals(9L, view.fetchedAt);
    }

    @Test
    public void failureBackoffDoesNotCallFetcherAgain() {
        AtomicInteger calls = new AtomicInteger();
        RoadmapService service = new RoadmapService((repo, issue) -> {
            calls.incrementAndGet();
            throw new IOException("rate limit");
        }, "p4774756/PunchClock", 36);

        RoadmapService.View first = service.get(1_000L);
        assertEquals(RoadmapService.UNAVAILABLE, first.message);
        assertEquals(1, calls.get());

        RoadmapService.View held = service.get(1_000L + RoadmapService.FAILURE_BACKOFF_MS - 1);
        assertEquals(RoadmapService.UNAVAILABLE, held.message);
        assertFalse(held.stale);
        assertTrue(held.phases.isEmpty());
        assertEquals(1, calls.get());

        service.get(1_000L + RoadmapService.FAILURE_BACKOFF_MS);
        assertEquals(2, calls.get());
    }

    @Test
    public void staleResultIsHeldThroughFailureBackoff() {
        AtomicInteger calls = new AtomicInteger();
        RoadmapService service = new RoadmapService((repo, issue) -> {
            if (calls.incrementAndGet() == 1) {
                return BODY;
            }
            throw new IOException("rate limit");
        }, "p4774756/PunchClock", 36);

        assertEquals(Integer.valueOf(4), service.get(5_000L).current);
        long failedAt = 5_000L + RoadmapService.TTL_MS;
        RoadmapService.View stale = service.get(failedAt);
        assertTrue(stale.stale);
        assertEquals(2, calls.get());

        RoadmapService.View held = service.get(failedAt + RoadmapService.FAILURE_BACKOFF_MS - 1);
        assertTrue(held.stale);
        assertEquals(Integer.valueOf(4), held.current);
        assertEquals(5_000L, held.fetchedAt);
        assertNull(held.message);
        assertEquals(2, calls.get());

        service.get(failedAt + RoadmapService.FAILURE_BACKOFF_MS);
        assertEquals(3, calls.get());
    }
}
