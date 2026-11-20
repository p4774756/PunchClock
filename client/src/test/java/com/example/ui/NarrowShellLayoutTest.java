package com.example.ui;

import org.junit.Test;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 約 380 寬扣掉外框後的內容區，按鈕與欄位要落在視埠裡。
 */
public class NarrowShellLayoutTest {

    private static final int VIEW_W = 320;
    private static final int VIEW_H = 720;

    @Test
    public void scheduleControlsStayInsideViewport() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fonts fonts = fonts();
            PanelFactory.SlotPanelRefs slotRefs = new PanelFactory.SlotPanelRefs();
            PanelFactory.CheckInHistoryRefs historyRefs = new PanelFactory.CheckInHistoryRefs();
            JPanel tasks = new JPanel(new BorderLayout(0, 6));
            tasks.setOpaque(false);
            tasks.add(PanelFactory.createSlotPanel(slotRefs, fonts.main, fonts.bold, fonts.field), BorderLayout.NORTH);
            tasks.add(PanelFactory.createCheckInHistoryPanel(historyRefs, fonts.main, fonts.bold), BorderLayout.CENTER);
            JScrollPane scroll = layout(tasks);
            assertFits(scroll, slotRefs.executeNowButton);
            assertFits(scroll, slotRefs.browserCombo);
            assertFits(scroll, slotRefs.buttonIdCombo);
            assertFits(scroll, slotRefs.urlCombo);
            assertFits(scroll, slotRefs.workIn.minuteCombo);
            assertFits(scroll, slotRefs.workIn.hourCombo);
            assertFits(scroll, historyRefs.clearButton);
            FontMetrics urlMetrics = slotRefs.urlCombo.getFontMetrics(slotRefs.urlCombo.getFont());
            assertTrue(slotRefs.urlCombo.getWidth() >= urlMetrics.stringWidth("https://www.msn.com/zh-tw"));
        });
    }

    @Test
    public void settingsActionsStayInsideViewport() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fonts fonts = fonts();
            PanelFactory.ServerConfigRefs refs = new PanelFactory.ServerConfigRefs();
            JTabbedPane tabs = PanelFactory.createServerConfigBody(refs, fonts.main, fonts.bold, fonts.field);
            JPanel page = new JPanel(new BorderLayout());
            page.setOpaque(false);
            page.add(tabs, BorderLayout.CENTER);
            JScrollPane scroll = layout(page);
            assertFits(scroll, refs.trustAllSslCheckBox);
            assertFits(scroll, refs.checkUpdateButton);
            assertFits(scroll, refs.applyUpdateButton);
            assertTrue(refs.trustAllSslCheckBox.getWidth() + 1
                    >= refs.trustAllSslCheckBox.getPreferredSize().width);
            assertTrue(refs.checkUpdateButton.getX() >= 0);
            assertTrue(refs.applyUpdateButton.getX() >= 0);
        });
    }

    @Test
    public void peerColumnsAndChatStayInsideViewport() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fonts fonts = fonts();
            PanelFactory.PeerInteractionRefs refs = new PanelFactory.PeerInteractionRefs();
            JScrollPane scroll = layout(PanelFactory.createPeerInteractionPanel(refs, fonts.main, fonts.bold));
            assertFits(scroll, refs.openChatButton);
            assertFits(scroll, refs.clientIdCombo);
            assertFits(scroll, refs.clientIdApplyButton);
            assertTrue(refs.peerTable.getWidth() > 0);
            assertTrue(refs.peerTable.getWidth() <= VIEW_W);
            assertEquals(5, refs.peerTable.getColumnCount());
            int covered = 0;
            for (int i = 0; i < refs.peerTable.getColumnCount(); i++) {
                int colW = refs.peerTable.getColumnModel().getColumn(i).getWidth();
                assertTrue("col " + i + " width " + colW, colW >= 28);
                covered += colW;
            }
            assertTrue(covered <= refs.peerTable.getWidth() + 2);
            assertTrue(refs.peerHintLabel.getWidth() <= VIEW_W);
            assertTrue(refs.peerHintLabel.getWidth() > 40);
        });
    }

    private static JScrollPane layout(JComponent body) {
        JScrollPane scroll = CompactShellPanels.wrap(body);
        Component view = scroll.getViewport().getView();
        int height = Math.max(VIEW_H, view.getPreferredSize().height);
        view.setBounds(0, 0, VIEW_W, height);
        scroll.setBounds(0, 0, VIEW_W, VIEW_H);
        scroll.getViewport().setBounds(0, 0, VIEW_W, VIEW_H);
        if (view instanceof Container) {
            layoutTree((Container) view);
        }
        return scroll;
    }

    private static void layoutTree(Container container) {
        if (container.getWidth() <= 0) {
            container.setSize(VIEW_W, Math.max(1, container.getPreferredSize().height));
        }
        container.doLayout();
        if (container instanceof JTabbedPane) {
            JTabbedPane tabs = (JTabbedPane) container;
            Component selected = tabs.getSelectedComponent();
            if (selected != null && selected.getWidth() <= 0) {
                selected.setSize(Math.max(1, tabs.getWidth()), Math.max(1, selected.getPreferredSize().height));
            }
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layoutTree((Container) child);
            }
        }
    }

    private static void assertFits(JScrollPane scroll, Component component) {
        assertTrue(component.getWidth() > 0);
        assertTrue(component.getHeight() > 0);
        Point point = SwingUtilities.convertPoint(
                component.getParent(), component.getX(), component.getY(), scroll.getViewport());
        int right = point.x + component.getWidth();
        assertTrue("left " + point.x + " " + component, point.x >= -1);
        assertTrue("right " + right + " view " + scroll.getViewport().getWidth() + " " + component,
                right <= scroll.getViewport().getWidth() + 1);
    }

    private static Fonts fonts() {
        return new Fonts(UiFonts.chinesePlain(13), UiFonts.chineseBold(13), UiFonts.latinPlain(13));
    }

    private static final class Fonts {
        final Font main;
        final Font bold;
        final Font field;

        Fonts(Font main, Font bold, Font field) {
            this.main = main;
            this.bold = bold;
            this.field = field;
        }
    }
}
