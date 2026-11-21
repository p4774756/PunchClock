package com.example.ui.hud;

import org.junit.Test;

import javax.swing.JTabbedPane;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
            assertTrue(a.width * (AnimeShell.navCount() + 1) <= w + 4);
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

}