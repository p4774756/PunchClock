package com.example.service;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class HeartbeatServiceCheckinReportTest {

    @Test
    public void payloadKeepsLastResultAfterSlotIsRescheduled() {
        CheckInTask task = sampleTask();
        task.setStatus(TaskStatus.SUCCESS);
        task.setResultMessage("[成功] 打卡成功");
        task.rememberLastResult();

        task.setStatus(TaskStatus.SCHEDULED);
        task.setResultMessage("");

        Map<String, Object> payload = HeartbeatService.toTaskPayload(task);
        assertEquals("SCHEDULED", payload.get("status"));
        assertEquals("", payload.get("message"));
        assertEquals("SUCCESS", payload.get("lastResultStatus"));
        assertEquals("[成功] 打卡成功", payload.get("lastResultMessage"));
    }

    @Test
    public void payloadOmitsLastResultWhenNeverPunched() {
        CheckInTask task = sampleTask();
        task.setStatus(TaskStatus.SCHEDULED);

        Map<String, Object> payload = HeartbeatService.toTaskPayload(task);
        assertFalse(payload.containsKey("lastResultStatus"));
        assertNull(payload.get("lastResultStatus"));
    }

    @Test
    public void displayResultFallsBackToLastResultAfterReschedule() {
        CheckInTask task = sampleTask();
        task.setStatus(TaskStatus.FAILED);
        task.setResultMessage("[失敗] 找不到按鈕");
        task.rememberLastResult();
        task.setStatus(TaskStatus.SCHEDULED);
        task.setResultMessage("");

        assertEquals("[失敗] 找不到按鈕", task.getDisplayResultMessage());
    }

    private static CheckInTask sampleTask() {
        CheckInTask task = new CheckInTask(
                "下班打卡",
                "https://example.com/checkin",
                "#btn",
                LocalDateTime.of(2026, 9, 4, 18, 0),
                false,
                "msedge");
        task.setId("work-out");
        return task;
    }
}
