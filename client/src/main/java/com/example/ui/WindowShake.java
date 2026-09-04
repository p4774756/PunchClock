package com.example.ui;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.RootPaneContainer;

/**
 * 同事「戳一下」時的視窗晃動（類似即時通訊軟體的視窗震動）。
 *
 * <p>同時移動頂層視窗與內部 layered pane。部分 Linux 視窗管理員會忽略或合併
 * 連續的 {@code setLocation}，內部平移仍能讓使用者看到晃動。</p>
 */
public final class WindowShake {

    /** 水平位移序列（像素），最後一格回到原點。 */
    static final int[] OFFSETS_X = {
            22, -22, 20, -20, 18, -18, 14, -14, 10, -10, 6, -6, 3, -3, 0
    };

    static final int FRAME_DELAY_MS = 32;

    private WindowShake() {
    }

    public static List<Point> shakePath(Point origin) {
        if (origin == null) {
            return Collections.emptyList();
        }
        List<Point> path = new ArrayList<>(OFFSETS_X.length);
        for (int dx : OFFSETS_X) {
            path.add(new Point(origin.x + dx, origin.y));
        }
        return path;
    }

    public static boolean canShake(Window window) {
        if (window == null || !window.isDisplayable()) {
            return false;
        }
        if (window instanceof Frame) {
            int state = ((Frame) window).getExtendedState();
            if ((state & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
                return false;
            }
        }
        return true;
    }

    /**
     * 將視窗從最小化還原並嘗試搶到前景（部分 Linux 視窗管理員會忽略單純 toFront）。
     */
    public static void bringToFront(Window window) {
        if (window == null) {
            return;
        }
        if (window instanceof Frame) {
            Frame frame = (Frame) window;
            int state = frame.getExtendedState();
            if ((state & Frame.ICONIFIED) != 0) {
                frame.setExtendedState(state & ~Frame.ICONIFIED);
            }
        }
        window.setVisible(true);
        window.toFront();
        window.requestFocus();
        try {
            window.setAlwaysOnTop(true);
            window.setAlwaysOnTop(false);
        } catch (Exception ignored) {
            // 部分平台不允許 always-on-top
        }
    }

    /**
     * 在呼叫執行緒（應為 EDT）同步播放晃動；結束後還原座標並執行 {@code onDone}。
     * 最大化或尚未顯示的視窗會跳過動畫，直接回呼。
     */
    public static void shake(Window window, Runnable onDone) {
        if (!canShake(window)) {
            complete(onDone);
            return;
        }

        Point windowOrigin = window.getLocation();
        Component inner = innerLayer(window);
        Point innerOrigin = inner != null ? inner.getLocation() : null;

        try {
            for (int dx : OFFSETS_X) {
                window.setLocation(windowOrigin.x + dx, windowOrigin.y);
                if (inner != null && innerOrigin != null) {
                    inner.setLocation(innerOrigin.x + dx, innerOrigin.y);
                    inner.repaint();
                }
                window.repaint();
                Toolkit.getDefaultToolkit().sync();
                sleepQuietly(FRAME_DELAY_MS);
            }
        } finally {
            if (window.isDisplayable()) {
                window.setLocation(windowOrigin);
            }
            if (inner != null && innerOrigin != null && inner.isDisplayable()) {
                inner.setLocation(innerOrigin);
            }
        }
        complete(onDone);
    }

    private static Component innerLayer(Window window) {
        if (!(window instanceof RootPaneContainer)) {
            return null;
        }
        JComponent layered = ((RootPaneContainer) window).getLayeredPane();
        return layered;
    }

    private static void sleepQuietly(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static void complete(Runnable onDone) {
        if (onDone != null) {
            onDone.run();
        }
    }
}
