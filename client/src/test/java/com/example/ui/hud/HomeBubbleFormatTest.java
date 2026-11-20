package com.example.ui.hud;

import com.example.model.CheckInTask;
import com.example.model.TaskStatus;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HomeBubbleFormatTest {

    @Test
    public void failureBeatsNextTask() {
        HomeBrief brief = HomeBrief.testSnapshot(1, 0, 2, 0, "任務", "08:00", "");
        CheckInTask next = task("每日", LocalDateTime.now().plusHours(2), TaskStatus.SCHEDULED);
        HomeBubbleFormat.BubbleLine line = HomeBubbleFormat.resolve(brief, List.of(next), "", "");
        assertTrue(line.openLog);
        assertTrue(line.text.contains("失敗"));
    }

    @Test
    public void todayTomorrowAndWeekLabels() {
        LocalDateTime today = LocalDate.now().atTime(8, 55);
        assertEquals("今天", HomeBubbleFormat.dayLabel(today));
        assertEquals("明天", HomeBubbleFormat.dayLabel(today.plusDays(1)));
        assertEquals("昨天", HomeBubbleFormat.dayLabel(today.minusDays(1)));
        String week = HomeBubbleFormat.dayLabel(today.plusDays(3));
        assertTrue(week.startsWith("週"));
    }

    @Test
    public void nextTaskLineIncludesCountdownHint() {
        CheckInTask task = task("每日排程", LocalDate.now().plusDays(1).atTime(8, 55), TaskStatus.SCHEDULED);
        String text = HomeBubbleFormat.formatNext(task);
        assertTrue(text.contains("明天"));
        assertTrue(text.contains("08:55"));
        assertTrue(text.contains("每日排程"));
    }

    @Test
    public void allDoneMessageWhenNoWaiting() {
        HomeBrief brief = HomeBrief.testSnapshot(2, 0, 0, 0, null, "", "");
        HomeBubbleFormat.BubbleLine line = HomeBubbleFormat.resolve(brief, List.of(), "", "");
        assertTrue(line.text.contains("完成"));
    }

    private static CheckInTask task(String name, LocalDateTime when, TaskStatus status) {
        CheckInTask task = new CheckInTask();
        task.setName(name);
        task.setActualTriggerTime(when);
        task.setStatus(status);
        task.setId("t-" + name);
        return task;
    }
}