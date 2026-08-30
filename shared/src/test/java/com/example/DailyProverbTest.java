package com.example;

import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DailyProverbTest {

    @Test
    public void sameDateReturnsSameProverb() {
        DailyProverb.Entry a = DailyProverb.forDate(LocalDate.of(2026, 8, 26));
        DailyProverb.Entry b = DailyProverb.forDate(LocalDate.of(2026, 8, 26));
        assertEquals(a.en, b.en);
        assertEquals(a.zh, b.zh);
        assertEquals(a.index, b.index);
        assertEquals("2026-08-26", a.date);
    }

    @Test
    public void proverbFieldsArePresent() {
        DailyProverb.Entry entry = DailyProverb.forToday();
        assertFalse(entry.en.trim().isEmpty());
        assertFalse(entry.zh.trim().isEmpty());
        assertFalse(entry.context.trim().isEmpty());
        assertTrue(entry.context.matches("^S\\d+E\\d+ · .+"));
        assertTrue(entry.index >= 0);
        assertTrue(entry.index < DailyProverb.proverbCount());
    }

    @Test
    public void hashIsStableUnsigned() {
        assertEquals(3473338209L, DailyProverb.hashDay("2026-08-26"));
    }

    @Test
    public void alignsWithServerSampleDay() {
        DailyProverb.Entry entry = DailyProverb.forDate(LocalDate.of(2026, 8, 26));
        assertEquals(9, entry.index);
        assertEquals("Ross: \"Unagi.\"", entry.en);
        assertTrue(entry.zh.contains("鰻魚"));
        assertTrue(entry.context.contains("S6E17"));
    }
}
