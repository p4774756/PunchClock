package com.example.service;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

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

    @Test
    public void nextTriggerTimeAfterTodaysSlot_skipsTodayWhenPunchedEarly() {
        LocalDateTime punchedAt = LocalDateTime.of(2026, 8, 27, 8, 56);
        LocalDateTime next = SlotScheduleHelper.nextTriggerTimeAfterTodaysSlot(9, 0, true, punchedAt);
        assertEquals(LocalDateTime.of(2026, 8, 28, 9, 0), next);
    }

    @Test
    public void nextTriggerTimeAfterTodaysSlot_skipsTodayAtExactSlotTime() {
        LocalDateTime atSlot = LocalDateTime.of(2026, 8, 27, 9, 0, 0);
        LocalDateTime next = SlotScheduleHelper.nextTriggerTimeAfterTodaysSlot(9, 0, true, atSlot);
        assertEquals(LocalDateTime.of(2026, 8, 28, 9, 0), next);
    }

    @Test
    public void nextTriggerTimeAfterTodaysSlot_tomorrowWhenAlreadyPast() {
        LocalDateTime afterSlot = LocalDateTime.of(2026, 8, 27, 9, 5);
        LocalDateTime next = SlotScheduleHelper.nextTriggerTimeAfterTodaysSlot(9, 0, true, afterSlot);
        assertEquals(LocalDateTime.of(2026, 8, 28, 9, 0), next);
    }

    @Test
    public void nextTriggerTime_skipsListedDatesAndWeekend() {
        LocalDateTime sundayEvening = LocalDateTime.of(2026, 10, 4, 19, 0);
        Set<LocalDate> skip = Set.of(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 7));
        LocalDateTime next = SlotScheduleHelper.nextTriggerTime(9, 0, true, sundayEvening, skip);
        assertEquals(LocalDateTime.of(2026, 10, 8, 9, 0), next);
    }

    @Test
    public void nextTriggerTime_skipsTodayWhenListed() {
        LocalDateTime mondayMorning = LocalDateTime.of(2026, 10, 5, 8, 0);
        LocalDateTime next = SlotScheduleHelper.nextTriggerTime(
                9, 0, true, mondayMorning, Set.of(LocalDate.of(2026, 10, 5)));
        assertEquals(LocalDateTime.of(2026, 10, 6, 9, 0), next);
    }

    @Test
    public void nextTriggerTimeAfterTodaysSlot_skipsFollowingSkipDates() {
        LocalDateTime punchedEarly = LocalDateTime.of(2026, 10, 2, 8, 56); // Friday
        Set<LocalDate> skip = Set.of(
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 6),
                LocalDate.of(2026, 10, 7));
        LocalDateTime next = SlotScheduleHelper.nextTriggerTimeAfterTodaysSlot(9, 0, true, punchedEarly, skip);
        assertEquals(LocalDateTime.of(2026, 10, 8, 9, 0), next);
    }
}
