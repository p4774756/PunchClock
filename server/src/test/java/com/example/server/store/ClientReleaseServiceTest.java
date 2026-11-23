package com.example.server.store;

import com.example.server.ClientRelease;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class ClientReleaseServiceTest {

    private static final ClientRelease ONE = new ClientRelease("1.0.0", "", "a".repeat(64), 1L,
            "https://github.com/p4774756/PunchClock/releases/download/v1.0.0/SKLbaby.jar", List.of());
    private static final ClientRelease TWO = new ClientRelease("2.0.0", "", "b".repeat(64), 2L,
            "https://github.com/p4774756/PunchClock/releases/download/v2.0.0/SKLbaby.jar", List.of());

    @Test
    public void cachesFreshResultForTtl() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ClientReleaseService service = new ClientReleaseService(repo -> {
            calls.incrementAndGet();
            return calls.get() == 1 ? ONE : TWO;
        });
        assertSame(ONE, service.get(0));
        assertSame(ONE, service.get(1_000L));
        assertEquals(1, calls.get());
        assertSame(TWO, service.get(ClientReleaseService.TTL_MS));
        assertEquals(2, calls.get());
    }

    @Test
    public void keepsLastGoodOnFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ClientReleaseService service = new ClientReleaseService(repo -> {
            if (calls.incrementAndGet() == 1) {
                return ONE;
            }
            throw new IOException("offline");
        });
        assertSame(ONE, service.get(0));
        assertSame(ONE, service.get(ClientReleaseService.TTL_MS));
        assertEquals(2, calls.get());
    }
}