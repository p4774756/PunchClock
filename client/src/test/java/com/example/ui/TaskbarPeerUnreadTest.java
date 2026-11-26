package com.example.ui;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaskbarPeerUnreadTest {

    @Test
    public void badgeText_emptyWhenNoUnread() {
        assertEquals("", TaskbarPeerUnread.badgeTextForPeerUnreadCount(0));
        assertEquals("", TaskbarPeerUnread.badgeTextForPeerUnreadCount(-3));
    }

    @Test
    public void badgeText_showsCountUpToNine() {
        assertEquals("1", TaskbarPeerUnread.badgeTextForPeerUnreadCount(1));
        assertEquals("9", TaskbarPeerUnread.badgeTextForPeerUnreadCount(9));
    }

    @Test
    public void badgeText_capsAtNinePlus() {
        assertEquals("9+", TaskbarPeerUnread.badgeTextForPeerUnreadCount(10));
        assertEquals("9+", TaskbarPeerUnread.badgeTextForPeerUnreadCount(99));
    }

    @Test
    public void shouldRequestAttention_whenNoAppWindowFocused() {
        assertTrue(TaskbarPeerUnread.shouldRequestDockAttention(null, null, Collections.emptyList()));
    }

    @Test
    public void shouldRequestAttention_whenFocusElsewhere() {
        Object other = new Object();
        Object main = new Object();
        assertTrue(TaskbarPeerUnread.shouldRequestDockAttention(other, main, List.of()));
    }

    @Test
    public void shouldNotRequestAttention_whenMainFocused() {
        Object main = new Object();
        assertFalse(TaskbarPeerUnread.shouldRequestDockAttention(main, main, List.of()));
    }

    @Test
    public void shouldNotRequestAttention_whenChatFocused() {
        Object main = new Object();
        Object chat = new Object();
        assertFalse(TaskbarPeerUnread.shouldRequestDockAttention(chat, main, List.of(chat)));
    }
}