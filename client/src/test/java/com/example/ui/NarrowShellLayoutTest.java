package com.example.ui;

import org.junit.Test;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 約 380 寬扣掉外框後的內容區，按鈕與欄位要落在視埠裡。
 */
public class NarrowShellLayoutTest {

    private static final int VIEW_W = 320;
    private static final int VIEW_H = 720;
    private static final Color PEER_ONLINE_DOT = new Color(120, 210, 150);
    private static final Color PEER_OFFLINE_DOT = new Color(120, 108, 148);

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
            tabs.setSelectedIndex(PanelFactory.SETTINGS_ABOUT_TAB);
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
            assertFits(scroll, refs.clientIdCancelButton);
            assertTrue(refs.peerTable.getWidth() > 0);
            assertTrue(refs.peerTable.getWidth() <= VIEW_W);
            assertEquals(2, refs.peerTable.getColumnCount());
            int covered = 0;
            for (int i = 0; i < refs.peerTable.getColumnCount(); i++) {
                int colW = refs.peerTable.getColumnModel().getColumn(i).getWidth();
                assertTrue("col " + i + " width " + colW, colW >= 28);
                covered += colW;
                Object header = refs.peerTable.getColumnModel().getColumn(i).getHeaderValue();
                if (i == 0) {
                    assertEquals("好友", header);
                } else {
                    assertEquals("最愛 ★", header);
                }
            }
            assertTrue(covered <= refs.peerTable.getWidth() + 2);
            assertTrue(refs.peerHintLabel.getWidth() <= VIEW_W);
            assertTrue(refs.peerHintLabel.getWidth() > 40);
            Map<String, Boolean> online = new LinkedHashMap<>();
            online.put("company-worker2", true);
            online.put("company-worker3", false);
            refs.peerTable.putClientProperty(PanelFactory.PEER_ONLINE_CLIENT_KEY, online);
            Map<String, String> statuses = new LinkedHashMap<>();
            statuses.put("company-worker2", "開會中");
            statuses.put("company-worker3", "");
            refs.peerTable.putClientProperty(PanelFactory.PEER_STATUS_TEXTS_KEY, statuses);
            refs.peerTableModel.addRow(new Object[]{"company-worker2", "☆"});
            refs.peerTableModel.addRow(new Object[]{"company-worker3", "★"});
            assertPeerIdsFit(refs.peerTable, 300);
            assertPeerColumnAlignments(refs.peerTable);
            assertPeerOnlineDotsDiffer(refs.peerTable);
            refs.peerTable.setRowSelectionInterval(0, 0);
            java.awt.Component selected = refs.peerTable.prepareRenderer(refs.peerTable.getCellRenderer(0, 0), 0, 0);
            assertEquals(CandyTheme.PINK_INK.getRGB(), selected.getForeground().getRGB());
            assertTrue(refs.statusApplyButton != null);
            assertFits(scroll, refs.statusApplyButton);
            assertTrue(refs.statusCancelButton != null);
            assertFits(scroll, refs.statusCancelButton);
            assertEquals("★", refs.peerTableModel.getValueAt(1, PanelFactory.PEER_FAVORITE_COLUMN));
            refs.peerTableModel.setValueAt(PanelFactory.PEER_FAVORITE_OFF, 1, PanelFactory.PEER_FAVORITE_COLUMN);
            assertEquals("☆", refs.peerTableModel.getValueAt(1, PanelFactory.PEER_FAVORITE_COLUMN));
        });
    }

    @Test
    public void logTabFillsViewportHeight() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Fonts fonts = fonts();
            PanelFactory.LogPanelRefs refs = new PanelFactory.LogPanelRefs();
            JPanel logPanel = PanelFactory.createLogPanel(refs, fonts.main, fonts.bold);
            JPanel logTab = new JPanel(new BorderLayout());
            logTab.setOpaque(false);
            logTab.add(logPanel, BorderLayout.CENTER);
            JScrollPane scroll = layoutFill(logTab, 520);
            assertTrue(logPanel.getHeight() >= scroll.getViewport().getHeight() - 8);
        });
    }

    private static java.util.List<Component> findDescendants(Container root) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        if (root == null) {
            return out;
        }
        for (Component child : root.getComponents()) {
            out.add(child);
            if (child instanceof Container) {
                out.addAll(findDescendants((Container) child));
            }
        }
        return out;
    }

    private static void assertPeerColumnAlignments(javax.swing.JTable table) {
        for (int col = 0; col < table.getColumnCount(); col++) {
            Component cell = table.prepareRenderer(table.getCellRenderer(0, col), 0, col);
            if (col == 0) {
                assertTrue(cell instanceof PeerFriendNameCell);
            } else {
                assertTrue(cell instanceof JLabel);
                int align = ((JLabel) cell).getHorizontalAlignment();
                assertEquals(SwingConstants.CENTER, align);
            }
            javax.swing.table.TableColumn column = table.getColumnModel().getColumn(col);
            Component header = column.getHeaderRenderer().getTableCellRendererComponent(
                    table, column.getHeaderValue(), false, false, -1, col);
            assertTrue(header instanceof JLabel);
            int headerAlign = ((JLabel) header).getHorizontalAlignment();
            if (col == 0) {
                assertEquals(SwingConstants.LEADING, headerAlign);
            } else {
                assertEquals(SwingConstants.CENTER, headerAlign);
            }
        }
    }

    private static void assertPeerOnlineDotsDiffer(javax.swing.JTable table) {
        Color online = dotColor(table, 0);
        Color offline = dotColor(table, 1);
        assertColorNear(PEER_ONLINE_DOT, online);
        assertColorNear(PEER_OFFLINE_DOT, offline);
        assertNotEquals(online.getRGB(), offline.getRGB());
    }

    private static Color dotColor(javax.swing.JTable table, int row) {
        Component cell = table.prepareRenderer(table.getCellRenderer(row, 0), row, 0);
        assertTrue(cell instanceof PeerFriendNameCell);
        Icon icon = ((PeerFriendNameCell) cell).avatarIcon();
        assertTrue(icon instanceof ImageIcon);
        BufferedImage face = (BufferedImage) ((ImageIcon) icon).getImage();
        return new Color(face.getRGB(13, 13), true);
    }

    private static void assertColorNear(Color expected, Color actual) {
        assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= 8);
        assertTrue(Math.abs(expected.getGreen() - actual.getGreen()) <= 8);
        assertTrue(Math.abs(expected.getBlue() - actual.getBlue()) <= 8);
    }

    /** 380 寬小方塊裡好友表大約 300px。套用跟畫面上一樣的表頭之後，每欄都要蓋過表頭 preferred 寬。 */
    private static void assertPeerIdsFit(javax.swing.JTable table, int width) {
        NightTheme.apply(table);
        table.setSize(width, 160);
        table.doLayout();
        int covered = 0;
        javax.swing.table.JTableHeader header = table.getTableHeader();
        for (int i = 0; i < table.getColumnCount(); i++) {
            javax.swing.table.TableColumn column = table.getColumnModel().getColumn(i);
            int colW = column.getWidth();
            assertTrue("col " + i + " width " + colW, colW >= 28);
            covered += colW;
            javax.swing.table.TableCellRenderer headerRenderer = column.getHeaderRenderer();
            if (headerRenderer == null) {
                headerRenderer = header.getDefaultRenderer();
            }
            java.awt.Component painted = headerRenderer.getTableCellRendererComponent(
                    table, column.getHeaderValue(), false, false, -1, i);
            assertTrue("header " + column.getHeaderValue() + " pref " + painted.getPreferredSize().width
                            + " col " + colW,
                    painted.getPreferredSize().width <= colW);
        }
        assertTrue(covered <= table.getWidth() + 2);
        for (int row = 0; row < table.getRowCount(); row++) {
            java.awt.Component cell = table.prepareRenderer(table.getCellRenderer(row, 0), row, 0);
            int colW = table.getColumnModel().getColumn(0).getWidth();
            assertTrue("id row " + row + " pref " + cell.getPreferredSize().width + " col " + colW,
                    cell.getPreferredSize().width <= colW);
            String id = String.valueOf(table.getValueAt(row, 0));
            assertTrue(cell instanceof PeerFriendNameCell);
            PeerFriendNameCell nameCell = (PeerFriendNameCell) cell;
            assertTrue("id " + id + " in " + nameCell.displayId(), nameCell.displayId().contains(id));
            String status = PanelFactory.peerRowStatusText(table, id);
            if (status != null && !status.isBlank()) {
                assertEquals(status, nameCell.statusText());
            }
        }
    }

    private static JScrollPane layoutFill(JComponent body, int viewH) {
        JScrollPane scroll = CompactShellPanels.wrapFill(body, true);
        Component view = scroll.getViewport().getView();
        view.setBounds(0, 0, VIEW_W, viewH);
        scroll.setBounds(0, 0, VIEW_W, viewH);
        scroll.getViewport().setBounds(0, 0, VIEW_W, viewH);
        if (view instanceof Container) {
            layoutTree((Container) view);
        }
        return scroll;
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