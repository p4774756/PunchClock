package com.example.service;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SlotScheduleHelperTest {

    @Test
    public void nextTriggerTime_sameDayWhenStillFuture() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 8, 0);
        LocalDateTime next = SlotScheduleHelper.nextTriggerTime(9, 0, true, now);
        assertEquals(LocalDateTime.of(2026, 8, 27, 9, 0), next);
    }

    @Test
    public void nextTriggerTime_tomorrowWhenPastToday() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 10, 0);
        LocalDateTime next = SlotScheduleHelper.nextTriggerTime(9, 0, true, now);
        assertEquals(LocalDateTime.of(2026, 8, 28, 9, 0), next);
    }

    @Test
    public void nextTriggerTime_skipsWeekendWhenWeekdaysOnly() {
        LocalDateTime fridayEvening = LocalDateTime.of(2026, 8, 28, 19, 0); // Friday
        LocalDateTime next = SlotScheduleHelper.nextTriggerTime(9, 0, true, fridayEvening);
        assertEquals(LocalDateTime.of(2026, 8, 31, 9, 0), next); // Monday
    }

    @Test
    public void isWeekend() {
        assertTrue(SlotScheduleHelper.isWeekend(LocalDateTime.of(2026, 8, 29, 12, 0).toLocalDate()));
        assertFalse(SlotScheduleHelper.isWeekend(LocalDateTime.of(2026, 8, 27, 12, 0).toLocalDate()));
    }
}
