package com.example.service;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateIdlePolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 12, 9, 0);

    @Test
    public void idleWithoutTasks() {
        assertTrue(UpdateIdlePolicy.isIdle(Collections.emptyList(), NOW));
        assertTrue(UpdateIdlePolicy.isIdle(null, NOW));
    }

    @Test
    public void notIdleWhileCheckingIn() {
        assertFalse(UpdateIdlePolicy.isIdle(List.of(task(TaskStatus.CHECKING_IN, NOW, null)), NOW));
    }

    @Test
    public void notIdleWhenScheduleIsImminentOrOverdue() {
        assertFalse(UpdateIdlePolicy.isIdle(
                List.of(task(TaskStatus.SCHEDULED, NOW.plusMinutes(5), null)), NOW));
        assertFalse(UpdateIdlePolicy.isIdle(
                List.of(task(TaskStatus.SCHEDULED, NOW.plusMinutes(10), null)), NOW));
        assertFalse(UpdateIdlePolicy.isIdle(
                List.of(task(TaskStatus.SCHEDULED, NOW.minusMinutes(1), null)), NOW));
    }

    @Test
    public void actualTriggerTimeWinsOverTargetTime() {
        // 目標時間還遠，但隨機偏移後實際觸發時間已很近
        assertFalse(UpdateIdlePolicy.isIdle(
                List.of(task(TaskStatus.SCHEDULED, NOW.plusHours(3), NOW.plusMinutes(2))), NOW));
        assertTrue(UpdateIdlePolicy.isIdle(
                List.of(task(TaskStatus.SCHEDULED, NOW.plusMinutes(1), NOW.plusHours(2))), NOW));
    }

    @Test
    public void idleWhenNextScheduleIsFarAwayOrTasksFinished() {
        assertTrue(UpdateIdlePolicy.isIdle(
                List.of(task(TaskStatus.SCHEDULED, NOW.plusMinutes(11), null)), NOW));
        assertTrue(UpdateIdlePolicy.isIdle(
                List.of(task(TaskStatus.SUCCESS, NOW.minusHours(1), null),
                        task(TaskStatus.SCHEDULED, NOW.plusHours(8), null)), NOW));
    }

    private static CheckInTask task(TaskStatus status, LocalDateTime target, LocalDateTime actual) {
        CheckInTask task = new CheckInTask("t", "https://example.com", "btn", target, false, "chromium");
        task.setActualTriggerTime(actual);
        task.setStatus(status);
        return task;
    }
}
