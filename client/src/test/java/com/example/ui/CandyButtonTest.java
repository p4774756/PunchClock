package com.example.ui;

import org.junit.Test;

import javax.swing.JPanel;
import java.awt.FontMetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CandyButtonTest {

    @Test
    public void scheduleButtonsKeepTheirVariantAndRoomForTheLip() {
        CandyButton run = button("立即執行", CandyButton.Variant.PRIMARY);
        CandyButton skip = button("跳過日期", CandyButton.Variant.SECONDARY);
        CandyButton clear = button("清除紀錄", CandyButton.Variant.DANGER);
        assertEquals(CandyButton.Variant.PRIMARY, run.variant());
        assertEquals(CandyButton.Variant.SECONDARY, skip.variant());
        assertEquals(CandyButton.Variant.DANGER, clear.variant());
        assertRoomForLabel(run);
        assertRoomForLabel(skip);
        assertRoomForLabel(clear);
    }

    @Test
    public void nightThemeLeavesCandyButtonsAlone() {
        CandyButton button = button("立即執行", CandyButton.Variant.PRIMARY);
        JPanel panel = new JPanel();
        panel.add(button);
        NightTheme.apply(panel);
        assertTrue(button.getUI().getClass().getName().contains("CandyButton"));
        button.setEnabled(false);
        assertFalse(button.isEnabled());
    }

    private static CandyButton button(String text, CandyButton.Variant variant) {
        CandyButton button = new CandyButton(text, variant, CandyButton.Size.MEDIUM);
        button.setFont(UiFonts.chineseBold(13));
        return button;
    }

    private static void assertRoomForLabel(CandyButton button) {
        FontMetrics metrics = button.getFontMetrics(button.getFont());
        int text = metrics.stringWidth(button.getText());
        assertTrue(button.getPreferredSize().width > text + 16);
        assertTrue(button.getPreferredSize().height > metrics.getHeight() + 8);
    }
}
