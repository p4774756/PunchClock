package com.example.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 心跳連線失敗時的日誌去重、退避間隔與恢復摘要（可注入 {@link Clock} 供單元測試）。
 */
class HeartbeatFailurePolicy {

    static final int[] BACKOFF_INTERVAL_SECONDS = {30, 60, 120, 300};
    static final long STILL_OFFLINE_LOG_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5);

    private final Clock clock;

    private int backoffIndex;
    private boolean inFailureStreak;
    private int failureAttemptCount;
    private Instant failureStreakStart;
    private Instant lastPeriodicOfflineLogAt;
    private String firstFailureDetail;
    private boolean initialFailureLogged;

    HeartbeatFailurePolicy() {
        this(Clock.systemDefaultZone());
    }

    HeartbeatFailurePolicy(Clock clock) {
        this.clock = clock;
    }

    void reset() {
        backoffIndex = 0;
        inFailureStreak = false;
        failureAttemptCount = 0;
        failureStreakStart = null;
        lastPeriodicOfflineLogAt = null;
        firstFailureDetail = null;
        initialFailureLogged = false;
    }

    int currentIntervalSeconds() {
        return BACKOFF_INTERVAL_SECONDS[backoffIndex];
    }

    int backoffStepIndex() {
        return backoffIndex;
    }

    /**
     * 成功回應；若先前在失敗期間會回傳恢復日誌一行。
     */
    Optional<String> onSuccess() {
        backoffIndex = 0;
        if (!inFailureStreak) {
            return Optional.empty();
        }
        Instant start = failureStreakStart;
        clearFailureStreak();
        if (start == null) {
            return Optional.empty();
        }
        long minutes = Math.max(1, Duration.between(start, clock.instant()).toMinutes());
        return Optional.of("[連線] 已恢復連線，斷線約 " + minutes + " 分鐘");
    }

    /**
     * 連線層失敗（逾時、ConnectException 等）；去重後決定要不要寫日誌。
     */
    Optional<String> onConnectionFailure(String detail) {
        registerFailureAttempt();
        advanceBackoff();
        if (!initialFailureLogged) {
            initialFailureLogged = true;
            firstFailureDetail = trimDetail(detail);
            String line = "[失敗] 雲端連線中斷";
            if (firstFailureDetail != null && !firstFailureDetail.isEmpty()) {
                line += "（" + firstFailureDetail + "）";
            }
            lastPeriodicOfflineLogAt = clock.instant();
            return Optional.of(line);
        }
        return periodicStillOfflineLine();
    }

    /**
     * 已連上伺服器但 HTTP 狀態非 200；同樣計入失敗退避，日誌去重策略與連線失敗一致（摘要不同）。
     */
    Optional<String> onHttpError(int statusCode) {
        registerFailureAttempt();
        advanceBackoff();
        if (!initialFailureLogged) {
            initialFailureLogged = true;
            firstFailureDetail = null;
            lastPeriodicOfflineLogAt = clock.instant();
            return Optional.of("[警告] [HTTP POST 心跳] 伺服器回應異常，狀態碼：" + statusCode);
        }
        return periodicStillOfflineLine();
    }

    static boolean shouldLog401Hint(int statusCode, boolean connectionFailure) {
        return !connectionFailure && statusCode == 401;
    }

    static String unauthorizedHintLine() {
        return "[提示] 若為 401，請先取消「啟用雲端」→ 修改 Token / 網址 → 再重新勾選";
    }

    int failureAttemptCount() {
        return failureAttemptCount;
    }

    private void registerFailureAttempt() {
        if (!inFailureStreak) {
            inFailureStreak = true;
            failureStreakStart = clock.instant();
            failureAttemptCount = 0;
        }
        failureAttemptCount++;
    }

    private void advanceBackoff() {
        if (backoffIndex < BACKOFF_INTERVAL_SECONDS.length - 1) {
            backoffIndex++;
        }
    }

    private Optional<String> periodicStillOfflineLine() {
        Instant now = clock.instant();
        if (lastPeriodicOfflineLogAt != null
                && now.toEpochMilli() - lastPeriodicOfflineLogAt.toEpochMilli() < STILL_OFFLINE_LOG_INTERVAL_MS) {
            return Optional.empty();
        }
        lastPeriodicOfflineLogAt = now;
        return Optional.of("[連線] 仍斷線，已重試 " + failureAttemptCount + " 次");
    }

    private void clearFailureStreak() {
        inFailureStreak = false;
        failureAttemptCount = 0;
        failureStreakStart = null;
        lastPeriodicOfflineLogAt = null;
        firstFailureDetail = null;
        initialFailureLogged = false;
    }

    private static String trimDetail(String detail) {
        if (detail == null) {
            return "";
        }
        String trimmed = detail.trim();
        if (trimmed.length() > 200) {
            return trimmed.substring(0, 200) + "…";
        }
        return trimmed;
    }
}