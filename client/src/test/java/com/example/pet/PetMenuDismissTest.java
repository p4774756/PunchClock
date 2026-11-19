package com.example.pet;

import org.junit.Test;

import java.awt.event.KeyEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PetMenuDismissTest {

    @Test
    public void toggleClosesOnlyWhenAlreadyOpen() {
        assertFalse(PetMenuDismiss.shouldCloseOnToggle(false));
        assertTrue(PetMenuDismiss.shouldCloseOnToggle(true));
    }

    @Test
    public void escapeClosesOpenMenuAndIgnoresOtherKeys() {
        assertFalse(PetMenuDismiss.shouldCloseOnEscape(false, KeyEvent.VK_ESCAPE));
        assertTrue(PetMenuDismiss.shouldCloseOnEscape(true, KeyEvent.VK_ESCAPE));
        assertFalse(PetMenuDismiss.shouldCloseOnEscape(true, KeyEvent.VK_ENTER));
        assertFalse(PetMenuDismiss.shouldCloseOnEscape(true, KeyEvent.VK_SPACE));
    }

    @Test
    public void focusLostClosesUnlessFocusMovedToMenu() {
        assertFalse(PetMenuDismiss.shouldCloseOnFocusLost(false, false));
        assertTrue(PetMenuDismiss.shouldCloseOnFocusLost(true, false));
        assertFalse(PetMenuDismiss.shouldCloseOnFocusLost(true, true));
    }

    @Test
    public void outsideClickClosesAndInsideClickDoesNot() {
        assertFalse(PetMenuDismiss.shouldCloseOnOutsideClick(false, false));
        assertTrue(PetMenuDismiss.shouldCloseOnOutsideClick(true, false));
        assertFalse(PetMenuDismiss.shouldCloseOnOutsideClick(true, true));
    }

    @Test
    public void autoCloseAfterFourSecondsOutsideAndResetsWhenPointerReturns() {
        long t0 = 10_000L;
        assertEquals(-1L, PetMenuDismiss.updateLeftAt(true, 9_000L, t0));
        long left = PetMenuDismiss.updateLeftAt(false, -1L, t0);
        assertEquals(t0, left);
        assertFalse(PetMenuDismiss.shouldAutoClose(true, false, left, t0 + 3_999L));
        assertTrue(PetMenuDismiss.shouldAutoClose(true, false, left, t0 + PetMenuDismiss.AUTO_CLOSE_MS));
        long back = PetMenuDismiss.updateLeftAt(true, left, t0 + 1_000L);
        assertEquals(-1L, back);
        assertFalse(PetMenuDismiss.shouldAutoClose(true, true, back, t0 + 20_000L));
        assertFalse(PetMenuDismiss.shouldAutoClose(false, false, left, t0 + 20_000L));
        long leftAgain = PetMenuDismiss.updateLeftAt(false, -1L, t0 + 2_000L);
        assertEquals(t0 + 2_000L, leftAgain);
        assertFalse(PetMenuDismiss.shouldAutoClose(true, false, leftAgain, t0 + 2_000L + 3_999L));
        assertTrue(PetMenuDismiss.shouldAutoClose(true, false, leftAgain, t0 + 2_000L + 4_000L));
    }
}
