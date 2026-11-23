package com.example.ui;

import com.example.ui.hud.AnimeShell;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 第 3 輪：用跟主視窗一樣的殼，在預設與最小尺寸確認同一列與說明沒被父層裁切。
 */
public class Issue305Round3LayoutTest {

    private static final String LONG_NOTES = "新版 v4.1.0 · abcdef0\n"
            + "大小 18.4 MB\n"
            + "這次修改了桌面寵物的視窗層級，並且調整了排程卡片的換行。\n"
            + "好友傳檔的裝置 ID 改成跟套用同一列。\n"
            + "設定頁的雲端連線改成橫排，窄的時候再換行。\n"
            + "中繼說明要看得到最後一句。\n"
            + "跳過日期移進每日排程。\n"
            + "名稱套用後會顯示已儲存。\n"
            + "底部分頁的迷你按鈕跟其他分頁同一套。\n"
            + "下載檔名改成 SKLbaby.jar。";

    @Test
    public void defaultAndMinShellKeepFailingRowsOnOneLine() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Font main = UiFonts.chinesePlain(13);
            Font bold = UiFonts.chineseBold(13);
            Font field = UiFonts.latinPlain(13);
            NightTheme.installLook();

            PanelFactory.PeerInteractionRefs peer = new PanelFactory.PeerInteractionRefs();
            PanelFactory.ServerConfigRefs settings = new PanelFactory.ServerConfigRefs();
            PanelFactory.SlotPanelRefs slots = new PanelFactory.SlotPanelRefs();
            PanelFactory.CheckInHistoryRefs history = new PanelFactory.CheckInHistoryRefs();
            Held def = shell(peer, settings, slots, history, main, bold, field, 356, 467);
            select(def.tabs, 2);
            assertSameBand(peer.avatarRow, "def-avatar");
            JComponent idRow = (JComponent) peer.clientIdCombo.getParent();
            assertSameBand(idRow, "def-id");
            assertTrue(peer.clientIdApplyButton.getX() > peer.clientIdCombo.getX());

            select(def.tabs, 1);
            settingsTabs(settings).setSelectedIndex(0);
            layoutDeep(def.shell);
            settings.heartbeatStatusLabel.setText("[正常] HTTP POST 正常");
            layoutDeep(def.shell);
            assertSameBand((JComponent) settings.enableServerCheckBox.getParent(), "def-cloud");
            assertEquals(13, settings.enableServerCheckBox.getFont().getSize());
            assertTextInside(settings.relayHelpArea, "def-relay");
            assertFalse(settings.relayHelpArea.getParent() instanceof javax.swing.JScrollPane);

            settings.updateStatusDetail.setText(LONG_NOTES);
            PanelFactory.fitRelayHelp(settings.updateStatusDetail);
            layoutDeep(def.shell);
            assertTextInside(settings.updateStatusDetail, "def-update");
            assertContained(settings.updateStatusDetail, "def-update-parent");
            assertButtonBelow(settings.updateStatusDetail, settings.checkUpdateButton, "def-update-button");
            capture(def.shell, "def-settings-long");
            captureScroll(def.shell, "def-settings-long-full");
            settings.updateStatusDetail.setText("有新版時會顯示在這裡。");
            PanelFactory.fitRelayHelp(settings.updateStatusDetail);
            layoutDeep(def.shell);
            capture(def.shell, "def-settings-short");
            captureScroll(def.shell, "def-settings-short-full");

            select(def.tabs, 2);
            capture(def.shell, "def-peer");
            select(def.tabs, 1);
            settingsTabs(settings).setSelectedIndex(1);
            layoutDeep(def.shell);
            capture(def.shell, "def-appearance");
            settingsTabs(settings).setSelectedIndex(2);
            layoutDeep(def.shell);
            capture(def.shell, "def-relay");
            settingsTabs(settings).setSelectedIndex(0);
            layoutDeep(def.shell);

            select(def.tabs, 0);
            JComponent schedule = (JComponent) slots.workIn.hourCombo.getParent().getParent();
            JComponent nameRow = (JComponent) slots.workIn.nameField.getParent();
            assertSameBand(schedule, "def-schedule");
            assertTrue(slots.skipDatesButton.getParent() == nameRow);
            assertSameBand(nameRow, "def-name");
            assertTrue(slots.skipDatesButton.getX() > slots.workIn.nameField.getX());
            capture(def.shell, "def-schedule");
            captureScroll(def.shell, "def-schedule-full");

