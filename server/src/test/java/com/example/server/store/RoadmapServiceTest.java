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
}
