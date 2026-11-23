package com.example.ui;

import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/** 分頁內層滾輪轉給外層 {@link CompactShellPanels} 捲軸；內層垂直尚能捲時才自己吃。 */
public final class ShellWheelForwarder {

    private ShellWheelForwarder() {
    }

    public static void install(JScrollPane outer, JComponent body) {
        if (outer == null || body == null) {
            return;
        }
        MouseWheelListener listener = e -> handle(outer, e);
        attach(body, listener);
        outer.addMouseWheelListener(listener);
    }

    private static void attach(Component root, MouseWheelListener listener) {
        if (root instanceof JScrollPane) {
            JScrollPane pane = (JScrollPane) root;
            pane.setWheelScrollingEnabled(false);
            pane.addMouseWheelListener(listener);
            if (pane.getViewport() != null && pane.getViewport().getView() != null) {
                attach(pane.getViewport().getView(), listener);
            }
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                attach(child, listener);
            }
        }
        if (root instanceof JComponent) {
            ((JComponent) root).addMouseWheelListener(listener);
        }
    }

    static void handle(JScrollPane outer, MouseWheelEvent event) {
        if (event.isConsumed()) {
            return;
        }
        Component source = event.getComponent();
        if (source instanceof JSlider && ((JSlider) source).hasFocus()) {
            return;
        }
        JScrollPane inner = enclosingScrollPane(source);
        if (inner != null && inner != outer && innerCanScrollVertically(inner, event)) {
            scrollVertically(inner, event);
            event.consume();
            return;
        }
        if (outerCanScrollVertically(outer, event)) {
            scrollVertically(outer, event);
        }
        if (effectiveWheelRotation(event) != 0) {
            event.consume();
        }
    }

    private static JScrollPane enclosingScrollPane(Component source) {
        for (Component walk = source; walk != null; walk = walk.getParent()) {
            if (walk instanceof JScrollPane) {
                return (JScrollPane) walk;
            }
        }
        return null;
    }

    private static boolean innerCanScrollVertically(JScrollPane pane, MouseWheelEvent event) {
        JScrollBar bar = pane.getVerticalScrollBar();
        if (bar == null || bar.getMaximum() <= bar.getVisibleAmount()) {
            return false;
        }
        return canScroll(bar, effectiveWheelRotation(event));
    }

    private static boolean outerCanScrollVertically(JScrollPane outer, MouseWheelEvent event) {
        JScrollBar bar = outer.getVerticalScrollBar();
        if (bar == null) {
            return false;
        }
        return canScroll(bar, effectiveWheelRotation(event));
    }

    static int effectiveWheelRotation(MouseWheelEvent event) {
        int rot = event.getWheelRotation();
        if (rot != 0) {
            return rot;
        }
        double precise = event.getPreciseWheelRotation();
        if (precise > 0.0001d) {
            return 1;
        }
        if (precise < -0.0001d) {
            return -1;
        }
        return 0;
    }

    private static boolean canScroll(JScrollBar bar, int wheelRotation) {
        int max = Math.max(0, bar.getMaximum() - bar.getVisibleAmount());
        if (wheelRotation > 0) {
            return bar.getValue() < max;
        }
        if (wheelRotation < 0) {
            return bar.getValue() > bar.getMinimum();
        }
        return false;
    }

    private static void scrollVertically(JScrollPane pane, MouseWheelEvent event) {
        JScrollBar bar = pane.getVerticalScrollBar();
        if (bar == null) {
            return;
        }
        int delta = event.getWheelRotation() != 0
                ? event.getUnitsToScroll()
                : scrollUnitsFromPrecise(event);
        int next = bar.getValue() + delta;
        int max = Math.max(bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount());
        bar.setValue(Math.max(bar.getMinimum(), Math.min(next, max)));
        SwingUtilities.invokeLater(pane::revalidate);
    }

    private static int scrollUnitsFromPrecise(MouseWheelEvent event) {
        double precise = event.getPreciseWheelRotation();
        if (Math.abs(precise) < 0.0001d) {
            return 0;
        }
        int amount = Math.max(1, (int) Math.round(Math.abs(precise) * event.getScrollAmount()));
        return precise > 0 ? amount : -amount;
    }
}