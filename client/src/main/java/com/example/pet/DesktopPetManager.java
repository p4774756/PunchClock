package com.example.pet;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

/**
 * 桌面寵物的啟用、停用與結束。同一隻寵物只會有一個視窗，監聽器不重複註冊。
 * 所有 Swing 操作都在 EDT 上執行。Cursor hook 監聽只綁 127.0.0.1。
 */
public final class DesktopPetManager {

    private final DesktopPetSettings settings;
    private final DesktopPetCare care;
    private final DesktopPetEventBridge bridge;
    private final DesktopPetCursorStatus cursorStatus = new DesktopPetCursorStatus();
    private final Path hookFile;
    private DesktopPetWindow window;
    private DesktopPetHookServer hookServer;
    private Runnable summonMain;
    private boolean active;
    private boolean closed;

    public DesktopPetManager() {
        this(new DesktopPetSettings());
    }

    public DesktopPetManager(DesktopPetSettings settings) {
        this(settings, DesktopPetHookServer.defaultHookFile());
    }

    DesktopPetManager(DesktopPetSettings settings, Path hookFile) {
        if (settings == null) {
            throw new IllegalArgumentException("settings");
        }
        this.settings = settings;
        this.care = new DesktopPetCare();
        this.care.restore(settings.loadCare());
        this.care.tick();
        this.settings.saveCare(this.care.snapshot());
        this.hookFile = hookFile != null ? hookFile : DesktopPetHookServer.defaultHookFile();
        this.bridge = new DesktopPetEventBridge(settings, new PetSurface());
    }

    DesktopPetCare care() {
        return care;
    }

    public DesktopPetSettings settings() {
        return settings;
    }

    /**
     * 聊天、傳檔、任務完成與排程時間到都從這裡進來。
     * 總開關關閉時直接丟掉，不影響原本的功能，重新開啟也不補播。
     * 打卡成功（{@link DesktopPetEvent#TASK_COMPLETED}）在養成開啟時另加心情，與提醒開關無關。
     */
    public void onAppEvent(DesktopPetEvent event, Runnable open) {
        if (closed) {
            return;
        }
        if (event == DesktopPetEvent.TASK_COMPLETED) {
            applyTaskMood();
        }
        bridge.offer(event, open);
    }

    /** 設定頁切換「養成」時呼叫。關閉會凍結數值；再開從凍結值繼續。 */
    public void setCareEnabled(boolean enabled) {
        Runnable task = () -> {
            if (closed) {
                return;
            }
            care.setEnabled(enabled, System.currentTimeMillis());
            settings.setCareEnabled(enabled);
            persistCare();
            if (window != null) {
                window.syncCareUi();
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
            // 設定失敗時維持現況。
        }
    }

    public boolean savedEnabled() {
        return settings.isEnabled();
    }

    /** 連點兩下寵物時執行。用來把主視窗叫到前景。 */
    public void setSummonMain(Runnable summonMain) {
        this.summonMain = summonMain;
        if (window != null) {
            window.setSummonMain(summonMain);
        }
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

    /** 設定頁切換「Cursor 狀態反應」時呼叫。總開關關閉時不會開監聽。 */
    public void setCursorReactEnabled(boolean enabled) {
        Runnable task = () -> {
            if (closed) {
                return;
            }
            settings.setCursorReact(enabled);
            if (!enabled) {
                cursorStatus.clear();
                if (window != null) {
                    window.clearCursorStatus();
                }
                stopHookServer();
                return;
            }
            if (active && settings.isEnabled()) {
                startHookServer();
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
            // 設定失敗時維持現況。
        }
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
            cursorStatus.clear();
            stopHookServer();
            care.tick();
            persistCare();
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

    DesktopPetHookServer hookServer() {
        return hookServer;
    }

    DesktopPetCursorStatus cursorStatus() {
        return cursorStatus;
    }

    private boolean applyEnabled(boolean enabled) {
        if (closed) {
            return false;
        }
        if (!enabled) {
            settings.setEnabled(false);
            active = false;
            bridge.discard();
            cursorStatus.clear();
            stopHookServer();
            care.tick();
            persistCare();
            if (window != null) {
                window.clearCursorStatus();
                window.hidePet();
            }
            return true;
        }
        settings.setEnabled(true);
        care.tick();
        persistCare();
        if (active && window != null && window.isShowing() && window.isAnimating()) {
            startHookServer();
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
                window.setSummonMain(summonMain);
                window.bindCare(care, this::persistCare);
            }
            window.syncCareUi();
            window.showPet();
            active = window.isShowing();
            if (active) {
                bridge.pump();
                startHookServer();
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

    private void persistCare() {
        settings.saveCare(care.snapshot());
    }

    private void applyTaskMood() {
        Runnable task = () -> {
            if (closed) {
                return;
            }
            care.onTaskCompleted();
            persistCare();
            if (window != null) {
                window.syncCareUi();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private void startHookServer() {
        if (closed || !active || !settings.isEnabled() || !settings.cursorReact()) {
            stopHookServer();
            return;
        }
        if (hookServer != null && hookServer.isRunning()) {
            return;
        }
        stopHookServer();
        hookServer = new DesktopPetHookServer(hookFile, this::onHookEvent);
        if (!hookServer.start()) {
            hookServer = null;
        }
    }

    private void stopHookServer() {
        if (hookServer != null) {
            hookServer.stop();
            hookServer = null;
        }
    }

    private void onHookEvent(DesktopPetHookServer.HookEvent event) {
        if (event == null || closed) {
            return;
        }
        Runnable task = () -> {
            if (closed || !active || !settings.isEnabled() || !settings.cursorReact()) {
                return;
            }
            DesktopPetCursorStatus.Outcome outcome = cursorStatus.apply(
                    event.eventName, event.conversationId, event.status);
            if (outcome != null && outcome.action == DesktopPetCursorStatus.Action.CELEBRATE) {
                care.onCursorCelebrate();
                persistCare();
                if (window != null) {
                    window.syncCareUi();
                }
            }
            if (window != null && window.isShowing()) {
                window.applyCursorOutcome(outcome);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
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