            Held min = shell(peer, settings, slots, history, main, bold, field, 316, 387);
            select(min.tabs, 2);
            assertSameBand(peer.avatarRow, "min-avatar");
            assertSameBand((JComponent) peer.clientIdCombo.getParent(), "min-id");
            select(min.tabs, 1);
            settingsTabs(settings).setSelectedIndex(0);
            layoutDeep(min.shell);
            assertSameBand((JComponent) settings.enableServerCheckBox.getParent(), "min-cloud");
            settings.updateStatusDetail.setText(LONG_NOTES);
            PanelFactory.fitRelayHelp(settings.updateStatusDetail);
            layoutDeep(min.shell);
            assertTextInside(settings.updateStatusDetail, "min-update");
            assertContained(settings.updateStatusDetail, "min-update-parent");
            capture(min.shell, "min-settings-long");
            captureScroll(min.shell, "min-settings-long-full");
            select(min.tabs, 2);
            capture(min.shell, "min-peer");
            select(min.tabs, 0);
            capture(min.shell, "min-schedule");
        });
    }

    private static void capture(Component component, String name) {
        Path dir = shotsDir();
        if (dir == null) {
            return;
        }
        try {
            if (component instanceof AnimeShell) {
                AnimeShell shell = (AnimeShell) component;
                JTabbedPane tabs = null;
                for (Component child : shell.getComponents()) {
                    if (child instanceof JTabbedPane) {
                        tabs = (JTabbedPane) child;
                    }
                }
                if (tabs != null) {
                    shell.openTab(Math.max(0, tabs.getSelectedIndex()));
                    layoutDeep(shell);
                }
            }
            writeShot(component, dir.resolve(name + ".png"));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void captureScroll(Component root, String name) {
        Path dir = shotsDir();
        if (dir == null) {
            return;
        }
        JScrollPane scroll = findVisibleScroll(root);
        if (scroll == null) {
            capture(root, name);
            return;
        }
        Component view = scroll.getViewport().getView();
        java.awt.Dimension pref = view.getPreferredSize();
        view.setSize(Math.max(view.getWidth(), pref.width), Math.max(view.getHeight(), pref.height));
        layoutDeep(view);
        Component deep = deepestView(view);
        if (deep != view) {
            deep.setSize(Math.max(deep.getWidth(), deep.getPreferredSize().width),
                    Math.max(deep.getHeight(), deep.getPreferredSize().height));
            layoutDeep(deep);
        }
        capture(deep, name);
    }

    private static Component deepestView(Component component) {
        if (component instanceof JScrollPane) {
            Component view = ((JScrollPane) component).getViewport().getView();
            if (view != null) {
                return deepestView(view);
            }
        }
        if (component instanceof JTabbedPane) {
            Component selected = ((JTabbedPane) component).getSelectedComponent();
            if (selected != null) {
                return deepestView(selected);
            }
        }
        if (component instanceof Container) {
            Component best = component;
            int area = component.getWidth() * component.getHeight();
            for (Component child : ((Container) component).getComponents()) {
                Component deeper = deepestView(child);
                int childArea = deeper.getWidth() * deeper.getHeight();
                if (childArea > area) {
                    best = deeper;
                    area = childArea;
                }
            }
            return best;
        }
        return component;
    }

    private static JScrollPane findVisibleScroll(Component component) {
        if (component instanceof JTabbedPane) {
            Component selected = ((JTabbedPane) component).getSelectedComponent();
            return selected == null ? null : findVisibleScroll(selected);
        }
        if (component instanceof JScrollPane) {
            return (JScrollPane) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JScrollPane found = findVisibleScroll(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Path shotsDir() {
        String dir = System.getenv("SHOTS305_DIR");
        if (dir == null || dir.isBlank()) {
            return null;
        }
        try {
            Path out = Path.of(dir);
            Files.createDirectories(out);
            return out;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void writeShot(Component component, Path file) throws Exception {
        int w = Math.max(1, component.getWidth());
        int h = Math.max(1, component.getHeight());
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(28, 22, 48));
        g.fillRect(0, 0, w, h);
        component.paint(g);
        g.dispose();
        ImageIO.write(img, "png", file.toFile());
    }

    private static JTabbedPane settingsTabs(PanelFactory.ServerConfigRefs refs) {
        return (JTabbedPane) SwingUtilities.getAncestorOfClass(
                JTabbedPane.class, refs.enableServerCheckBox);
    }

    private static void select(JTabbedPane tabs, int index) {
        tabs.setSelectedIndex(index);
        layoutDeep(tabs);
    }

    private static void layoutDeep(Component component) {
        if (!(component instanceof Container)) {
            return;
        }
        Container container = (Container) component;
        container.doLayout();
        for (Component child : container.getComponents()) {
            layoutDeep(child);
        }
    }

    private static final class Held {
        final AnimeShell shell;
        final JTabbedPane tabs;

        Held(AnimeShell shell, JTabbedPane tabs) {
            this.shell = shell;
            this.tabs = tabs;
        }
    }

    private static Held shell(
            PanelFactory.PeerInteractionRefs peer,
            PanelFactory.ServerConfigRefs settings,
            PanelFactory.SlotPanelRefs slots,
            PanelFactory.CheckInHistoryRefs history,
            Font main, Font bold, Font field,
            int width, int height) {
        JPanel peerBody = PanelFactory.createPeerInteractionPanel(peer, main, bold);
        JPanel peerGroup = PanelFactory.createGroupPanel(PanelFactory.PEER_TAB_LABEL, bold);
        peerGroup.setLayout(new BorderLayout());
        peerGroup.add(peerBody, BorderLayout.CENTER);
        JPanel peerTab = new JPanel(new BorderLayout());
        peerTab.setOpaque(false);
        peerTab.setBorder(new EmptyBorder(8, 4, 8, 4));
        peerTab.add(peerGroup, BorderLayout.CENTER);

        JTabbedPane settingsTabs = PanelFactory.createServerConfigBody(settings, main, bold, field);
        JPanel settingsTab = new JPanel(new BorderLayout());
        settingsTab.setOpaque(false);
        settingsTab.setBorder(new EmptyBorder(4, 4, 4, 4));
        settingsTab.add(settingsTabs, BorderLayout.CENTER);

        JPanel slotPanel = PanelFactory.createSlotPanel(slots, main, bold, field);
        JPanel historyPanel = PanelFactory.createCheckInHistoryPanel(history, main, bold);
        JPanel tasksBody = new JPanel(new BorderLayout(0, 0));
        tasksBody.setOpaque(false);
        tasksBody.add(slotPanel, BorderLayout.NORTH);
        tasksBody.add(historyPanel, BorderLayout.CENTER);
        JPanel tasksTab = new JPanel(new BorderLayout(0, 6));
        tasksTab.setOpaque(false);
        tasksTab.setBorder(new EmptyBorder(8, 8, 8, 8));
        tasksTab.add(tasksBody, BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UiFonts.latinBold(13));
        tabs.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
        tabs.setUI(new BasicTabbedPaneUI());
        tabs.addTab("排程任務", CompactShellPanels.wrap(tasksTab, true));
        tabs.addTab("設定", CompactShellPanels.wrap(settingsTab, true));
        tabs.addTab(PanelFactory.PEER_TAB_LABEL, CompactShellPanels.wrap(peerTab, true));
        tabs.setBorder(new EmptyBorder(8, 8, 8, 8));
        AnimeShell.hideTabStrip(tabs);
        AnimeShell shell = new AnimeShell(null, tabs);
        shell.setSize(width, height);
        layoutDeep(shell);
        return new Held(shell, tabs);
    }

    private static void assertSameBand(JComponent row, String label) {
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;
        int tallest = 0;
        int seen = 0;
        for (Component child : row.getComponents()) {
            if (!child.isVisible() || child.getWidth() <= 0 || child.getHeight() <= 0) {
                continue;
            }
            seen++;
            top = Math.min(top, child.getY());
            bottom = Math.max(bottom, child.getY() + child.getHeight());
            tallest = Math.max(tallest, child.getHeight());
            assertTrue(label + " overflow " + child.getX() + "," + child.getY()
                            + " " + child.getWidth() + "x" + child.getHeight()
                            + " row " + row.getWidth() + "x" + row.getHeight(),
                    child.getX() >= -1 && child.getY() >= -1
                            && child.getX() + child.getWidth() <= row.getWidth() + 1
                            && child.getY() + child.getHeight() <= row.getHeight() + 1);
        }
        assertTrue(label + " empty", seen >= 2);
        assertTrue(label + " band " + (bottom - top) + " tallest " + tallest + " row " + row.getWidth(),
                bottom - top <= tallest + 6);
    }

    private static void assertTextInside(javax.swing.JTextArea area, String label) {
        try {
            int last = Math.max(0, area.getText().length() - 1);
            Rectangle2D end2d = area.modelToView2D(last);
            Rectangle end = end2d != null ? end2d.getBounds() : null;
            assertTrue(label + " end " + end + " area " + area.getWidth() + "x" + area.getHeight(),
                    end != null && end.y >= 0 && end.y + end.height <= area.getHeight() + 2);
        } catch (BadLocationException ex) {
            throw new AssertionError(label, ex);
        }
    }

    private static void assertContained(Component child, String label) {
        Container parent = child.getParent();
        assertTrue(label + " parent " + parent + " child " + child.getBounds()
                        + " childPref " + child.getPreferredSize()
                        + " parentPref " + (parent instanceof JComponent
                        ? ((JComponent) parent).getPreferredSize() : ""),
                parent != null
                        && child.getY() >= -1
                        && child.getY() + child.getHeight() <= parent.getHeight() + 2);
        Rectangle inParent = SwingUtilities.convertRectangle(parent, child.getBounds(), parent);
        Component walk = child;
        while (walk.getParent() != null && !(walk.getParent() instanceof javax.swing.JViewport)) {
            Container box = walk.getParent();
            Rectangle there = SwingUtilities.convertRectangle(walk.getParent(), walk.getBounds(), box);
            assertTrue(label + " clipped by " + box.getClass().getSimpleName()
                            + " child " + there + " box " + box.getWidth() + "x" + box.getHeight(),
                    there.y + there.height <= box.getHeight() + 2);
            walk = box;
        }
        assertTrue(label, inParent != null);
    }

    private static void assertButtonBelow(Component area, Component button, String label) {
        Rectangle a = SwingUtilities.convertRectangle(area.getParent(), area.getBounds(), area.getParent());
        Rectangle b = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), area.getParent());
        int gap = b.y - (a.y + a.height);
        assertTrue(label + " gap " + gap + " a " + a + " b " + b, gap >= 8);
        assertContained(button, label);
    }
}
