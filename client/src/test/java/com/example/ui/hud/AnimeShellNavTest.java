package com.example.ui.hud;

import org.junit.Test;

import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnimeShellNavTest {

    @Test
    public void gameCenterSitsAfterFriendsWithoutMovingOldTabs() {
        assertEquals(6, AnimeShell.navCount());
        assertEquals("首頁", AnimeShell.navLabel(0));
        assertEquals("排程任務", AnimeShell.navLabel(1));
        assertEquals("好友與傳檔", AnimeShell.navLabel(2));
        assertEquals("遊戲中心", AnimeShell.navLabel(3));
        assertEquals("設定", AnimeShell.navLabel(4));
        assertEquals("日誌", AnimeShell.navLabel(5));
        assertEquals(-1, AnimeShell.tabForNav(0));
        assertEquals(0, AnimeShell.tabForNav(1));
        assertEquals(2, AnimeShell.tabForNav(2));
        assertEquals(4, AnimeShell.tabForNav(3));
        assertEquals(1, AnimeShell.tabForNav(4));
        assertEquals(3, AnimeShell.tabForNav(5));
    }

    @Test
    public void homePlayButtonIsReachable() {
        HomeBoard board = new HomeBoard();
        board.layout(new Rectangle(0, 0, 900, 700));
        assertTrue(board.play.width > 36);
        assertTrue(board.play.x > board.file.x);
        assertEquals(HomeBoard.PLAY, board.hit(board.play.x + 4, board.play.y + 4));
        assertEquals(HomeBoard.RUN, board.hit(board.run.x + 2, board.run.y + 2));
        assertEquals(HomeBoard.CHAT, board.hit(board.chat.x + 2, board.chat.y + 2));
        assertEquals(HomeBoard.FILE, board.hit(board.file.x + 2, board.file.y + 2));
    }
}
