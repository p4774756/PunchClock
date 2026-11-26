package com.example.ui;

import java.awt.Taskbar;
import java.util.Collection;

/**
 * macOS Dock／工作列：好友未讀徽章與單次跳動；不支援時靜默略過。
 */
public final class TaskbarPeerUnread {

    private TaskbarPeerUnread() {}

    /** 與導覽「好友與傳檔」紅點同一未讀人數；0 表示清除徽章。 */
    public static String badgeTextForPeerUnreadCount(int unreadPeerCount) {
        int count = Math.max(0, unreadPeerCount);
        if (count <= 0) {
            return "";
        }
        if (count > 9) {
            return "9+";
        }
        return String.valueOf(count);
    }

    /** 主視窗或任一聊天視窗已取得焦點時，不應請求 Dock 跳動。 */
    public static boolean shouldRequestDockAttention(
            Object focused, Object mainWindow, Collection<?> chatWindows) {
        if (mainWindow != null && focused == mainWindow) {
            return false;
        }
        if (chatWindows != null) {
            for (Object chat : chatWindows) {
                if (chat != null && chat == focused) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void syncBadge(int unreadPeerCount) {
        try {
            if (!Taskbar.isTaskbarSupported()) {
                return;
            }
            Taskbar taskbar = Taskbar.getTaskbar();
            String badge = badgeTextForPeerUnreadCount(unreadPeerCount);
            if (taskbar.isSupported(Taskbar.Feature.ICON_BADGE_TEXT)) {
                taskbar.setIconBadge(badge);
                return;
            }
            if (taskbar.isSupported(Taskbar.Feature.ICON_BADGE_NUMBER) && !badge.isEmpty()) {
                taskbar.setIconBadge(badge);
            } else if (taskbar.isSupported(Taskbar.Feature.ICON_BADGE_NUMBER) && badge.isEmpty()) {
                taskbar.setIconBadge("");
            }
        } catch (Exception ignored) {
            // 部分平台／權限下無法設定徽章
        }
    }

    /** 單次請求使用者注意（Dock 圖示跳一下）；不支援時 no-op。 */
    public static void requestDockAttentionOnce() {
        try {
            if (!Taskbar.isTaskbarSupported()) {
                return;
            }
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.USER_ATTENTION)) {
                taskbar.requestUserAttention(true, false);
            }
        } catch (Exception ignored) {
            // 部分平台不支援
        }
    }
}