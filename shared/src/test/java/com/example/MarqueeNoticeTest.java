package com.example;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarqueeNoticeTest {

    @Test
    public void normalizeCollapsesWhitespaceAndKeepsSwitch() {
        MarqueeNotice notice = MarqueeNotice.normalize(true, "  下午三點\n會議室\t集合  ");
        assertTrue(notice.enabled);
        assertEquals("下午三點 會議室 集合", notice.text);
    }

    @Test
    public void emptyTextTurnsOffEvenWhenAskedOn() {
        MarqueeNotice notice = MarqueeNotice.normalize(true, "   \n");
        assertFalse(notice.enabled);
        assertEquals("", notice.text);
    }

    @Test
    public void disabledKeepsTheText() {
        MarqueeNotice notice = MarqueeNotice.normalize(false, "還想留著");
        assertFalse(notice.enabled);
        assertEquals("還想留著", notice.text);
    }

    @Test
    public void truncatesToMaxChars() {
        String raw = "字".repeat(MarqueeNotice.MAX_CHARS + 10);
        MarqueeNotice notice = MarqueeNotice.normalize(true, raw);
        assertEquals(MarqueeNotice.MAX_CHARS, notice.text.length());
        assertTrue(notice.enabled);
    }
}
