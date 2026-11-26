package com.example.service;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class HeartbeatFailurePolicyTest {

    @Test
    public void backoff_advancesOnFailureAndResetsOnSuccess() {
        HeartbeatFailurePolicy policy = new HeartbeatFailurePolicy();
        assertEquals(15, policy.currentIntervalSeconds());
        policy.onConnectionFailure("x");
        assertEquals(30, policy.currentIntervalSeconds());
        policy.onConnectionFailure("x");
        assertEquals(60, policy.currentIntervalSeconds());
        policy.onConnectionFailure("x");
        assertEquals(120, policy.currentIntervalSeconds());
        policy.onConnectionFailure("x");
        assertEquals(300, policy.currentIntervalSeconds());
        policy.onConnectionFailure("x");
        assertEquals(300, policy.currentIntervalSeconds());
        policy.onSuccess();
        assertEquals(15, policy.currentIntervalSeconds());
    }

    @Test
    public void connectionFailure_dedupAndFiveMinuteSummaryWithMutableClock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        HeartbeatFailurePolicy policy = new HeartbeatFailurePolicy(clock);

        assertTrue(policy.onConnectionFailure("refused").isPresent());
        assertFalse(policy.onConnectionFailure("refused").isPresent());

        clock.advanceSeconds(TimeUnit.MINUTES.toSeconds(4));
        assertFalse(policy.onConnectionFailure("refused").isPresent());

        clock.advanceSeconds(TimeUnit.MINUTES.toSeconds(2));
        Optional<String> summary = policy.onConnectionFailure("refused");
        assertTrue(summary.isPresent());
        assertEquals("[連線] 仍斷線，已重試 4 次", summary.get());
    }

    @Test
    public void recovery_reportsOfflineDuration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        HeartbeatFailurePolicy policy = new HeartbeatFailurePolicy(clock);

        policy.onConnectionFailure("down");
        clock.advanceSeconds(TimeUnit.MINUTES.toSeconds(7));
        Optional<String> recovered = policy.onSuccess();
        assertTrue(recovered.isPresent());
        assertEquals("[連線] 已恢復連線，斷線約 7 分鐘", recovered.get());
        assertFalse(policy.onSuccess().isPresent());
    }

    @Test
    public void reset_clearsBackoff() {
        HeartbeatFailurePolicy policy = new HeartbeatFailurePolicy();
        policy.onConnectionFailure("x");
        assertEquals(30, policy.currentIntervalSeconds());
        policy.reset();
        assertEquals(15, policy.currentIntervalSeconds());
    }

    @Test
    public void shouldLog401Hint_onlyForHttp401() {
        assertFalse(HeartbeatFailurePolicy.shouldLog401Hint(0, true));
        assertFalse(HeartbeatFailurePolicy.shouldLog401Hint(403, false));
        assertTrue(HeartbeatFailurePolicy.shouldLog401Hint(401, false));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone = ZoneId.systemDefault();

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}