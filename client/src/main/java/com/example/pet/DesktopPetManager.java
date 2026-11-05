package com.example.pet;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.lang.reflect.InvocationTargetException;

/**
 * 桌面寵物的啟用、停用與結束。同一隻寵物只會有一個視窗，監聽器不重複註冊。
 * 所有 Swing 操作都在 EDT 上執行。
 */
public final class DesktopPetManager {

    private final DesktopPetSettings settings;
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
            }
            window.showPet();
            active = window.isShowing();
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
}
