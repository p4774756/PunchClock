package com.example.ui.hud;

import org.junit.Test;

import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AnimeShellNavTest {

    @Test
    public void sideNavKeepsOldTabIndexes() {
        assertEquals(5, AnimeShell.navCount());
        assertEquals("首頁", AnimeShell.navLabel(0));
        assertEquals("排程任務", AnimeShell.navLabel(1));
        assertEquals("好友與傳檔", AnimeShell.navLabel(2));
        assertEquals("設定", AnimeShell.navLabel(3));
        assertEquals("日誌", AnimeShell.navLabel(4));
        assertEquals(-1, AnimeShell.tabForNav(0));
        assertEquals(0, AnimeShell.tabForNav(1));
        assertEquals(2, AnimeShell.tabForNav(2));
        assertEquals(1, AnimeShell.tabForNav(3));
        assertEquals(3, AnimeShell.tabForNav(4));
    }

    @Test
    public void homeShortcutsStayReachable() {
        HomeBoard board = new HomeBoard();
        board.layout(new Rectangle(0, 0, 900, 700));
        assertTrue(board.run.width > 36);
        assertTrue(board.file.x > board.chat.x);
        assertEquals(HomeBoard.RUN, board.hit(board.run.x + 2, board.run.y + 2));
        assertEquals(HomeBoard.CHAT, board.hit(board.chat.x + 2, board.chat.y + 2));
        assertEquals(HomeBoard.FILE, board.hit(board.file.x + 2, board.file.y + 2));
    }

    @Test
    public void reminderStaysUnderTheArmsOnTheRight() {
        HomeBoard board = new HomeBoard();
        board.layout(new Rectangle(0, 0, 900, 700));
        board.seatReminder(new Rectangle(520, 160, 300, 140), 640);
        assertTrue(board.reminder.width >= 120);
        assertTrue(board.reminder.x > board.schedule.x + board.schedule.width - 20);
        assertEquals(160, board.reminder.y);
        assertEquals(140, board.reminder.height);
    }

    @Test
    public void orbitalReminderStaysInsideTheStage() {
        HomeBoard board = new HomeBoard();
        board.layout(new Rectangle(0, 0, 900, 700));
        board.seatOrbitalReminder();
        assertTrue(board.stage.contains(board.reminder));
        assertTrue(board.reminder.x > board.actions.x + board.actions.width - 4);
        assertTrue(board.reminder.y > board.stage.y);
    }

    @Test
    public void orbitalReminderCoversTheWaistCut() {
        HomeBoard board = new HomeBoard();
        board.layout(new Rectangle(0, 0, 900, 700));
        Rectangle figure = new Rectangle(480, 40, 360, 420);
        board.seatOrbitalReminderAtWaist(figure);
        assertTrue(board.stage.contains(board.reminder));
        assertTrue(board.reminder.y < figure.y + figure.height);
        int cardH = board.reminder.height;
        int overlap = Math.min(
                Math.max(36, cardH * 42 / 100),
                Math.max(36, Math.round(figure.height * 0.14f)));
        assertTrue(board.reminder.y <= figure.y + figure.height - overlap + 1);
        assertTrue(figure.y + figure.height <= board.reminder.y + board.reminder.height + 1);
        assertTrue(board.reminder.width + 8 >= figure.width * 70 / 100);
        assertTrue(board.reminder.x + board.reminder.width <= board.stage.x + board.stage.width);
    }
}
