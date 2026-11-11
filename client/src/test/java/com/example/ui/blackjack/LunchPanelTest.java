package com.example.ui.blackjack;

import com.example.BlackjackProtocol;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LunchPanelTest {

    @Test
    public void placeholderIsNeverSubmittedAsText() {
        List<String> sent = new ArrayList<>();
        LunchPanel panel = new LunchPanel();
        panel.onSubmit(sent::add);
        panel.show(LunchPanel.View.CHOOSE, "你", null, "", false, null, null);
        assertEquals("", panel.input().getText());
        panel.confirmButton().doClick();
        assertEquals(1, sent.size());
        assertEquals("", sent.get(0));
        assertFalse(sent.get(0).contains(LunchPanel.PLACEHOLDER));
    }

    @Test
    public void inputKeepsFortyCodePointLimit() {
        LunchPanel panel = new LunchPanel();
        panel.show(LunchPanel.View.CHOOSE, "你", null, "", false, null, null);
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < BlackjackProtocol.MAX_LUNCH_CHARS + 10; i++) {
            longName.append(i % 2 == 0 ? "拉" : "😀");
        }
        panel.input().setText(longName.toString());
        String text = panel.input().getText();
        assertEquals(BlackjackProtocol.MAX_LUNCH_CHARS, text.codePointCount(0, text.length()));
    }

    @Test
    public void failedSendLocksFieldAndOffersRetry() {
        LunchPanel panel = new LunchPanel();
        panel.show(LunchPanel.View.CHOOSE, "你", null, "", false, "咖哩飯", "沒有送出");
        assertEquals("咖哩飯", panel.input().getText());
        assertFalse(panel.input().isEnabled());
        assertTrue(panel.confirmButton().isEnabled());
        assertEquals("再送一次", panel.confirmButton().getText());

        panel.show(LunchPanel.View.CHOOSE, "你", null, "", true, "咖哩飯", null);
        assertFalse(panel.confirmButton().isEnabled());
    }

    @Test
    public void waitingSideHasNoInput() {
        LunchPanel panel = new LunchPanel();
        panel.show(LunchPanel.View.WAIT, "【小明】", null, "", false, null, null);
        assertFalse(panel.input().isVisible());
        assertFalse(panel.input().isEnabled());
        assertFalse(panel.confirmButton().isVisible());
    }

    @Test
    public void finishedBadgeShowsPassiveLabelInsteadOfStand() {
        PlayerBadge badge = new PlayerBadge("你", null);
        badge.setTag(PlayerBadge.Tag.STAND);
        assertEquals("停牌", badge.tagText());
        badge.setTag(PlayerBadge.Tag.STAND, true);
        assertEquals(PlayerBadge.FINISHED_TEXT, badge.tagText());
        badge.setTag(null, true);
        assertEquals(PlayerBadge.FINISHED_TEXT, badge.tagText());
        badge.setTag(PlayerBadge.Tag.BUST, true);
        assertEquals("爆牌！", badge.tagText());
        badge.setTag(null);
        assertNull(badge.tagText());
    }
}
