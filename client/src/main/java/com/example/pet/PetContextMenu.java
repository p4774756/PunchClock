package com.example.pet;

import com.example.ui.CandyTheme;
import com.example.ui.UiFonts;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 桌寵右鍵選單（自繪單一面板，避免 JPopupMenu／JCheckBoxMenuItem 的重影）。
 */
final class PetContextMenu extends JPanel {

    static final int WIDTH = 196;
    static final int PAD_H = 12;
    static final int PAD_TOP = 10;
    static final int PAD_BOTTOM = 10;
    static final int ROW_H = 32;
    static final int STATUS_BLOCK = 58;
    static final int SEP_H = 9;
    static final int CORNER = 14;
    static final int SHADOW_PAD = 6;

    static final Color BG = new Color(0xFF, 0xF6, 0xFA);
    static final Color SHADOW = new Color(0, 0, 0, 42);
    static final Color SEP = new Color(0xE8, 0xD4, 0xE0);
    static final Color HOVER = new Color(0xFF, 0xE4, 0xF0);
    static final Color MUTED = new Color(0x9A, 0x88, 0x92);
    static final Color INK = CandyTheme.PINK_INK;
    static final Color BAR_TRACK = new Color(0xF0, 0xE0, 0xE8);
    static final Color BAR_HUNGER = new Color(0xFF, 0x9A, 0x6E);
    static final Color BAR_MOOD = CandyTheme.PINK;
    static final Color BAR_ENERGY = new Color(0x8E, 0xB4, 0xE8);

    enum RowKind {
        STATUS, CONTROL, CARE, DEBUG
    }

    static final class Row {
        final RowKind kind;
        final String icon;
        final String label;
        final boolean enabled;
        final boolean checked;
        final boolean muted;
        final DesktopPetCare.Action careAction;
        final DesktopPetAnimation.Mode careMode;

        Row(RowKind kind, String icon, String label, boolean enabled, boolean checked, boolean muted,
            DesktopPetCare.Action careAction, DesktopPetAnimation.Mode careMode) {
            this.kind = kind;
            this.icon = icon;
            this.label = label;
            this.enabled = enabled;
            this.checked = checked;
            this.muted = muted;
            this.careAction = careAction;
            this.careMode = careMode;
        }
    }

    interface Listener {
        void onToggleControl(boolean enable);

        void onCare(DesktopPetCare.Action action, DesktopPetAnimation.Mode mode);

        void onToggleDebug();

        void onDismiss();
    }

    private final List<Row> rows = new ArrayList<>();
    private final int statusHunger;
    private final int statusMood;
    private final int statusEnergy;
    private final Listener listener;
    private int hoverIndex = -1;

