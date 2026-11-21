package com.example.pet;

import java.awt.event.KeyEvent;

/**
 * 桌寵右鍵選單何時該收起。純判斷、不含 Swing，方便單測。
 */
final class PetMenuDismiss {

    static final long AUTO_CLOSE_MS = 4_000L;

    private PetMenuDismiss() {
    }

    static boolean shouldCloseOnToggle(boolean menuVisible) {
        return menuVisible;
    }

    static boolean shouldCloseOnEscape(boolean menuVisible, int keyCode) {
        return menuVisible && keyCode == KeyEvent.VK_ESCAPE;
    }

    /**
     * 看板本身不收鍵。Esc 只會進已取得鍵盤焦點的寵物視窗；
     * 視窗還沒有焦點時，按鍵不會進這個行程，開啟時必須主動要焦點。
     */
    static boolean needsKeyboardFocus(boolean petWindowFocused) {
        return !petWindowFocused;
    }

    static boolean shouldCloseOnFocusLost(boolean menuVisible, boolean focusMovedToMenu) {
        return menuVisible && !focusMovedToMenu;
    }

    static boolean shouldCloseOnOutsideClick(boolean menuVisible, boolean clickInsideMenu) {
        return menuVisible && !clickInsideMenu;
    }

    /**
     * @param leftAtMs 游標離開選單的時間；仍在選單上或尚未離開時為 -1
     */
    static boolean shouldAutoClose(boolean menuVisible, boolean mouseInsideMenu, long leftAtMs, long nowMs) {
        if (!menuVisible || mouseInsideMenu || leftAtMs < 0L) {
            return false;
        }
        return nowMs - leftAtMs >= AUTO_CLOSE_MS;
    }

    static long updateLeftAt(boolean mouseInsideMenu, long currentLeftAt, long nowMs) {
        if (mouseInsideMenu) {
            return -1L;
        }
        return currentLeftAt < 0L ? nowMs : currentLeftAt;
    }
}
