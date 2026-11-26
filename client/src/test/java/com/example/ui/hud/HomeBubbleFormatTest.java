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
        assertTrue(text.contains("還有"));
        assertTrue(text.contains("小時") || text.contains("分鐘"));
        assertTrue(!text.matches(".*\\d天.*"));
        assertTrue(!text.matches(".*\\d{2}:\\d{2}:\\d{2}.*"));
    }

    @Test
    public void countdownUsesHoursNotDayClock() {
        CheckInTask task = task("每日排程", LocalDateTime.now().plusHours(11).plusMinutes(5), TaskStatus.SCHEDULED);
        String text = HomeBubbleFormat.formatNext(task);
        assertTrue(text.contains("每日排程"));
        assertTrue(text.contains("還有 11 小時"));
    }

    @Test
    public void crossMonthDateLabel() {
        LocalDate day = LocalDate.now().plusMonths(1).withDayOfMonth(15);
        String[] week = {"週日", "週一", "週二", "週三", "週四", "週五", "週六"};
        int dow = day.getDayOfWeek().getValue() % 7;
        String expected = day.getMonthValue() + "月" + day.getDayOfMonth() + "日 " + week[dow];
        assertEquals(expected, HomeBubbleFormat.dayLabel(day.atTime(9, 0)));
        assertTrue(day.getMonthValue() != LocalDate.now().getMonthValue());
    }

    @Test
    public void hidesCheckInLinesWhenUiHidden() {
        HomeBrief brief = HomeBrief.testSnapshot(1, 0, 2, 0, "任務", "08:00", "");
        CheckInTask next = task("每日", LocalDateTime.now().plusHours(2), TaskStatus.SCHEDULED);
        HomeBubbleFormat.BubbleLine line = HomeBubbleFormat.resolve(brief, List.of(next), "", "", false);
        assertTrue(!line.openLog);
        assertTrue(!line.text.contains("失敗"));
        assertTrue(!line.text.contains("每日"));
    }

    @Test
    public void allDoneMessageWhenNoWaiting() {
        HomeBrief brief = HomeBrief.testSnapshot(2, 0, 0, 0, null, "", "");
        HomeBubbleFormat.BubbleLine line = HomeBubbleFormat.resolve(brief, List.of(), "", "");
        assertTrue(line.text.contains("完成"));
        assertTrue(!line.text.contains("♡"));
    }

    @Test
    public void moodFallbackIsAmbient() {
        HomeBubbleFormat.BubbleLine line = HomeBubbleFormat.resolve(HomeBrief.empty(), List.of(), "", "午安");
        assertTrue(line.ambientMood);
        assertTrue(!line.openLog);
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