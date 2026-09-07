package com.example.service;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class SchedulerServiceTest {

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
    public void scheduleTask_withoutRandomOffset_usesExactTargetTime() {
        LocalDateTime target = LocalDateTime.now().plusSeconds(10);
        CheckInTask task = new CheckInTask("test", "http://example.com", "#btn", target, false, "msedge");

        boolean ok = schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> done.run());

        assertTrue(ok);
        assertEquals(0, task.getRandomOffsetSeconds());
        assertEquals(target.withNano(0), task.getActualTriggerTime().withNano(0));
        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
        assertTrue(task.hasComputedSchedule());
    }

    @Test
    public void scheduleTask_withRandomOffset_computesOffsetInRange() {
        LocalDateTime target = LocalDateTime.now().plusHours(1);
        CheckInTask task = new CheckInTask("test", "http://example.com", "#btn", target, true, "msedge");

        boolean ok = schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> done.run());

        assertTrue(ok);
        assertTrue(task.getRandomOffsetSeconds() >= -300);
        assertTrue(task.getRandomOffsetSeconds() <= 300);
        assertTrue(task.hasComputedSchedule());
    }

    @Test
    public void scheduleTask_withPrecomputedOffset_reusesExistingOffset() {
        LocalDateTime target = LocalDateTime.now().plusHours(2);
        CheckInTask task = new CheckInTask("test", "http://example.com", "#btn", target, true, "msedge");
        task.setRandomOffsetSeconds(42);
        task.setActualTriggerTime(target.plusSeconds(42));

        boolean ok = schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> done.run());

        assertTrue(ok);
        assertEquals(42, task.getRandomOffsetSeconds());
        assertEquals(target.plusSeconds(42).withNano(0), task.getActualTriggerTime().withNano(0));
    }

    @Test
    public void scheduleTask_nullTargetTime_returnsFalse() {
        CheckInTask task = new CheckInTask("test", "http://example.com", "#btn", null, false, "msedge");

        boolean ok = schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> done.run());

        assertFalse(ok);
    }

    @Test
    public void scheduleTask_pastTimeBeyondGracePeriod_returnsFalse() {
        LocalDateTime target = LocalDateTime.now().minusHours(2);
        CheckInTask task = new CheckInTask("test", "http://example.com", "#btn", target, false, "msedge");

        boolean ok = schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> done.run());

        assertFalse(ok);
        assertEquals(TaskStatus.FAILED, task.getStatus());
    }

    @Test
    public void cancelTask_preventsExecution() throws Exception {
        LocalDateTime target = LocalDateTime.now().plusSeconds(3);
        CheckInTask task = new CheckInTask("test", "http://example.com", "#btn", target, false, "msedge");
        AtomicBoolean executed = new AtomicBoolean(false);

        schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> {
            executed.set(true);
            done.run();
        });

        assertTrue(schedulerService.cancelTask(task.getId()));
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        assertEquals("來源未標示的取消", task.getResultMessage());

        Thread.sleep(4000);
        assertFalse("Task should not execute after cancel", executed.get());
    }

    @Test
    public void cancelTask_recordsCustomReason() {
        LocalDateTime target = LocalDateTime.now().plusHours(1);
        CheckInTask task = new CheckInTask("下班打卡", "http://example.com", "#btn", target, false, "msedge");
        schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> done.run());

        assertTrue(schedulerService.cancelTask(task.getId(), "網頁後台遠端取消"));
        assertEquals(TaskStatus.CANCELLED, task.getStatus());
        assertEquals("網頁後台遠端取消", task.getResultMessage());
    }

    @Test
    public void stopTimer_doesNotMarkCancelled() {
        LocalDateTime target = LocalDateTime.now().plusHours(1);
        CheckInTask task = new CheckInTask("上班打卡", "http://example.com", "#btn", target, false, "msedge");
        schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> done.run());

        schedulerService.stopTimer(task.getId());
        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
    }

    @Test
    public void cancelTask_doesNotOverwriteSuccess() {
        LocalDateTime target = LocalDateTime.now().plusHours(1);
        CheckInTask task = new CheckInTask("下班打卡", "http://example.com", "#btn", target, false, "msedge");
        schedulerService.addTaskRecord(task);
        task.setStatus(TaskStatus.SUCCESS);
        task.setResultMessage("打卡成功");

        schedulerService.cancelTask(task.getId(), "槽位已停用");
        assertEquals(TaskStatus.SUCCESS, task.getStatus());
        assertEquals("打卡成功", task.getResultMessage());
    }

    @Test
    public void scheduleTask_executesAtScheduledTime() throws Exception {
        LocalDateTime target = LocalDateTime.now().plusSeconds(2);
        CheckInTask task = new CheckInTask("test", "http://example.com", "#btn", target, false, "msedge");
        CountDownLatch latch = new CountDownLatch(1);

        schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> {
            t.setStatus(TaskStatus.SUCCESS);
            latch.countDown();
            done.run();
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(TaskStatus.SUCCESS, task.getStatus());
    }

    @Test
    public void scheduleTask_marksFailedWhenExecuteActionDoesNotReportResult() throws Exception {
        LocalDateTime target = LocalDateTime.now().plusSeconds(1);
        CheckInTask task = new CheckInTask("下班打卡", "http://example.com", "#btn", target, false, "msedge");
        CountDownLatch failed = new CountDownLatch(1);

        schedulerService.scheduleTask(task, t -> {
            if (t != null && t.getStatus() == TaskStatus.FAILED) {
                failed.countDown();
            }
        }, msg -> {}, (t, done) -> done.run());

        assertTrue(failed.await(5, TimeUnit.SECONDS));
        assertEquals("任務執行異常：未回報最終結果", task.getResultMessage());
    }

    @Test
    public void scheduleTask_doesNotFailWhenRescheduledDuringExecution() throws Exception {
        LocalDateTime target = LocalDateTime.now().plusSeconds(1);
        CheckInTask task = new CheckInTask("下班打卡", "http://example.com", "#btn", target, false, "msedge");
        CountDownLatch executed = new CountDownLatch(1);

        schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> {
            t.setStatus(TaskStatus.SUCCESS);
            t.setResultMessage("[成功] 打卡成功");
            t.rememberLastResult();
            t.setStatus(TaskStatus.SCHEDULED);
            t.setResultMessage("");
            t.setTargetTime(LocalDateTime.now().plusDays(1).withHour(18).withMinute(5).withSecond(0).withNano(0));
            t.setActualTriggerTime(t.getTargetTime());
            done.run();
            executed.countDown();
        });

        assertTrue(executed.await(5, TimeUnit.SECONDS));
        Thread.sleep(300);
        assertEquals(TaskStatus.SCHEDULED, task.getStatus());
        assertEquals("", task.getResultMessage());
        assertEquals(TaskStatus.SUCCESS, task.getLastResultStatus());
        assertEquals("[成功] 打卡成功", task.getLastResultMessage());
    }

    @Test
    public void scheduleTask_doesNotOverwritePendingResetForNextDay() throws Exception {
        LocalDateTime target = LocalDateTime.now().plusSeconds(1);
        CheckInTask task = new CheckInTask("下班打卡", "http://example.com", "#btn", target, false, "msedge");
        CountDownLatch ran = new CountDownLatch(1);

        schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> {
            t.setStatus(TaskStatus.SUCCESS);
            t.setResultMessage("[成功] 打卡成功");
            t.rememberLastResult();
            t.setStatus(TaskStatus.PENDING);
            t.setResultMessage("");
            done.run();
            ran.countDown();
        });

        assertTrue(ran.await(5, TimeUnit.SECONDS));
        Thread.sleep(300);
        assertEquals(TaskStatus.PENDING, task.getStatus());
        assertEquals(TaskStatus.SUCCESS, task.getLastResultStatus());
        assertNotEquals("任務執行異常：未回報最終結果", task.getResultMessage());
    }

    @Test
    public void scheduleTask_keepsReplacementFutureAfterRescheduleDuringRun() throws Exception {
        LocalDateTime target = LocalDateTime.now().plusSeconds(1);
        CheckInTask task = new CheckInTask("下班打卡", "http://example.com", "#btn", target, false, "msedge");
        task.setId("work-out");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch replacementArmed = new CountDownLatch(1);
        CountDownLatch secondRan = new CountDownLatch(1);

        schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> {
            firstStarted.countDown();
            try {
                assertTrue(replacementArmed.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            t.setStatus(TaskStatus.SUCCESS);
            t.setResultMessage("[成功] 打卡成功");
            t.rememberLastResult();
            t.setStatus(TaskStatus.SCHEDULED);
            done.run();
        });

        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        task.setUseRandomOffset(false);
        task.setTargetTime(LocalDateTime.now().plusSeconds(1));
        task.setActualTriggerTime(null);
        task.setRandomOffsetSeconds(0);
        boolean ok = schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> {
            secondRan.countDown();
            t.setStatus(TaskStatus.SUCCESS);
            done.run();
        });
        assertTrue(ok);
        replacementArmed.countDown();

        assertTrue("Replacement schedule should still fire", secondRan.await(5, TimeUnit.SECONDS));
        assertNotEquals("任務執行異常：未回報最終結果", task.getResultMessage());
    }

    @Test
    public void scheduleTask_afterExecuteSeesSuccessBeforeReschedule() throws Exception {
        LocalDateTime target = LocalDateTime.now().plusSeconds(1);
        CheckInTask task = new CheckInTask("test", "http://example.com", "#btn", target, false, "msedge");
        CountDownLatch after = new CountDownLatch(1);
        AtomicReference<TaskStatus> seen = new AtomicReference<>();

        schedulerService.scheduleTask(task, t -> {}, msg -> {}, (t, done) -> {
            t.setStatus(TaskStatus.SUCCESS);
            t.setResultMessage("ok");
            done.run();
        }, t -> {
            seen.set(t.getStatus());
            after.countDown();
        });

        assertTrue(after.await(5, TimeUnit.SECONDS));
        assertEquals(TaskStatus.SUCCESS, seen.get());
    }
}
