package com.example.ui.hud;

import org.junit.Test;

import javax.swing.JTabbedPane;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AnimeShellNavTest {

    @Test
    public void iconTabsKeepTabIndexes() {
        assertEquals(5, AnimeShell.navCount());
        assertEquals("首頁", AnimeShell.navLabel(0));
        assertEquals("排程", AnimeShell.navLabel(1));
        assertEquals("好友傳檔", AnimeShell.navLabel(2));
        assertEquals("日誌", AnimeShell.navLabel(3));
        assertEquals("設定", AnimeShell.navLabel(4));
        assertEquals(-1, AnimeShell.tabForNav(0));
        assertEquals(0, AnimeShell.tabForNav(1));
        assertEquals(2, AnimeShell.tabForNav(2));
        assertEquals(3, AnimeShell.tabForNav(3));
        assertEquals(1, AnimeShell.tabForNav(4));
    }

    @Test
    public void tabRowsDoNotOverlap() {
        int w = ShellDimensions.DEFAULT_WIDTH;
        int h = ShellDimensions.DEFAULT_HEIGHT;
        for (int i = 0; i < AnimeShell.navCount(); i++) {
            Rectangle a = AnimeShell.navRowBounds(w, h, i);
            assertTrue(a.width > 20);
            assertTrue(a.width * AnimeShell.navCount() <= w + 4);
            assertTrue(a.y + a.height <= h);
            for (int j = i + 1; j < AnimeShell.navCount(); j++) {
                Rectangle b = AnimeShell.navRowBounds(w, h, j);
                assertTrue(!a.intersects(b));
            }
        }
    }

    @Test
    public void failureBubbleOpensLog() {
        HomeBoard board = new HomeBoard();
        board.layout(new Rectangle(0, 48, 360, 400));
        board.setBubble(new HomeBubbleFormat.BubbleLine("有任務失敗了", true));
        assertTrue(board.bubbleOpensLog());
    }

    @Test
    public void navIconsExposeTooltips() {
        AnimeShell shell = new AnimeShell(null, new JTabbedPane());
        shell.setSize(ShellDimensions.DEFAULT_WIDTH, ShellDimensions.DEFAULT_HEIGHT);
        shell.doLayout();
        for (int i = 0; i < AnimeShell.navCount(); i++) {
            Rectangle box = AnimeShell.navRowBounds(
                    ShellDimensions.DEFAULT_WIDTH, ShellDimensions.DEFAULT_HEIGHT, i);
            MouseEvent event = new MouseEvent(
                    shell, MouseEvent.MOUSE_MOVED, 0, 0,
                    box.x + box.width / 2, box.y + box.height / 2,
                    0, false);
            assertEquals(AnimeShell.navTooltip(i), shell.getToolTipText(event));
        }
        shell.setMiniMode(true);
        MouseEvent mini = new MouseEvent(shell, MouseEvent.MOUSE_MOVED, 0, 0, 20, 20, 0, false);
        assertEquals("點一下展開", shell.getToolTipText(mini));
    }

    @Test
    public void shellPreviewRendersWithoutError() {
        assertTrue(AnimeShell.renderShellPreview(380, 520, false).getWidth() > 0);
        assertTrue(AnimeShell.renderShellPreview(300, 56, true).getWidth() > 0);
    }

    @Test
    public void navFillIconsLoad() {
        for (int i = 0; i < AnimeShell.navCount(); i++) {
            String id = TabNavIcon.navIconId(i);
            TabNavIcon idle = TabNavIcon.load(id);
            TabNavIcon active = TabNavIcon.loadActive(id);
            assertNotNull(idle);
            assertNotNull(active);
            assertTrue("icon " + id, idle.ready());
            assertTrue("active " + id, active.ready());
            assertTrue(idle.partCount() > 0);
            assertTrue(active.partCount() > 0);
            assertTrue("active differs from idle for " + id, active.partCount() != idle.partCount()
                    || !sameResource(id, false, id, true));
        }
    }

    @Test
    public void navInactiveLabelColors() {
        assertEquals(new Color(0xFF, 0xB3, 0xCC), AnimeShell.navLabelColor("home"));
        assertEquals(new Color(0xFF, 0xC4, 0xA8), AnimeShell.navLabelColor("calendar-clock"));
        assertEquals(new Color(0x8F, 0xDF, 0xC0), AnimeShell.navLabelColor("folder-up"));
        assertEquals(new Color(0xC9, 0xB6, 0xF0), AnimeShell.navLabelColor("notebook-text"));
        assertEquals(new Color(0xA8, 0xD4, 0xF5), AnimeShell.navLabelColor("settings"));
    }

    private static boolean sameResource(String idA, boolean activeA, String idB, boolean activeB) {
        String fileA = activeA ? idA + "-active" : idA;
        String fileB = activeB ? idB + "-active" : idB;
        try (var a = TabNavIcon.class.getResourceAsStream("/hud/nav/" + fileA + ".svg");
             var b = TabNavIcon.class.getResourceAsStream("/hud/nav/" + fileB + ".svg")) {
            if (a == null || b == null) {
                return false;
            }
            return java.util.Arrays.equals(a.readAllBytes(), b.readAllBytes());
        } catch (java.io.IOException ex) {
            return false;
        }
    }

    @Test
    public void glyphStringsRetired() {
        try {
            AnimeShell.class.getDeclaredMethod("tabGlyph", int.class);
            fail("tabGlyph should be removed");
        } catch (NoSuchMethodException expected) {
            // expected
        }
    }

    @Test
    public void hiddenCheckInUiOmitsScheduleNavSlot() {
        JTabbedPane tabs = new JTabbedPane();
        for (int i = 0; i < 4; i++) {
            tabs.addTab("", new javax.swing.JPanel());
        }
        AnimeShell shell = new AnimeShell(null, tabs);
        shell.setCheckInUiVisible(false);
        assertEquals(4, shell.visibleNavSlotCountForTests());
        assertEquals("好友傳檔", AnimeShell.navLabel(shell.navIdForSlotForTests(1)));
        shell.openTab(0);
        assertTrue(shell.isHoldingHome());
        shell.setCheckInUiVisible(true);
        assertEquals(5, shell.visibleNavSlotCountForTests());
    }

    @Test
    public void navLabelsFitAtMinimumWidth() {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scratch.createGraphics();
        try {
            g.setFont(com.example.ui.UiFonts.chinesePlain(11));
            FontMetrics fm = g.getFontMetrics();
            int slotW = Math.max(36, ShellDimensions.MIN_WIDTH / AnimeShell.navCount());
            for (int i = 0; i < AnimeShell.navCount(); i++) {
                assertTrue(fm.stringWidth(AnimeShell.navLabel(i)) <= slotW);
            }
        } finally {
            g.dispose();
        }
    }

}