package com.example.pet;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.lang.reflect.InvocationTargetException;

/**
 * 桌面寵物的啟用、停用與結束。同一隻寵物只會有一個視窗，監聽器不重複註冊。
 * 所有 Swing 操作都在 EDT 上執行。
 */
public final class DesktopPetManager {

    private final DesktopPetSettings settings;
    private final DesktopPetEventBridge bridge;
    private DesktopPetWindow window;
    private boolean active;
    private boolean closed;

    public DesktopPetManager() {
        this(new DesktopPetSettings());
    }

    public DesktopPetManager(DesktopPetSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings");
        }
        this.settings = settings;
        this.bridge = new DesktopPetEventBridge(settings, new PetSurface());
    }

    public DesktopPetSettings settings() {
        return settings;
    }

    /**
     * 聊天、傳檔、任務完成與排程時間到都從這裡進來。
     * 總開關關閉時直接丟掉，不影響原本的功能，重新開啟也不補播。
     */
    public void onAppEvent(DesktopPetEvent event, Runnable open) {
        if (closed) {
            return;
        }
        bridge.offer(event, open);
    }

    public boolean savedEnabled() {
        return settings.isEnabled();
    }

    /**
     * @return 要求的狀態是否已經生效。無螢幕時仍會記住「開啟」，但回傳 false。
     */
    public boolean setEnabled(boolean enabled) {
        if (SwingUtilities.isEventDispatchThread()) {
            return applyEnabled(enabled);
        }
        boolean[] result = {false};
        try {
            SwingUtilities.invokeAndWait(() -> result[0] = applyEnabled(enabled));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ex) {
            return false;
        }
        return result[0];
    }

    /**
     * 關掉寵物並停掉計時器。不改已儲存的開關，下次啟動仍照上次的選擇。
     */
    public void shutdown() {
        Runnable task = () -> {
            if (closed) {
                return;
            }
            closed = true;
            active = false;
            bridge.discard();
            if (window != null) {
                window.disposePet();
                window = null;
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(task);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException ex) {
            // 結束過程中的繪圖失敗不留著視窗。
        }
    }

    boolean isAnimating() {
        return window != null && window.isAnimating();
    }

    DesktopPetWindow window() {
        return window;
    }

    private boolean applyEnabled(boolean enabled) {
        if (closed) {
            return false;
        }
        if (!enabled) {
            settings.setEnabled(false);
            active = false;
            bridge.discard();
            if (window != null) {
                window.hidePet();
            }
            return true;
        }
        settings.setEnabled(true);
        if (active && window != null && window.isShowing() && window.isAnimating()) {
            return true;
        }
        if (GraphicsEnvironment.isHeadless()) {
            active = false;
            return false;
        }
        try {
            if (window == null || !window.isDisplayable()) {
                window = new DesktopPetWindow(settings);
                window.setNoticeEnded(bridge::noticeEnded);
                window.setNoticeReleased(bridge::releaseShowing);
                window.setNoticePump(bridge::pump);
            }
            window.showPet();
            active = window.isShowing();
            if (active) {
                bridge.pump();
            }
            return active;
        } catch (HeadlessException ex) {
            active = false;
            return false;
        } catch (RuntimeException ex) {
            active = false;
            System.err.println("[桌面寵物] 無法顯示：" + ex.getMessage());
            return false;
        }
    }

    private void playSound() {
        Thread worker = new Thread(() -> {
            try {
                Toolkit.getDefaultToolkit().beep();
            } catch (RuntimeException ex) {
                // 沒有音效裝置時安靜略過。
            }
        }, "milu-notice-sound");
        worker.setDaemon(true);
        worker.start();
    }

    private final class PetSurface implements DesktopPetEventBridge.Surface {
        @Override
        public boolean petEnabled() {
            return !closed && settings.isEnabled();
        }

        @Override
        public boolean readyForNotice() {
            return petEnabled()
                    && window != null
                    && window.isShowing()
                    && window.isAnimating()
                    && !window.carried()
                    && !window.noticeActive();
        }

        @Override
        public boolean showNotice(String phrase, Runnable open) {
            if (window == null) {
                return false;
            }
            return window.showNotice(phrase, open);
        }

        @Override
        public void playSound() {
            DesktopPetManager.this.playSound();
        }
    }
}