    private PetContextMenu(List<Row> rows, int hunger, int mood, int energy, Listener listener) {
        this.rows.addAll(rows);
        this.statusHunger = hunger;
        this.statusMood = mood;
        this.statusEnergy = energy;
        this.listener = listener;
        setOpaque(false);
        int h = layoutHeight();
        setPreferredSize(new Dimension(WIDTH + SHADOW_PAD * 2, h + SHADOW_PAD * 2));
        setMinimumSize(getPreferredSize());
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                hoverIndex = -1;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                int idx = rowAt(e.getY());
                if (idx < 0) {
                    return;
                }
                Row row = rows.get(idx);
                if (!row.enabled && row.kind != RowKind.STATUS) {
                    return;
                }
                activate(row);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = rowAt(e.getY());
                if (idx != hoverIndex) {
                    hoverIndex = idx;
                    repaint();
                }
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = rowAt(e.getY());
                if (idx != hoverIndex) {
                    hoverIndex = idx;
                    repaint();
                }
            }
        });
    }

    static PetContextMenu build(
            boolean careEnabled,
            boolean controlOn,
            boolean debugOn,
            DesktopPetCare care,
            long nowMs,
            Listener listener) {
        List<Row> rows = new ArrayList<>();
        int hunger = DesktopPetCare.DEFAULT;
        int mood = DesktopPetCare.DEFAULT;
        int energy = DesktopPetCare.DEFAULT;
        if (careEnabled && care != null) {
            DesktopPetCare.Snapshot snap = care.tick();
            hunger = snap.hunger;
            mood = snap.mood;
            energy = snap.energy;
        }
        rows.add(new Row(RowKind.STATUS, "", "", false, false, false, null, null));
        rows.add(new Row(RowKind.CONTROL, "🎮", "操控模式", true, controlOn, false, null, null));
        if (careEnabled && care != null) {
            rows.add(rowForCare("🍙", "餵食", DesktopPetCare.Action.FEED,
                    DesktopPetAnimation.Mode.FEED, care, nowMs));
            rows.add(rowForCare("🤚", "摸頭", DesktopPetCare.Action.PAT,
                    DesktopPetAnimation.Mode.PAT, care, nowMs));
            rows.add(rowForCare("🎾", "玩耍", DesktopPetCare.Action.PLAY,
                    DesktopPetAnimation.Mode.PLAY, care, nowMs));
        }
        String debugLabel = debugOn ? "關閉除錯" : "顯示除錯";
        rows.add(new Row(RowKind.DEBUG, "🐞", debugLabel, true, false, true, null, null));
        return new PetContextMenu(rows, hunger, mood, energy, listener);
    }

    List<Row> rows() {
        return Collections.unmodifiableList(rows);
    }

    int statusHunger() {
        return statusHunger;
    }

    int statusMood() {
        return statusMood;
    }

    int statusEnergy() {
        return statusEnergy;
    }

    static Point clampMenuOrigin(int x, int y, int menuW, int menuH, Rectangle[] screens) {
        Rectangle clip = unionScreens(screens);
        if (clip == null) {
            return new Point(x, y);
        }
        int maxX = clip.x + Math.max(0, clip.width - menuW);
        int maxY = clip.y + Math.max(0, clip.height - menuH);
        int cx = Math.max(clip.x, Math.min(x, maxX));
        int cy = Math.max(clip.y, Math.min(y, maxY));
        return new Point(cx, cy);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int ox = SHADOW_PAD;
            int oy = SHADOW_PAD;
            int w = WIDTH;
            int h = layoutHeight();
            g.setColor(SHADOW);
            g.fillRoundRect(ox + 2, oy + 3, w, h, CORNER + 2, CORNER + 2);
            g.setColor(BG);
            g.fillRoundRect(ox, oy, w, h, CORNER, CORNER);
            g.setColor(new Color(0xFF, 0xE8, 0xF2));
            g.drawRoundRect(ox, oy, w - 1, h - 1, CORNER, CORNER);
            paintStatus(g, ox, oy);
            int y = oy + STATUS_BLOCK + SEP_H;
            Font font = UiFonts.chinesePlain(13);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                if (row.kind == RowKind.STATUS) {
                    continue;
                }
                if (i > 1 || (i == 1 && careRowsPresent())) {
                    // separator before control is skipped; between status and control none
                }
                if (needsSeparatorBefore(row, i)) {
                    y += paintSeparator(g, ox, y, w);
                }
                if (hoverIndex == i && row.enabled) {
                    g.setColor(HOVER);
                    g.fill(new RoundRectangle2D.Float(ox + 4, y, w - 8, ROW_H, 8, 8));
                }
                int textY = y + (ROW_H + metrics.getAscent() - metrics.getDescent()) / 2;
                g.setColor(row.muted ? MUTED : (row.enabled ? INK : MUTED));
                int tx = ox + PAD_H;
                if (row.icon != null && !row.icon.isEmpty()) {
                    g.drawString(row.icon, tx, textY);
                    tx += metrics.stringWidth(row.icon) + 6;
                }
                g.drawString(row.label, tx, textY);
                if (row.kind == RowKind.CONTROL && row.checked) {
                    String mark = "✓";
                    int mw = metrics.stringWidth(mark);
                    g.setColor(CandyTheme.PINK_DEEP);
                    g.drawString(mark, ox + w - PAD_H - mw, textY);
                }
                y += ROW_H;
            }
        } finally {
            g.dispose();
        }
    }

    private boolean careRowsPresent() {
        for (Row row : rows) {
            if (row.kind == RowKind.CARE) {
                return true;
            }
        }
        return false;
    }

    private boolean needsSeparatorBefore(Row row, int index) {
        if (row.kind == RowKind.CONTROL) {
            return true;
        }
        if (row.kind == RowKind.DEBUG) {
            return true;
        }
        if (row.kind == RowKind.CARE && index > 0 && rows.get(index - 1).kind == RowKind.CONTROL) {
            return true;
        }
        return false;
    }

    private int paintSeparator(Graphics2D g, int ox, int y, int w) {
        g.setColor(SEP);
        g.drawLine(ox + PAD_H, y + SEP_H / 2, ox + w - PAD_H, y + SEP_H / 2);
        return SEP_H;
    }

    private void paintStatus(Graphics2D g, int ox, int oy) {
        Font font = UiFonts.chinesePlain(11);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int y = oy + PAD_TOP + metrics.getAscent();
        int barW = WIDTH - PAD_H * 2;
        int barH = 4;
        int gap = 15;
        paintBarRow(g, ox + PAD_H, y, barW, barH, "飽足", statusHunger, BAR_HUNGER, metrics);
        y += gap;
        paintBarRow(g, ox + PAD_H, y, barW, barH, "心情", statusMood, BAR_MOOD, metrics);
        y += gap;
        paintBarRow(g, ox + PAD_H, y, barW, barH, "精神", statusEnergy, BAR_ENERGY, metrics);
    }

    private void paintBarRow(Graphics2D g, int x, int y, int barW, int barH,
                               String label, int value, Color fill, FontMetrics metrics) {
        g.setColor(MUTED);
        g.drawString(label, x, y);
        int labelW = metrics.stringWidth(label) + 6;
        int bx = x + labelW;
        int bw = Math.max(20, barW - labelW - 28);
        int by = y - metrics.getAscent() + 2;
        g.setColor(BAR_TRACK);
        g.fillRoundRect(bx, by, bw, barH, barH, barH);
        double ratio = (value - DesktopPetCare.MIN) / (double) (DesktopPetCare.MAX - DesktopPetCare.MIN);
        ratio = Math.max(0, Math.min(1, ratio));
        int fw = (int) Math.round(bw * ratio);
        if (fw > 0) {
            g.setColor(fill);
            g.fillRoundRect(bx, by, fw, barH, barH, barH);
        }
        String num = Integer.toString(value);
        int nw = metrics.stringWidth(num);
        g.setColor(INK);
        g.drawString(num, x + barW - nw, y);
    }

    private int layoutHeight() {
        int h = PAD_TOP + STATUS_BLOCK + SEP_H;
        boolean hadControl = false;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind == RowKind.STATUS) {
                continue;
            }
            if (needsSeparatorBefore(row, i)) {
                h += SEP_H;
            }
            h += ROW_H;
            if (row.kind == RowKind.CONTROL) {
                hadControl = true;
            }
        }
        if (!hadControl) {
            // care disabled path still has status + items
        }
        h += PAD_BOTTOM;
        return h;
    }

    private int rowAt(int py) {
        int localY = py - SHADOW_PAD;
        if (localY < STATUS_BLOCK + SEP_H) {
            return rows.isEmpty() ? -1 : 0;
        }
        int y = STATUS_BLOCK + SEP_H;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.kind == RowKind.STATUS) {
                continue;
            }
            if (needsSeparatorBefore(row, i)) {
                y += SEP_H;
            }
            if (localY >= y && localY < y + ROW_H) {
                return i;
            }
            y += ROW_H;
        }
        return -1;
    }

    private void activate(Row row) {
        if (listener == null) {
            return;
        }
        switch (row.kind) {
            case CONTROL:
                listener.onToggleControl(!row.checked);
                listener.onDismiss();
                break;
            case CARE:
                if (row.careAction != null) {
                    listener.onCare(row.careAction, row.careMode);
                    listener.onDismiss();
                }
                break;
            case DEBUG:
                listener.onToggleDebug();
                listener.onDismiss();
                break;
            default:
                break;
        }
    }

    private static Row rowForCare(String icon, String label, DesktopPetCare.Action action,
                                  DesktopPetAnimation.Mode mode, DesktopPetCare care, long nowMs) {
        long remaining = care.cooldownRemaining(action, nowMs);
        String text = remaining > 0L ? label + "（還要等一下）" : label;
        return new Row(RowKind.CARE, icon, text, remaining <= 0L, false, false, action, mode);
    }

    private static Rectangle unionScreens(Rectangle[] screens) {
        if (screens == null || screens.length == 0) {
            return null;
        }
        int x1 = Integer.MAX_VALUE;
        int y1 = Integer.MAX_VALUE;
        int x2 = Integer.MIN_VALUE;
        int y2 = Integer.MIN_VALUE;
        for (Rectangle r : screens) {
            if (r == null) {
                continue;
            }
            x1 = Math.min(x1, r.x);
            y1 = Math.min(y1, r.y);
            x2 = Math.max(x2, r.x + r.width);
            y2 = Math.max(y2, r.y + r.height);
        }
        if (x1 == Integer.MAX_VALUE) {
            return null;
        }
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    static void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}