package com.example.service;

import com.example.model.CheckInHistoryEntry;
import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class CheckInHistoryServiceTest {

    private Path tempDir;
    private Path historyFile;
    private CheckInHistoryService historyService;

    @Before
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("punchclock-history-test");
        historyFile = tempDir.resolve("checkin-history.json");
        historyService = new CheckInHistoryService(historyFile);
    }

    @After
    public void tearDown() throws Exception {
        Files.deleteIfExists(historyFile);
        Files.deleteIfExists(tempDir);
    }

    @Test
    public void record_ignoresNonTerminalStatus() {
        CheckInTask task = new CheckInTask();
        task.setName("上班打卡");
        task.setStatus(TaskStatus.SCHEDULED);
        task.setResultMessage("waiting");

        historyService.record(task, true);
        assertTrue(historyService.getRecent().isEmpty());
        assertFalse(Files.exists(historyFile));
    }

    @Test
    public void record_prependsAndPersistsSuccess() {
        CheckInTask task = new CheckInTask();
        task.setName("上班打卡");
        task.setStatus(TaskStatus.SUCCESS);
        task.setResultMessage("[成功] 打卡成功");

        historyService.record(task, true);
        List<CheckInHistoryEntry> recent = historyService.getRecent();
        assertEquals(1, recent.size());
        assertEquals("上班打卡", recent.get(0).getSlotName());
        assertEquals(TaskStatus.SUCCESS, recent.get(0).statusEnum());
        assertFalse(recent.get(0).isImmediate());
        assertTrue(Files.exists(historyFile));

        CheckInHistoryService reloaded = new CheckInHistoryService(historyFile);
        assertEquals(1, reloaded.getRecent().size());
        assertEquals("[成功] 打卡成功", reloaded.getRecent().get(0).getMessage());
    }

    @Test
    public void record_marksImmediateWhenNotFromScheduler() {
        CheckInTask task = new CheckInTask();
        task.setName("下班打卡");
        task.setStatus(TaskStatus.FAILED);
        task.setResultMessage("[失敗] 打卡失敗");

        historyService.record(task, false);
        assertTrue(historyService.getRecent().get(0).isImmediate());
        assertTrue(historyService.getRecent().get(0).toDisplayLine().contains("立即執行"));
    }

    @Test
    public void record_trimsToMaxEntries() {
        for (int i = 0; i < CheckInHistoryService.MAX_ENTRIES + 3; i++) {
            CheckInTask task = new CheckInTask();
            task.setName("上班打卡");
            task.setStatus(TaskStatus.SUCCESS);
            task.setResultMessage("ok-" + i);
            historyService.record(task, true);
        }
        List<CheckInHistoryEntry> recent = historyService.getRecent();
        assertEquals(CheckInHistoryService.MAX_ENTRIES, recent.size());
        assertEquals("ok-" + (CheckInHistoryService.MAX_ENTRIES + 2), recent.get(0).getMessage());
        assertEquals("ok-3", recent.get(CheckInHistoryService.MAX_ENTRIES - 1).getMessage());
    }

    @Test
    public void clear_emptiesPersistedHistory() {
        CheckInTask task = new CheckInTask();
        task.setName("上班打卡");
        task.setStatus(TaskStatus.SUCCESS);
        task.setResultMessage("ok");
        historyService.record(task, true);

        historyService.clear(null);
        assertTrue(historyService.getRecent().isEmpty());

        CheckInHistoryService reloaded = new CheckInHistoryService(historyFile);
        assertTrue(reloaded.getRecent().isEmpty());
    }
}
