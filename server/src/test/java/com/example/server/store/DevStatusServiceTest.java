package com.example.server.store;

import com.example.server.store.DevStatusModels.IssueRow;
import com.example.server.store.DevStatusModels.Snapshot;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DevStatusServiceTest {

    private static Snapshot sample() {
        IssueRow issue = new IssueRow(50, "樣本", true, 1L, 2L, null, Collections.singletonList("queue:1"));
        return new Snapshot(Collections.singletonList(issue), Collections.emptyList(), Collections.emptySet());
    }

    @Test
    public void cachesForTenMinutesThenRefetches() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        DevStatusService service = new DevStatusService(repo -> {
            calls.incrementAndGet();
            assertEquals("p4774756/PunchClock", repo);
            return sample();
        }, "p4774756/PunchClock");

        DevStatusService.View first = service.get(1_000L);
        assertFalse(first.stale);
        assertEquals(1_000L, first.fetchedAt);
        assertEquals("https://github.com/p4774756/PunchClock", first.source);
        assertNull(first.message);
        assertEquals(1, first.document.now.queue.size());
        assertEquals(1, calls.get());

        DevStatusService.View cached = service.get(1_000L + DevStatusService.TTL_MS - 1);
        assertFalse(cached.stale);
        assertEquals(1_000L, cached.fetchedAt);
        assertEquals(1, calls.get());

        DevStatusService.View again = service.get(1_000L + DevStatusService.TTL_MS);
        assertFalse(again.stale);
        assertEquals(1_000L + DevStatusService.TTL_MS, again.fetchedAt);
        assertEquals(2, calls.get());
    }

    @Test
    public void failureKeepsLastSuccessAndMarksStale() {
        AtomicInteger calls = new AtomicInteger();
        DevStatusService service = new DevStatusService(repo -> {
            if (calls.incrementAndGet() == 1) {
                return sample();
            }
            throw new IOException("rate limit");
        }, "p4774756/PunchClock");

        assertEquals(1, service.get(5_000L).document.now.queue.size());
        DevStatusService.View stale = service.get(5_000L + DevStatusService.TTL_MS);
        assertTrue(stale.stale);
        assertEquals(1, stale.document.now.queue.size());
        assertEquals(5_000L, stale.fetchedAt);
        assertNull(stale.message);
    }

    @Test
    public void firstFailureReturnsEmptyAndMessage() {
        DevStatusService service = new DevStatusService(repo -> {
            throw new IOException("down");
        }, "p4774756/PunchClock");

        DevStatusService.View view = service.get(9L);
        assertFalse(view.stale);
        assertTrue(view.document.items.isEmpty());
        assertTrue(view.document.now.queue.isEmpty());
        assertEquals(DevStatusService.UNAVAILABLE, view.message);
        assertEquals(9L, view.fetchedAt);
    }

    @Test
    public void failureBackoffDoesNotCallFetcherAgain() {
        AtomicInteger calls = new AtomicInteger();
        DevStatusService service = new DevStatusService(repo -> {
            calls.incrementAndGet();
            throw new IOException("rate limit");
        }, "p4774756/PunchClock");

        DevStatusService.View first = service.get(1_000L);
        assertEquals(DevStatusService.UNAVAILABLE, first.message);
        assertEquals(1, calls.get());

        DevStatusService.View held = service.get(1_000L + DevStatusService.FAILURE_BACKOFF_MS - 1);
        assertEquals(DevStatusService.UNAVAILABLE, held.message);
        assertEquals(1, calls.get());

        service.get(1_000L + DevStatusService.FAILURE_BACKOFF_MS);
        assertEquals(2, calls.get());
    }
}
