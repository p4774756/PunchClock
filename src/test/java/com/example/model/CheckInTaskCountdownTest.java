package com.example.model;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;

public class CheckInTaskCountdownTest {

    @Test
    public void scheduledFutureIsHms() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0, 0);
        LocalDateTime trigger = now.plusHours(1).plusMinutes(2).plusSeconds(3);
        assertEquals("01:02:03", CheckInTask.formatCountdown(TaskStatus.SCHEDULED, trigger, now));
    }

    @Test
    public void scheduledOverOneDayIncludesDayCount() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0, 0);
        LocalDateTime trigger = now.plusDays(6).plusHours(3).plusMinutes(12).plusSeconds(5);
        assertEquals("6天 03:12:05", CheckInTask.formatCountdown(TaskStatus.SCHEDULED, trigger, now));
    }

    @Test
    public void dueOrPastShowsImminent() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0, 0);
        assertEquals("即將觸發", CheckInTask.formatCountdown(TaskStatus.SCHEDULED, now, now));
        assertEquals("即將觸發", CheckInTask.formatCountdown(TaskStatus.SCHEDULED, now.minusSeconds(1), now));
    }

    @Test
    public void nonScheduledShowsDash() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0, 0);
        assertEquals("—", CheckInTask.formatCountdown(TaskStatus.SUCCESS, now.plusMinutes(5), now));
        assertEquals("—", CheckInTask.formatCountdown(TaskStatus.SCHEDULED, null, now));
    }
}
