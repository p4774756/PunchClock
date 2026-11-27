package com.example.ui;

import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

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
        syncBadge(unreadPeerCount, null, null);
    }

    /**
     * 同步未讀徽章：macOS 等走 Dock {@code setIconBadge}；否則以 {@code setFrameIcons} 套用疊圖圖示。
     */
    public static void syncBadge(
            int unreadPeerCount, List<Image> baseIcons, Consumer<List<Image>> setFrameIcons) {
        boolean usedNativeBadge = false;
        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                String badge = badgeTextForPeerUnreadCount(unreadPeerCount);
                boolean textBadge = taskbar.isSupported(Taskbar.Feature.ICON_BADGE_TEXT);
                boolean numberBadge = taskbar.isSupported(Taskbar.Feature.ICON_BADGE_NUMBER);
                if (textBadge) {
                    taskbar.setIconBadge(badge);
                    usedNativeBadge = true;
                } else if (numberBadge) {
                    taskbar.setIconBadge(badge);
                    usedNativeBadge = true;
                }
                if (!usedNativeBadge && setFrameIcons != null && baseIcons != null && !baseIcons.isEmpty()) {
                    applyComposedFrameAndTaskbarIcons(taskbar, baseIcons, unreadPeerCount, setFrameIcons);
                } else if (usedNativeBadge && unreadPeerCount <= 0 && setFrameIcons != null
                        && baseIcons != null && !baseIcons.isEmpty()) {
                    setFrameIcons.accept(baseIcons);
                }
            } else if (setFrameIcons != null && baseIcons != null && !baseIcons.isEmpty()) {
                applyComposedFrameAndTaskbarIcons(null, baseIcons, unreadPeerCount, setFrameIcons);
            }
        } catch (Exception ignored) {
            if (setFrameIcons != null && baseIcons != null && !baseIcons.isEmpty()) {
                try {
                    List<Image> icons = TaskbarPeerUnreadIcons.composeTaskbarIcons(baseIcons, unreadPeerCount);
                    setFrameIcons.accept(icons);
                } catch (Exception ignoredAgain) {
                    // 疊圖失敗時略過
                }
            }
        }
    }

    private static void applyComposedFrameAndTaskbarIcons(
            Taskbar taskbar,
            List<Image> baseIcons,
            int unreadPeerCount,
            Consumer<List<Image>> setFrameIcons) {
        List<Image> icons = TaskbarPeerUnreadIcons.composeTaskbarIcons(baseIcons, unreadPeerCount);
        setFrameIcons.accept(icons);
        if (taskbar != null && taskbar.isSupported(Taskbar.Feature.ICON_IMAGE) && !icons.isEmpty()) {
            taskbar.setIconImage(icons.get(icons.size() - 1));
        }
    }

    /** 單次請求使用者注意（Dock 圖示跳一下）；不支援時 no-op。 */
    public static void requestDockAttentionOnce() {
        requestDockAttentionOnce(null);
    }

    /** 單次請求使用者注意；若傳入主視窗且支援 {@code USER_ATTENTION_WINDOW} 一併閃爍工作列。 */
    public static void requestDockAttentionOnce(Window window) {
        try {
            if (!Taskbar.isTaskbarSupported()) {
                return;
            }
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.USER_ATTENTION)) {
                taskbar.requestUserAttention(true, false);
            }
            if (window != null && taskbar.isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
                taskbar.requestWindowUserAttention(window);
            }
        } catch (Exception ignored) {
            // 部分平台不支援
        }
    }
}