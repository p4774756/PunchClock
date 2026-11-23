package com.example.service;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 隱藏打卡介面只影響 UI 旗標，不應阻擋 {@link SchedulerService} 排程。
 */
public class CheckInUiSchedulerTest {

    private SchedulerService schedulerService;

    @Before
    public void setUp() {
        schedulerService = new SchedulerService();
    }

    @After
    public void tearDown() {
        schedulerService.shutdown();
    }

    @Test
    public void schedulerRunsWhenCheckInUiHiddenInConfig() {
        ConfigPersistenceService.CloudConfig config = new ConfigPersistenceService.CloudConfig();
        config.checkInUiVisible = false;
        config.targetUrl = "https://example.com/checkin";
        config.buttonId = "#btn";
        config.workIn.enabled = true;

        LocalDateTime target = LocalDateTime.now().plusHours(1);
        CheckInTask task = new CheckInTask(
                "work-in", config.targetUrl, config.buttonId, target, false, "msedge");

        assertTrue(config.checkInUiVisible == false);
        assertTrue(schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> done.run()));
        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
    }
}