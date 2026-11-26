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
        boolean hasUnreadCue = false;
        for (String line : HomeMoodPool.allLines()) {
            assertTrue("不得含愛心符號：" + line, !line.contains("♡"));
            assertTrue("不得含愛心符號：" + line, !line.contains("♥"));
            if (line.contains("訊息") || line.contains("消息")) {
                hasUnreadCue = true;
            }
        }
        assertTrue(hasUnreadCue);
        String unread = HomeMoodPool.unreadAt(0);
        assertTrue(unread.contains("訊息") || unread.contains("消息"));
        unread = HomeMoodPool.unreadAt(1);
        assertTrue(unread.contains("訊息") || unread.contains("消息"));
    }
}
