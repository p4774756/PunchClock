package com.example.ui.hud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class HomeMoodPoolTest {

    @Test
    public void bandsFollowHourBoundaries() {
        assertEquals(HomeMoodPool.Band.MORNING, HomeMoodPool.bandForHour(5));
        assertEquals(HomeMoodPool.Band.MORNING, HomeMoodPool.bandForHour(11));
        assertEquals(HomeMoodPool.Band.NOON, HomeMoodPool.bandForHour(12));
        assertEquals(HomeMoodPool.Band.EVENING, HomeMoodPool.bandForHour(17));
        assertEquals(HomeMoodPool.Band.LATE_NIGHT, HomeMoodPool.bandForHour(23));
        assertEquals(HomeMoodPool.Band.LATE_NIGHT, HomeMoodPool.bandForHour(2));
    }

    @Test
    public void clickLinesAvoidImmediateRepeat() {
        int last = 0;
        int next = HomeMoodPool.nextClickIndex(last);
        assertNotEquals(last, next);
        String line = HomeMoodPool.clickAt(next);
        assertTrue(!line.contains("♡"));
        assertTrue(!line.contains("♥"));
    }

    @Test
    public void moodLinesAvoidImmediateRepeatPerBand() {
        int last = 1;
        int next = HomeMoodPool.nextMoodIndex(HomeMoodPool.Band.NOON, last);
        assertNotEquals(last, next);
        String line = HomeMoodPool.moodAt(HomeMoodPool.Band.NOON, next);
        assertTrue(line.contains("午") || line.contains("中"));
    }

    @Test
    public void statusLinesHaveNoEmoji() {
        assertTrue(!HomeMoodPool.doneLine().contains("♡"));
        assertTrue(!HomeMoodPool.allDoneLine().contains("♡"));
        assertTrue(HomeMoodPool.pickUnread(-1).contains("訊息") || HomeMoodPool.pickUnread(-1).contains("消息"));
    }
}