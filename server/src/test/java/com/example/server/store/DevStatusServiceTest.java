package com.example.server.store;

import com.example.server.store.DevStatusModels.IssueRow;
import com.example.server.store.DevStatusModels.Snapshot;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
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

    @Test
    public void concurrentMissUsesSingleFlight() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        DevStatusService service = new DevStatusService(repo -> {
            calls.incrementAndGet();
            started.countDown();
            try {
                if (!release.await(3, TimeUnit.SECONDS)) {
                    throw new IOException("stuck");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", ex);
            }
            return sample();
        }, "p4774756/PunchClock");

        CyclicBarrier barrier = new CyclicBarrier(3);
        List<DevStatusService.View> views = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Thread t = new Thread(() -> {
                try {
                    barrier.await(3, TimeUnit.SECONDS);
                    views.add(service.get(10_000L));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            threads.add(t);
            t.start();
        }
        assertTrue(started.await(3, TimeUnit.SECONDS));
        // 給跟隨者時間掛上 inFlight，再放行唯一一次 fetch。
        Thread.sleep(80);
        release.countDown();
        for (Thread t : threads) {
            t.join(5_000L);
        }
        assertEquals(1, calls.get());
        assertEquals(3, views.size());
        for (DevStatusService.View view : views) {
            assertFalse(view.stale);
            assertEquals(1, view.document.now.queue.size());
        }
    }
}
