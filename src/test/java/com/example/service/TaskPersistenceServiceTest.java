package com.example.service;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class TaskPersistenceServiceTest {

    private Path tempDir;
    private Path tasksFile;
    private TaskPersistenceService persistenceService;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("clickclick-tasks-test");
        tasksFile = tempDir.resolve("tasks.json");
        persistenceService = new TaskPersistenceService(tasksFile);
    }

    @After
    public void tearDown() throws Exception {
        if (Files.exists(tasksFile)) {
            Files.delete(tasksFile);
        }
        Files.deleteIfExists(tempDir);
    }

    @Test
    public void loadTasks_whenFileMissing_returnsEmptyList() {
        List<CheckInTask> tasks = persistenceService.loadTasks(null);
        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    public void saveAndLoad_roundTripPreservesTaskFields() {
        LocalDateTime target = LocalDateTime.of(2026, 8, 20, 9, 0, 0);
        CheckInTask task = new CheckInTask("上班打卡", "https://example.com/checkin", "#punch", target, true, "chrome");
        task.setRandomOffsetSeconds(120);
        task.setActualTriggerTime(target.plusSeconds(120));
        task.setStatus(TaskStatus.SUCCESS);
        task.setResultMessage("ok");

        persistenceService.saveTasks(Collections.singletonList(task), null);
        assertTrue(Files.exists(tasksFile));

        List<CheckInTask> loaded = persistenceService.loadTasks(null);
        assertEquals(1, loaded.size());

        CheckInTask restored = loaded.get(0);
        assertEquals(task.getId(), restored.getId());
        assertEquals("上班打卡", restored.getName());
        assertEquals("https://example.com/checkin", restored.getTargetUrl());
        assertEquals("#punch", restored.getButtonId());
        assertEquals(target, restored.getTargetTime());
        assertTrue(restored.isUseRandomOffset());
        assertEquals(120, restored.getRandomOffsetSeconds());
        assertEquals(target.plusSeconds(120), restored.getActualTriggerTime());
        assertEquals("chrome", restored.getBrowserType());
        assertEquals(TaskStatus.SUCCESS, restored.getStatus());
        assertEquals("ok", restored.getResultMessage());
    }

    @Test
    public void loadTasks_marksExpiredScheduledTaskAsCancelled() {
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        CheckInTask expired = new CheckInTask("過期任務", "https://example.com", "#btn", past, false, "msedge");
        expired.setActualTriggerTime(past);
        expired.setStatus(TaskStatus.SCHEDULED);

        CheckInTask future = new CheckInTask("未來任務", "https://example.com", "#btn",
                LocalDateTime.now().plusHours(2), false, "msedge");
        future.setActualTriggerTime(future.getTargetTime());
        future.setStatus(TaskStatus.SCHEDULED);

        persistenceService.saveTasks(Arrays.asList(expired, future), null);
        List<CheckInTask> loaded = persistenceService.loadTasks(null);

        assertEquals(2, loaded.size());
        CheckInTask expiredLoaded = loaded.stream()
                .filter(t -> "過期任務".equals(t.getName()))
                .findFirst()
                .orElseThrow();
        CheckInTask futureLoaded = loaded.stream()
                .filter(t -> "未來任務".equals(t.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(TaskStatus.CANCELLED, expiredLoaded.getStatus());
        assertTrue(expiredLoaded.getResultMessage().contains("過期"));
        assertEquals(TaskStatus.SCHEDULED, futureLoaded.getStatus());
    }

    @Test
    public void loadTasks_whenFileBlank_returnsEmptyList() throws Exception {
        Files.writeString(tasksFile, "   ");
        List<CheckInTask> tasks = persistenceService.loadTasks(null);
        assertTrue(tasks.isEmpty());
    }

    @Test
    public void loadTasks_whenJsonInvalid_returnsEmptyList() throws Exception {
        Files.writeString(tasksFile, "{ not-valid-json");
        List<CheckInTask> tasks = persistenceService.loadTasks(msg -> {});
        assertTrue(tasks.isEmpty());
    }
}
