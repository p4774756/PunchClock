package com.example.ui;

import javax.swing.Timer;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 同事「戳一下」時的視窗晃動（類似即時通訊軟體的視窗震動）。
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
     * 在 EDT 以 Timer 播放晃動；結束後還原座標並執行 {@code onDone}。
     * 最大化或尚未顯示的視窗會跳過動畫，直接回呼。
     */
    public static void shake(Window window, Runnable onDone) {
        if (!canShake(window)) {
            complete(onDone);
            return;
        }
        Point origin = window.getLocation();
        List<Point> path = shakePath(origin);
        Timer timer = new Timer(FRAME_DELAY_MS, null);
        final int[] index = {0};
        timer.addActionListener(e -> {
            if (!window.isDisplayable()) {
                timer.stop();
                complete(onDone);
                return;
            }
            if (index[0] >= path.size()) {
                timer.stop();
                window.setLocation(origin);
                complete(onDone);
                return;
            }
            Point next = path.get(index[0]);
            window.setLocation(next.x, next.y);
            index[0]++;
        });
        timer.start();
    }

    private static void complete(Runnable onDone) {
        if (onDone != null) {
            onDone.run();
        }
    }
}
