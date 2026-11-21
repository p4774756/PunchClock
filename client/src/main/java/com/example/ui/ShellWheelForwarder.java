package com.example.ui;

import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
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
        JScrollPane inner = enclosingScrollPane(source);
        if (inner != null && inner != outer && innerCanScrollVertically(inner, event)) {
            scrollVertically(inner, event);
            event.consume();
            return;
        }
        if (outerCanScrollVertically(outer, event)) {
            scrollVertically(outer, event);
        }
        event.consume();
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
        return canScroll(bar, event.getWheelRotation());
    }

    private static boolean outerCanScrollVertically(JScrollPane outer, MouseWheelEvent event) {
        JScrollBar bar = outer.getVerticalScrollBar();
        if (bar == null) {
            return false;
        }
        return canScroll(bar, event.getWheelRotation());
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
        int next = bar.getValue() + event.getUnitsToScroll();
        int max = Math.max(bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount());
        bar.setValue(Math.max(bar.getMinimum(), Math.min(next, max)));
        SwingUtilities.invokeLater(pane::revalidate);
    }
}