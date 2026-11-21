package com.example.ui;

import org.junit.Test;

import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CandyHistoryColumnsTest {

    @Test
    public void tableWiderThanNarrowViewport() {
        assertTrue(CandyHistoryColumns.tableWidth() > 320);
    }

    @Test
    public void listDoesNotTrackViewportWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DefaultListModel<String> model = new DefaultListModel<>();
            model.addElement("2026-01-01 09:00:00"
                    + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                    + "任務"
                    + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                    + "成功"
                    + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                    + "排程"
                    + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                    + "very-long-result-text-that-should-not-fit");
            CandyHistoryColumns.View view = new CandyHistoryColumns.View(model);
            assertFalse(view.getScrollableTracksViewportWidth());
            assertEquals(CandyHistoryColumns.tableWidth(), view.getPreferredSize().width);
        });
    }

    @Test
    public void clickRevealsPlainDetail() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String line = "t"
                    + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                    + "n"
                    + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                    + "失敗"
                    + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                    + "手動"
                    + com.example.model.CheckInHistoryEntry.DISPLAY_SEPARATOR
                    + "connection reset by peer";
            DefaultListModel<String> model = new DefaultListModel<>();
            model.addElement(line);
            CandyHistoryColumns.View view = new CandyHistoryColumns.View(model);
            view.setSize(CandyHistoryColumns.tableWidth(), 60);
            view.dispatchEvent(new java.awt.event.MouseEvent(
                    view,
                    java.awt.event.MouseEvent.MOUSE_CLICKED,
                    0,
                    0,
                    40,
                    16,
                    1,
                    false,
                    java.awt.event.MouseEvent.BUTTON1));
            assertTrue(view.detailPane().isVisible());
            assertTrue(((JLabel) view.detailPane()).getText().contains("connection reset by peer"));
        });
    }
}