package com.example.ui;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.HierarchyEvent;
import java.util.HashSet;
import java.util.Set;

/** 好友表狀態跑馬燈：全表共用一個 {@link Timer}，僅可見且溢位列時運作。 */
final class PeerStatusMarquee {

    static final String TIMER_KEY = "peer.statusMarquee.timer";
    static final String OFFSET_KEY = "peer.statusMarquee.offset";
    private static final String SCROLL_KEY = "peer.statusMarquee.scroll";
    private static final String OVERFLOW_ROWS_KEY = "peer.statusMarquee.overflowRows";
    private static final int TICK_MS = 48;

    private PeerStatusMarquee() {
    }

    static void install(JTable table, JScrollPane scroll) {
        if (table.getClientProperty(TIMER_KEY) != null) {
            return;
        }
        table.putClientProperty(SCROLL_KEY, scroll);
        Timer timer = new Timer(TICK_MS, e -> tick(table));
        timer.setCoalesce(true);
        table.putClientProperty(TIMER_KEY, timer);
        table.putClientProperty(OFFSET_KEY, Integer.valueOf(0));
        table.putClientProperty(OVERFLOW_ROWS_KEY, new HashSet<Integer>());

        javax.swing.event.TableModelListener modelListener = e -> sync(table);
        table.getModel().addTableModelListener(modelListener);
        table.putClientProperty("peer.statusMarquee.modelListener", modelListener);

        if (scroll != null) {
            scroll.getViewport().addChangeListener(e -> sync(table));
        }
        table.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                sync(table);
            }
        });
        if (scroll != null) {
            scroll.addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    sync(table);
                }
            });
        }
        sync(table);
    }

    static int offset(JTable table) {
        Object stored = table.getClientProperty(OFFSET_KEY);
        return stored instanceof Integer ? ((Integer) stored).intValue() : 0;
    }

    static void setOffset(JTable table, int value) {
        table.putClientProperty(OFFSET_KEY, Integer.valueOf(Math.max(0, value)));
    }

    static boolean timerRunning(JTable table) {
        Timer timer = timer(table);
        return timer != null && timer.isRunning();
    }

    static void sync(JTable table) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> sync(table));
            return;
        }
        JScrollPane scroll = scroll(table);
        Set<Integer> overflow = collectOverflowRows(table, scroll);
        table.putClientProperty(OVERFLOW_ROWS_KEY, overflow);
        Timer timer = timer(table);
        if (timer == null) {
            return;
        }
        if (overflow.isEmpty() || !tableActive(table, scroll)) {
            timer.stop();
            table.putClientProperty(OFFSET_KEY, Integer.valueOf(0));
            return;
        }
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private static void tick(JTable table) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> tick(table));
            return;
        }
        @SuppressWarnings("unchecked")
        Set<Integer> rows = (Set<Integer>) table.getClientProperty(OVERFLOW_ROWS_KEY);
        JScrollPane scroll = scroll(table);
        if (rows == null || rows.isEmpty() || !tableActive(table, scroll)) {
            Timer timer = timer(table);
            if (timer != null) {
                timer.stop();
            }
            table.putClientProperty(OFFSET_KEY, Integer.valueOf(0));
            return;
        }
        int next = offset(table) + 1;
        table.putClientProperty(OFFSET_KEY, Integer.valueOf(next));
        for (int row : rows) {
            if (row < 0 || row >= table.getRowCount()) {
                continue;
            }
            Rectangle rect = table.getCellRect(row, 0, false);
            if (rect != null && rect.height > 0) {
                table.repaint(rect);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> collectOverflowRows(JTable table, JScrollPane scroll) {
        Set<Integer> overflow = new HashSet<>();
        if (!tableActive(table, scroll) || table.getRowCount() == 0) {
            return overflow;
        }
        int first = 0;
        int last = table.getRowCount() - 1;
        if (scroll != null && scroll.getViewport() != null) {
            Rectangle view = scroll.getViewport().getViewRect();
            int r0 = table.rowAtPoint(new Point(view.x + 1, view.y + 1));
            int r1 = table.rowAtPoint(new Point(view.x + 1, view.y + Math.max(1, view.height - 2)));
            if (r0 >= 0) {
                first = r0;
            }
            if (r1 >= 0) {
                last = r1;
            }
        }
        for (int row = first; row <= last; row++) {
            Component cell = table.prepareRenderer(table.getCellRenderer(row, 0), row, 0);
            if (cell instanceof PeerFriendNameCell && ((PeerFriendNameCell) cell).needsStatusMarquee()) {
                overflow.add(row);
            }
        }
        return overflow;
    }

    private static boolean tableActive(JTable table, JScrollPane scroll) {
        if (table == null || table.getRowCount() == 0) {
            return false;
        }
        if (!table.isShowing()) {
            return false;
        }
        return scroll == null || scroll.isShowing();
    }

    private static JScrollPane scroll(JTable table) {
        Object stored = table.getClientProperty(SCROLL_KEY);
        return stored instanceof JScrollPane ? (JScrollPane) stored : null;
    }

    private static Timer timer(JTable table) {
        Object stored = table.getClientProperty(TIMER_KEY);
        return stored instanceof Timer ? (Timer) stored : null;
    }
}