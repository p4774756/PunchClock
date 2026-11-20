package com.example.ui.hud;

import org.junit.Test;

import java.awt.Rectangle;

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
    public void shellPreviewRendersWithoutError() {
        assertTrue(AnimeShell.renderShellPreview(380, 520, false).getWidth() > 0);
        assertTrue(AnimeShell.renderShellPreview(300, 56, true).getWidth() > 0);
    }

}